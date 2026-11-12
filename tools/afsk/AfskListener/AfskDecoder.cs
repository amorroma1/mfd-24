// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

using System.Text;

namespace Mfd24.AfskListener;

/// <summary>
/// A streaming Bell 202 receiver for the packet the watch plays: 1200 Bd, mark 1200 Hz,
/// space 2200 Hz, 8N1, LSB first — the same arithmetic as <c>tools/afsk/decode_afsk.py</c>,
/// fed a few hundred samples at a time instead of a whole file.
///
/// Per sample it keeps the difference of mark and space energy over a one-bit window (the
/// matched filter for a bit); a mark-to-space edge is a candidate start bit; the frame is
/// read at whichever timing within a quarter bit of the edge gives the cleanest ten levels,
/// and only kept when the start bit is space and the stop bit is mark. Every frame
/// resynchronises, so a phone's sample clock drifting against the watch's costs nothing.
/// </summary>
public sealed class AfskDecoder
{
    public const int Baud = 1200;
    public const double MarkHz = 1200.0;
    public const double SpaceHz = 2200.0;

    private readonly int _rate;
    private readonly double _spb;
    private readonly int _window;
    private readonly int _quarter;

    // Raw samples not yet turned into energy, and energy not yet scanned for frames.
    private readonly List<float> _pending = new();
    private readonly List<double> _d = new();
    private readonly List<double> _total = new();   // mark + space energy: how much tone there is
    private double _peak;                            // the loudest tone heard, for the squelch
    private bool _ended;                             // the END line's CRLF has arrived

    /// <summary>A frame needs this fraction of the loudest tone heard: silence demodulates to
    /// random bits otherwise. A ten-thousandth is 40 dB down: under a phone's floor, above a
    /// microphone's noise suppression biting into the packet's tail.</summary>
    public const double Squelch = 0.0001;
    private long _t;                       // absolute sample index of _pending[0], for the phase

    private readonly StringBuilder _text = new();

    public AfskDecoder(int sampleRate)
    {
        _rate = sampleRate;
        _spb = (double)sampleRate / Baud;
        _window = Math.Max(2, (int)Math.Round(_spb));
        _quarter = Math.Max(1, (int)(_spb / 4));
    }

    /// <summary>Everything decoded so far, CRLF and all.</summary>
    public string Text => _text.ToString();

    public int Frames { get; private set; }
    public int BadFrames { get; private set; }

    public void Reset()
    {
        _pending.Clear();
        _d.Clear();
        _total.Clear();
        _peak = 0;
        _ended = false;
        _t = 0;
        _text.Clear();
        Frames = 0;
        BadFrames = 0;
    }

    /// <summary>Feed PCM in [-1, 1]; returns the bytes decoded from this call, if any.</summary>
    /// <summary>True once the END line has arrived: nothing after it is part of the packet.</summary>
    public bool Ended => _ended;

    public string Feed(ReadOnlySpan<float> samples)
    {
        if (_ended) return "";
        foreach (var s in samples) _pending.Add(s);
        Demodulate();
        return Scan();
    }

    /// <summary>The energy difference for every pending sample that has a full window after it.</summary>
    private void Demodulate()
    {
        // d[n] = |corr with mark over n..n+W|^2 - |corr with space|^2, computed by running sums.
        int n = _pending.Count;
        int usable = n - _window;
        if (usable <= 0) return;
        double wm = 2 * Math.PI * MarkHz / _rate, ws = 2 * Math.PI * SpaceHz / _rate;
        var mi = new double[n]; var mq = new double[n]; var si = new double[n]; var sq = new double[n];
        for (int k = 0; k < n; k++)
        {
            double t = _t + k;
            float x = _pending[k];
            mi[k] = x * Math.Cos(wm * t); mq[k] = x * Math.Sin(wm * t);
            si[k] = x * Math.Cos(ws * t); sq[k] = x * Math.Sin(ws * t);
        }
        double ami = 0, amq = 0, asi = 0, asq = 0;
        for (int k = 0; k < _window; k++) { ami += mi[k]; amq += mq[k]; asi += si[k]; asq += sq[k]; }
        for (int k = 0; k < usable; k++)
        {
            double m = ami * ami + amq * amq, sp = asi * asi + asq * asq;
            _d.Add(m - sp);
            _total.Add(m + sp);
            if (m + sp > _peak) _peak = m + sp;
            ami += mi[k + _window] - mi[k]; amq += mq[k + _window] - mq[k];
            asi += si[k + _window] - si[k]; asq += sq[k + _window] - sq[k];
        }
        _pending.RemoveRange(0, usable);
        _t += usable;
    }

