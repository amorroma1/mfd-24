#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 R. Kravcov
"""Decode a recording of the watch's EXPORT LOG tones and check the packet's CRC32.

The face plays the incident log as Bell 202 AFSK — 1200 Bd, mark 1200 Hz, space 2200 Hz,
8N1, LSB first — which is what `minimodem --rx 1200` reads on Linux. This is the same receiver
in one Python file for machines without minimodem: it takes any audio file ffmpeg can open
(WAV, m4a, mp3, ogg, a phone's voice memo), demodulates it, prints the packet and verifies the
`END … CRC32 xxxxxxxx` line against the lines above it, exactly as the watch computed it.

    python -I tools/afsk/decode_afsk.py recording.m4a
    python -I tools/afsk/decode_afsk.py --selftest        # modulate a packet, add noise, decode it

Exit status 0 when the CRC matches, 1 otherwise. Needs numpy; needs imageio-ffmpeg only for
files that are not 16-bit PCM WAV.
"""
import argparse
import io
import math
import re
import subprocess
import sys
import wave
import zlib

import numpy as np

BAUD = 1200
MARK_HZ = 1200.0
SPACE_HZ = 2200.0
SQUELCH = 0.0001


def ended(out, value):
    """True when VALUE completes the END line's CRLF: the packet is over, and whatever the trailer
    and the silence after it demodulate to is not part of it."""
    if value != 0x0A or not out.endswith(b"\r"):
        return False
    last = out.rfind(b"\n", 0, len(out) - 1)
    return out[last + 1:].startswith(b"END ")


# --- input ---------------------------------------------------------------------------------

def read_audio(path):
    """Mono float samples in [-1, 1] and the sample rate, via ffmpeg unless it is plain WAV."""
    try:
        with wave.open(path, "rb") as w:
            if w.getsampwidth() == 2:
                rate = w.getframerate()
                raw = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64)
                if w.getnchannels() > 1:
                    raw = raw.reshape(-1, w.getnchannels()).mean(axis=1)
                return raw / 32768.0, rate
    except (wave.Error, EOFError):
        pass
    import imageio_ffmpeg
    rate = 48_000
    cmd = [imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-i", path, "-ac", "1", "-ar", str(rate),
           "-f", "s16le", "-"]
    pcm = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768.0, rate


# --- demodulation --------------------------------------------------------------------------

def tone_difference(samples, rate, window_bits):
    """Per-sample mark-minus-space energy over a window of WINDOW_BITS bits (positive is mark),
    and the two energies' sum, which is how much tone there is at all."""
    spb = rate / BAUD
    n = max(2, int(round(spb * window_bits)))
    t = np.arange(len(samples)) / rate
    diff = np.zeros(len(samples))
    total = np.zeros(len(samples))
    kernel = np.ones(n) / n
    for hz, sign in ((MARK_HZ, 1.0), (SPACE_HZ, -1.0)):
        i = samples * np.cos(2 * math.pi * hz * t)
        q = samples * np.sin(2 * math.pi * hz * t)
        energy = np.convolve(i, kernel, mode="same") ** 2 + np.convolve(q, kernel, mode="same") ** 2
        diff += sign * energy
        total += energy
    return diff, total


def demodulate(samples, rate):
    """8N1 frames to bytes: wait for mark, take a mark-to-space edge as a start bit, sample the
    bit centres, require the stop bit, resynchronise on every frame. Returns (bytes, frames seen,
    frames with a bad stop bit)."""
    spb = rate / BAUD
    # A one-bit window is the matched filter for a bit: read at the bit's centre it integrates
    # exactly that bit, and the timing search below is what puts the centre on the centre.
    d, total = tone_difference(samples, rate, 1.0)
    mark = d > 0
    quarter = max(1, int(spb / 4))
    # Squelch: silence demodulates to random bits, so a frame needs tone — a ten-thousandth of
    # the loudest the recording has, 40 dB down, which a phone's floor is still far below and a
    # microphone's noise suppression biting into the tail of the packet is not. The peak is the whole file's, as this reads whole files.
    squelch = SQUELCH * total.max()

    def level(centre):
        return d[int(round(centre))]

    def read_frame(edge):
        """The ten bit levels for the start edge at EDGE, timed at the offset within a quarter
        bit that gives the cleanest frame — the edge detector is only as exact as the noise."""
        best = None
        for off in range(-quarter, quarter + 1):
            centre = edge + off + spb / 2
            levels = [level(centre + k * spb) for k in range(10)]
            if levels[0] >= 0 or levels[9] <= 0:
                continue
            # A start bit has to be a real space, not a flicker in the preamble's mark.
            if -levels[0] < 0.3 * sum(abs(v) for v in levels) / 10:
                continue
            if total[int(round(centre)):int(round(centre + 9 * spb))].mean() < squelch:
                return None
            confidence = sum(abs(v) for v in levels)
            if best is None or confidence > best[0]:
                best = (confidence, levels)
        return best[1] if best else None

    out = bytearray()
    frames = bad = 0
    i = int(spb) + quarter
    end = len(d) - int(11 * spb)
    while i < end:
        if mark[i - 1] and not mark[i]:
            levels = read_frame(i)
            frames += 1
            if levels is None:
                bad += 1
                i += 1
                continue
            value = 0
            for k in range(8):
                if levels[k + 1] > 0:
                    value |= 1 << k
            stop = True
            if ended(out, value):
                out.append(value)
                break
            if stop:
                out.append(value)
                i = int(i + 9.5 * spb)        # into the stop bit; the next edge is the next start
            else:
                bad += 1
                i += 1
        else:
            i += 1
    return bytes(out), frames, bad


