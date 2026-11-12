// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

using NAudio.Wave;

namespace Mfd24.AfskListener;

/// <summary>
/// The whole tool: pick a microphone, press Listen, hold the phone — or this machine — to the
/// watch while EXPORT LOG plays, and read the packet as it lands. The CRC line turns the
/// status green or red; Stop and Listen again clears everything.
/// </summary>
public sealed class MainForm : Form
{
    private const int SampleRate = 48_000;

    private readonly ComboBox _devices = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 320 };
    private readonly Button _listen = new() { Text = "Listen", Width = 110, Height = 32 };
    private readonly Label _status = new() { AutoSize = true, Text = "Pick a microphone and press Listen." };
    private readonly ProgressBar _level = new() { Width = 120, Height = 12, Maximum = 100, Style = ProgressBarStyle.Continuous };
    private readonly TextBox _text = new()
    {
        Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill,
        Font = new Font("Consolas", 11f), WordWrap = false,
    };

    private WaveInEvent? _wave;
    private AfskDecoder? _decoder;
    private bool _verdictShown;

    // Everything heard while listening also goes to a WAV in %TEMP%, so a session that did
    // not decode can be looked at afterwards — through decode_afsk.py, or dropped back here.
    private WaveFileWriter? _tape;
    private string? _tapePath;

    public MainForm(string? file = null)
    {
        Text = "MFD-24 AFSK listener — 1200 Bd Bell 202";
        AllowDrop = true;
        DragEnter += (_, e) => { if (e.Data?.GetDataPresent(DataFormats.FileDrop) == true) e.Effect = DragDropEffects.Copy; };
        DragDrop += (_, e) =>
        {
            if (e.Data?.GetData(DataFormats.FileDrop) is string[] files && files.Length > 0) ShowFile(files[0]);
        };
        if (file != null) Load += (_, _) => ShowFile(file);
        Width = 760;
        Height = 520;
        MinimumSize = new Size(520, 320);

        var top = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 44, Padding = new Padding(8, 6, 8, 0), WrapContents = false };
        top.Controls.Add(_devices);
        top.Controls.Add(_listen);
        top.Controls.Add(_level);
        _level.Margin = new Padding(12, 10, 0, 0);

        var bottom = new Panel { Dock = DockStyle.Bottom, Height = 28, Padding = new Padding(8, 6, 8, 0) };
        bottom.Controls.Add(_status);

        Controls.Add(_text);
        Controls.Add(top);
        Controls.Add(bottom);

        for (int i = 0; i < WaveInEvent.DeviceCount; i++)
            _devices.Items.Add(WaveInEvent.GetCapabilities(i).ProductName);
        if (_devices.Items.Count > 0) _devices.SelectedIndex = 0;
        else { _status.Text = "No microphone found."; _listen.Enabled = false; }

        _listen.Click += (_, _) => { if (_wave == null) Start(); else Stop(); };
        FormClosing += (_, _) => Stop();
    }

    private void Start()
    {
        _decoder = new AfskDecoder(SampleRate);
        _text.Clear();
        _verdictShown = false;
        _wave = new WaveInEvent
        {
            DeviceNumber = _devices.SelectedIndex,
            WaveFormat = new WaveFormat(SampleRate, 16, 1),
            BufferMilliseconds = 100,
        };
        _wave.DataAvailable += OnData;
        _wave.RecordingStopped += (_, e) =>
        {
            if (e.Exception != null) BeginInvoke(() => _status.Text = "Microphone stopped: " + e.Exception.Message);
        };
        try
        {
            _tapePath = Path.Combine(Path.GetTempPath(), $"afsk-{DateTime.Now:yyyyMMdd-HHmmss}.wav");
            _tape = new WaveFileWriter(_tapePath, _wave.WaveFormat);
            _wave.StartRecording();
        }
        catch (Exception ex)
        {
            _status.Text = "Cannot open the microphone: " + ex.Message;
            _tape?.Dispose();
            _tape = null;
            _wave.Dispose();
            _wave = null;
            return;
        }
        _listen.Text = "Stop";
        _devices.Enabled = false;
        _status.ForeColor = SystemColors.ControlText;
        _status.Text = "Listening — hold the watch to the microphone and open EXPORT LOG. Recording to " + _tapePath;
    }

    private void Stop()
    {
        if (_wave == null) return;
        _wave.DataAvailable -= OnData;
        _wave.StopRecording();
        _wave.Dispose();
        _wave = null;
        lock (this)
        {
            _tape?.Dispose();
            _tape = null;
        }
        _listen.Text = "Listen";
        _devices.Enabled = true;
        _level.Value = 0;
        if (!_verdictShown) _status.Text = $"Stopped. {Summary()} Recording kept at {_tapePath}";
    }

    private void OnData(object? sender, WaveInEventArgs e)
    {
        var decoder = _decoder;
        if (decoder == null) return;
        lock (this) _tape?.Write(e.Buffer, 0, e.BytesRecorded);
        int n = e.BytesRecorded / 2;
        var samples = new float[n];
        float peak = 0;
        for (int i = 0; i < n; i++)
        {
            samples[i] = BitConverter.ToInt16(e.Buffer, i * 2) / 32768f;
            peak = Math.Max(peak, Math.Abs(samples[i]));
        }
        string got = decoder.Feed(samples);
        BeginInvoke(() =>
        {
            _level.Value = Math.Min(100, (int)(peak * 100));
            if (got.Length == 0) return;
            _text.AppendText(got.Replace("\r\n", Environment.NewLine));
            if (_verdictShown) return;
            var verdict = AfskDecoder.Check(decoder.Text);
            if (verdict == null) { _status.Text = Summary(); return; }
            _verdictShown = true;
            var (ok, said, computed) = verdict.Value;
            _status.ForeColor = ok ? Color.DarkGreen : Color.Firebrick;
            _status.Text = ok
                ? $"CRC32 {computed} matches — the packet is intact. {Summary()}"
                : $"CRC32 mismatch: the END line says {said}, the lines above give {computed}. {Summary()}";
        });
    }

    private string Summary() =>
        _decoder == null ? "" : $"{_decoder.Frames} frames, {_decoder.BadFrames} rejected.";

    /// <summary>A recording instead of the microphone: a file dropped on the window, or named on the command line.</summary>
    private void ShowFile(string path)
    {
        Stop();
        try
        {
            var (text, decoder) = FileDecoder.Decode(path);
            _decoder = decoder;
            _text.Text = text.Replace("\r\n", Environment.NewLine);
            var verdict = AfskDecoder.Check(text);
            _verdictShown = verdict != null;
            if (verdict == null)
            {
                _status.ForeColor = SystemColors.ControlText;
                _status.Text = $"{Path.GetFileName(path)}: no END line found. {Summary()}";
                return;
            }
            var (ok, said, computed) = verdict.Value;
            _status.ForeColor = ok ? Color.DarkGreen : Color.Firebrick;
            _status.Text = ok
                ? $"{Path.GetFileName(path)}: CRC32 {computed} matches — the packet is intact. {Summary()}"
                : $"{Path.GetFileName(path)}: CRC32 mismatch, the END line says {said}, the lines above give {computed}. {Summary()}";
        }
        catch (Exception ex)
        {
            _status.ForeColor = Color.Firebrick;
            _status.Text = "Cannot read " + Path.GetFileName(path) + ": " + ex.Message;
        }
    }
}