    /// <summary>Frames out of the energy stream; keeps what a later frame may still need.</summary>
    private string Scan()
    {
        var got = new StringBuilder();
        // d[k] is the window starting at sample k; the bit whose centre is c starts at c - spb/2.
        int need = (int)(11 * _spb) + 2 * _quarter;
        int i = _quarter + 1;
        int consumed = 0;
        while (_d.Count - i > need)
        {
            if (_d[i - 1] > 0 && _d[i] <= 0)
            {
                var levels = ReadFrame(i);
                Frames++;
                if (levels == null)
                {
                    BadFrames++;
                    i++;
                    continue;
                }
                int value = 0;
                for (int k = 0; k < 8; k++) if (levels[k + 1] > 0) value |= 1 << k;
                got.Append((char)value);
                i += (int)(9.5 * _spb);
                if (value == 0x0A && EndsPacket(got))
                {
                    _ended = true;
                    break;
                }
            }
            else i++;
            consumed = i - _quarter - 1;
        }
        if (consumed > 0) { _d.RemoveRange(0, consumed); _total.RemoveRange(0, consumed); }
        if (got.Length > 0) _text.Append(got);
        return got.ToString();
    }

    /// <summary>Whether the text so far, plus GOT, ends in an END line and its CRLF.</summary>
    private bool EndsPacket(StringBuilder got)
    {
        string all = _text.ToString() + got;
        if (!all.EndsWith("\r\n")) return false;
        int last = all.LastIndexOf('\n', all.Length - 3);
        return all.Substring(last + 1).StartsWith("END ");
    }

    private double[]? ReadFrame(int edge)
    {
        double[]? best = null;
        double bestConfidence = 0;
        // d[k] is the window *starting* at k, so its sign flips half a window before the edge
        // itself: the window that integrates exactly the start bit begins half a bit after the
        // crossing, and bit k's window half a bit plus k bits after it.
        for (int off = -_quarter; off <= _quarter; off++)
        {
            double start = edge + off + _spb / 2;
            var levels = new double[10];
            double confidence = 0;
            for (int k = 0; k < 10; k++)
            {
                int at = (int)Math.Round(start + k * _spb);
                levels[k] = _d[at];
                confidence += Math.Abs(levels[k]);
            }
            if (levels[0] >= 0 || levels[9] <= 0) continue;
            if (-levels[0] < 0.3 * confidence / 10) continue;   // a real space, not a flicker in the preamble
            double tone = 0;
            for (int k = 0; k < 10; k++) tone += _total[(int)Math.Round(start + k * _spb)];
            if (tone / 10 < Squelch * _peak) return null;
            if (best == null || confidence > bestConfidence) { best = levels; bestConfidence = confidence; }
        }
        return best;
    }

    /// <summary>
    /// The verdict on the text so far: null while no END line has arrived, otherwise whether
    /// the CRC32 the watch wrote matches the lines above it.
    /// </summary>
    public static (bool ok, string said, string computed)? Check(string text)
    {
        var lines = text.Split("\r\n");
        for (int i = 0; i < lines.Length; i++)
        {
            if (!lines[i].StartsWith("END ") || !lines[i].Contains(" CRC32 ")) continue;
            string above = string.Join("\r\n", lines.Take(i)) + "\r\n";
            var m = System.Text.RegularExpressions.Regex.Match(lines[i], "CRC32 ([0-9A-F]{8})");
            string said = m.Success ? m.Groups[1].Value : lines[i][(lines[i].LastIndexOf(' ') + 1)..].Trim();
            string computed = Crc32(Encoding.ASCII.GetBytes(above)).ToString("X8");
            return (said == computed, said, computed);
        }
        return null;
    }

    /// <summary>CRC-32 as java.util.zip.CRC32 computes it, which is what the watch wrote.</summary>
    public static uint Crc32(byte[] data)
    {
        uint crc = 0xFFFFFFFF;
        foreach (var b in data)
        {
            crc ^= b;
            for (int i = 0; i < 8; i++) crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320 : crc >> 1;
        }
        return ~crc;
    }
}
