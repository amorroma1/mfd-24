// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.export

import com.avdesign.mfd24.data.IncidentRecord
import java.time.Instant
import java.util.zip.CRC32

/**
 * The incident log as the text that goes over the air.
 *
 * Plain lines, not a binary frame: whoever decodes this is reading a terminal, and a record that
 * needs a second tool to interpret is a record nobody will read. The second line says *whose*
 * instants these are — [Callsign] plus its short id — because an unattributed log is an anonymous
 * note the moment two watches share a debrief. Identity lives here, at the exit, and not in the
 * store: the log itself stays pure instants. The `WATCH` line names the shift those instants
 * belong to — the log covers one watch and is emptied by the next, so without it the times would
 * be readings with no scale beside them. Then one incident per line in ISO-8601 Zulu — the
 * same clock the dial prints and the editor lists, so the transmission cannot disagree with
 * either. The `END` line carries the count and a CRC32 of everything above it, because an
 * acoustic channel drops characters and a debrief needs to know whether it got them all; both are
 * checkable by eye and by one line of shell.
 *
 * **The two channels do not carry the same number of lines, and the packet says so.** The sound
 * has no ceiling worth naming — the whole log is eighteen seconds of tones — but a QR code on a
 * 1.2-inch screen does: [QrCode] stops at version 20, 858 bytes, and a line with a pulse and its
 * reference is 51 of them. [fitting] therefore keeps the *newest* entries that fit, and its `END`
 * line reads `END 14 OF 32` rather than `END 14`, so a photograph can never pass for the whole
 * record. The first version of this screen built one packet for both and handed it to the
 * encoder unchecked; the twelfth incident with pulse logging on threw inside `onCreate` and took
 * the watch face's process with it.
 *
 * CRLF line ends, because the receiving side is a serial-terminal habit and has been since before
 * the modulation this rides on.
 */
object LogPacket {

    /**
     * The whole log, or with [keep] the newest [keep] entries of it.
     *
     * A truncated packet announces itself: `END <kept> OF <total>`. The CRC covers the lines that
     * were sent, not the ones that were not, so the checksum still verifies what the reader has.
     */
    fun build(
        log: Array<IncidentRecord>,
        callsign: String,
        shortId: String,
        shiftStartMillis: Long = 0L,
        shiftEndMillis: Long = 0L,
        keep: Int = log.size,
    ): String {
        val kept = keep.coerceIn(0, log.size)
        val body = StringBuilder(HEADER)
        body.append("CALLSIGN ").append(callsign).append(" ID ").append(shortId).append(CRLF)
        // Which watch these instants belong to. Without it every line is a bare moment: "20:22Z"
        // cannot say whether that was ten minutes into a night watch or the last hour of a
        // sixteen-hour one, and that is most of what a debrief wants to know.
        if (shiftStartMillis > 0L && shiftEndMillis > shiftStartMillis) {
            body.append("WATCH ").append(zulu(shiftStartMillis))
                .append(" TO ").append(zulu(shiftEndMillis))
                .append(" DUR ").append(duration(shiftEndMillis - shiftStartMillis))
                .append(CRLF)
        }
        for (i in log.size - kept until log.size) {
            val record = log[i]
            body.append(zulu(record.atMillis))
            // Columns, not prose, and only when there is a reading: a line that says HR says it in
            // the same place every time, so a debrief can cut the field out with one shell command.
            if (record.hasBpm) body.append(" HR ").append(pad(record.bpm))
            if (record.hasBaseline) {
                body.append(" BASE ").append(pad(record.baselineBpm))
                    .append(" AT ").append(baselineAt(record))
            }
            body.append(CRLF)
        }
        val crc = CRC32().apply { update(body.toString().toByteArray(Charsets.US_ASCII)) }
        body.append("END ").append(kept)
        if (kept < log.size) body.append(" OF ").append(log.size)
        return body.append(" CRC32 ").append("%08X".format(crc.value)).append(CRLF).toString()
    }

    /**
     * The newest entries that fit in [capacityBytes], for the QR code.
     *
     * The packet is ASCII throughout, so its length is its size in bytes. Steps down one entry at
     * a time from the whole log; the header alone is well under any code the encoder draws, so the
     * search always ends on something that encodes.
     */
    fun fitting(
        log: Array<IncidentRecord>,
        callsign: String,
        shortId: String,
        shiftStartMillis: Long,
        shiftEndMillis: Long,
        capacityBytes: Int,
    ): String {
        var keep = log.size
        while (true) {
            val packet = build(log, callsign, shortId, shiftStartMillis, shiftEndMillis, keep)
            if (packet.length <= capacityBytes || keep == 0) return packet
            keep--
        }
    }

    /** Whole seconds: the log's precision is the dial's, and millis would be false exactness. */
    private fun zulu(millis: Long): String =
        Instant.ofEpochMilli(millis / 1000L * 1000L).toString()

    /**
     * The reference pulse's instant, as a time of day when it is within the day before the
     * incident — which it always is: the reference is the last reading taken while the operator
     * was moving, on the same watch. `AT 19:10:00Z` beside an incident at `01:10:00Z` is the
     * evening before, and nothing else it could be. Eleven bytes a line, which is three more
     * entries in the largest QR; the full instant is kept for the case that should not occur.
     */
    private fun baselineAt(record: IncidentRecord): String {
        val before = record.atMillis - record.baselineAtMillis
        val stamp = zulu(record.baselineAtMillis)
        return if (before in 0L until DAY_MILLIS) stamp.substring(DATE_CHARS) else stamp
    }

    /** `08:00` — the booked length of the watch, in hours and minutes. */
    private fun duration(millis: Long): String {
        val minutes = millis / 60_000L
        return "%02d:%02d".format(minutes / 60L, minutes % 60L)
    }

    /** Three digits, so the columns line up under each other on a fixed-width terminal. */
    private fun pad(bpm: Int): String = if (bpm < 100) "0$bpm" else bpm.toString()

    const val HEADER: String = "MFD24 INCIDENT LOG\r\n"
    private const val CRLF = "\r\n"
    private const val DAY_MILLIS = 24L * 3_600_000L

    /** `2026-08-20T` — what is cut from an instant to leave the time of day. */
    private const val DATE_CHARS = 11
}
