// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

namespace Mfd24.AfskListener;

internal static class Program
{
    /// <summary>
    /// No arguments: the window. A recording as the argument: the window, with that file decoded
    /// into it. <c>--check recording.wav</c>: no window — the packet and the verdict are written
    /// to <c>recording.wav.txt</c> and the exit code says whether the CRC matched, which is how
    /// the decoder is tested without a microphone.
    /// </summary>
    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length == 2 && args[0] == "--check")
        {
            var (text, decoder) = FileDecoder.Decode(args[1]);
            var verdict = AfskDecoder.Check(text);
            string report = text.Replace("\r\n", Environment.NewLine) + Environment.NewLine +
                (verdict == null
                    ? "--- no END line ---"
                    : verdict.Value.ok
                        ? $"--- CRC32 {verdict.Value.computed} matches ---"
                        : $"--- CRC32 mismatch: said {verdict.Value.said}, computed {verdict.Value.computed} ---") +
                $" {decoder.Frames} frames, {decoder.BadFrames} rejected" + Environment.NewLine;
            File.WriteAllText(args[1] + ".txt", report);
            return verdict is { ok: true } ? 0 : 1;
        }
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm(args.Length == 1 ? args[0] : null));
        return 0;
    }
}

/// <summary>A recording through the same decoder, for a file dropped on the window or named on the command line.</summary>
internal static class FileDecoder
{
    public static (string text, AfskDecoder decoder) Decode(string path)
    {
        using var reader = new NAudio.Wave.AudioFileReader(path);   // WAV, MP3, M4A, … via Media Foundation
        var decoder = new AfskDecoder(reader.WaveFormat.SampleRate);
        int channels = reader.WaveFormat.Channels;
        var buffer = new float[reader.WaveFormat.SampleRate * channels];
        var mono = new float[reader.WaveFormat.SampleRate];
        int read;
        while ((read = reader.Read(buffer, 0, buffer.Length)) > 0)
        {
            int frames = read / channels;
            for (int i = 0; i < frames; i++)
            {
                float sum = 0;
                for (int c = 0; c < channels; c++) sum += buffer[i * channels + c];
                mono[i] = sum / channels;
            }
            decoder.Feed(mono.AsSpan(0, frames));
        }
        return (decoder.Text, decoder);
    }
}