# --- the packet ----------------------------------------------------------------------------

def check_packet(text):
    """Find the END line, recompute the CRC32 of everything above it, compare."""
    lines = text.split("\r\n")
    for idx, line in enumerate(lines):
        if line.startswith("END ") and " CRC32 " in line:
            above = "\r\n".join(lines[:idx]) + "\r\n"
            m = re.search(r"CRC32 ([0-9A-F]{8})", line)
            want = m.group(1) if m else line.rsplit(" ", 1)[1].strip()
            have = "%08X" % (zlib.crc32(above.encode("ascii", "replace")) & 0xFFFFFFFF)
            return want, have, idx
    return None, None, None


# --- self-test: the encoder, as Afsk.kt writes it ------------------------------------------

def modulate(text, rate=48_000, preamble_bits=300, trailer_bits=60):
    spb = rate // BAUD
    bits = [True] * preamble_bits
    for b in text.encode("ascii"):
        bits.append(False)
        bits.extend(bool((b >> k) & 1) for k in range(8))
        bits.append(True)
    bits.extend([True] * trailer_bits)
    phase = 0.0
    out = np.zeros(len(bits) * spb)
    at = 0
    for m in bits:
        step = 2 * math.pi * (MARK_HZ if m else SPACE_HZ) / rate
        for _ in range(spb):
            out[at] = 0.8 * math.sin(phase)
            phase += step
            at += 1
    return out


def selftest():
    body = ("MFD-24 LOG V1\r\nCALLSIGN RAVEN-07 ID 1a2b3c4d\r\n"
            "WATCH 12 NOV 08:00 TO 16:00Z DUR 8h\r\n12 NOV 10:14Z HR 041 BASE 058 AT 10:12Z\r\n")
    crc = "%08X" % (zlib.crc32(body.encode("ascii")) & 0xFFFFFFFF)
    packet = body + "END 1 CRC32 " + crc + "\r\n"
    rng = np.random.default_rng(1)
    # 0.2 of white noise against a 0.4 signal is about 6 dB broadband, far worse than a phone held
    # to the watch; the decoder is clean there and starts dropping characters around 0.3.
    for rate, noise in ((48_000, 0.0), (48_000, 0.2), (44_100, 0.2)):
        pcm = modulate(packet, 48_000)
        if rate != 48_000:
            t_old = np.arange(len(pcm)) / 48_000
            t_new = np.arange(int(len(pcm) * rate / 48_000)) / rate
            pcm = np.interp(t_new, t_old, pcm)
        pcm = 0.5 * pcm + noise * rng.standard_normal(len(pcm))
        data, frames, bad = demodulate(pcm, rate)
        text = data.decode("ascii", "replace")
        want, have, _ = check_packet(text)
        ok = text == packet and want == have
        print("%d Hz noise %.1f: %d frames, %d bad stop bits, %s" % (rate, noise, frames, bad, "OK" if ok else "FAIL"))
        if not ok:
            print(repr(text))
            return 1
    return 0


def main():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("file", nargs="?", help="recording of the watch's EXPORT LOG screen")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    if not args.file:
        ap.error("a recording, or --selftest")
    samples, rate = read_audio(args.file)
    samples = samples - samples.mean()
    peak = np.abs(samples).max() or 1.0
    samples = samples / peak
    data, frames, bad = demodulate(samples, rate)
    text = data.decode("ascii", "replace")
    print("--- %d frames, %d with a bad stop bit, %.1f s at %d Hz ---" % (frames, bad, len(samples) / rate, rate))
    print(text.replace("\r\n", "\n"), end="")
    want, have, _ = check_packet(text)
    if want is None:
        print("--- no END line found: the recording is cut short or did not decode ---")
        return 1
    if want == have:
        print("--- CRC32 %s matches: the packet is intact ---" % have)
        return 0
    print("--- CRC32 mismatch: the line says %s, the lines above it give %s ---" % (want, have))
    return 1


if __name__ == "__main__":
    sys.exit(main())
