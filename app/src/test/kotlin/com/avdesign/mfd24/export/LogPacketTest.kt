// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.export

import com.avdesign.mfd24.data.IncidentLog
import com.avdesign.mfd24.data.IncidentRecord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

/**
 * The over-the-air text: the receiving end is a human at a terminal, so the format is pinned
 * character for character — Zulu timestamps, a count, and a checksum that one line of shell can
 * verify.
 */
class LogPacketTest {

    @Test
    fun `a packet is header, identity, zulu lines, then a checkable END line`() {
        val packet = LogPacket.build(arrayOf(IncidentRecord(1_787_230_037_000L)), "RAVEN-42", "10396EB9")
        val lines = packet.split("\r\n")
        assertEquals("MFD24 INCIDENT LOG", lines[0])
        assertEquals("CALLSIGN RAVEN-42 ID 10396EB9", lines[1])
        assertEquals("2026-08-20T12:47:17Z", lines[2])
        assertTrue(lines[3].startsWith("END 1 CRC32 "))
        assertEquals("", lines[4])
    }

    @Test
    fun `the crc covers everything above the END line`() {
        val packet = LogPacket.build(
            arrayOf(IncidentRecord(1_787_230_037_000L), IncidentRecord(1_787_240_000_000L)), "RAVEN-42", "10396EB9",
        )
        val body = packet.substringBeforeLast("END ")
        val expected = CRC32().apply { update(body.toByteArray(Charsets.US_ASCII)) }.value
        val stated = packet.substringAfterLast("CRC32 ").trim().toLong(16)
        assertEquals(expected, stated)
    }

    @Test
    fun `an empty log still transmits a verifiable frame`() {
        // Sending "END 0" with a good checksum tells the debrief there is genuinely nothing on
        // file — silence would only say the transmission failed.
        val lines = LogPacket.build(emptyArray(), "RAVEN-42", "10396EB9").split("\r\n")
        assertEquals("MFD24 INCIDENT LOG", lines[0])
        assertEquals("CALLSIGN RAVEN-42 ID 10396EB9", lines[1])
        assertTrue(lines[2].startsWith("END 0 CRC32 "))
    }
    @Test
    fun `a reference within the day before the incident prints as a time of day`() {
        val at = 1_787_230_037_000L                       // 2026-08-20T12:47:17Z
        val evening = at - 15 * 3_600_000L                // 21:47:17Z the day before
        val lastWeek = at - 8 * 24 * 3_600_000L
        val lines = LogPacket.build(
            arrayOf(
                IncidentRecord(at, bpm = 48, baselineBpm = 72, baselineAtMillis = evening),
                IncidentRecord(at, bpm = 48, baselineBpm = 72, baselineAtMillis = lastWeek),
            ),
            "RAVEN-42", "10396EB9",
        ).split("\r\n")
        // Same watch, so the date is the incident's own and a time of day says it all.
        assertEquals("2026-08-20T12:47:17Z HR 048 BASE 072 AT 21:47:17Z", lines[2])
        // A reference older than a day should not happen; if it does, the line says exactly when.
        assertEquals("2026-08-20T12:47:17Z HR 048 BASE 072 AT 2026-08-12T12:47:17Z", lines[3])
    }

    @Test
    fun `the fitted packet keeps the newest entries and says how many it dropped`() {
        val log = Array(5) { IncidentRecord(1_787_230_037_000L + it * 600_000L) }
        val whole = LogPacket.build(log, "RAVEN-42", "10396EB9", 1_787_220_000_000L, 1_787_260_000_000L)
        // Room for three lines of the five, to the byte: the whole packet less two lines, plus
        // the " OF 5" the truncated END line grows.
        val fitted = LogPacket.fitting(
            log, "RAVEN-42", "10396EB9", 1_787_220_000_000L, 1_787_260_000_000L,
            capacityBytes = whole.length - 2 * "2026-08-20T12:47:17Z\r\n".length + " OF 5".length,
        )
        val lines = fitted.split("\r\n")
        assertEquals("2026-08-20T13:07:17Z", lines[3])   // the third-newest, not the oldest
        assertEquals("2026-08-20T13:27:17Z", lines[5])   // the newest
        assertTrue(lines[6].startsWith("END 3 OF 5 CRC32 "))
        val body = fitted.substringBeforeLast("END ")
        val expected = CRC32().apply { update(body.toByteArray(Charsets.US_ASCII)) }.value
        assertEquals(expected, fitted.substringAfterLast("CRC32 ").trim().toLong(16))
        // A packet that fits whole is the whole packet, with no OF on it.
        assertEquals(
            whole,
            LogPacket.fitting(log, "RAVEN-42", "10396EB9", 1_787_220_000_000L, 1_787_260_000_000L, whole.length),
        )
    }

    @Test
    fun `a full log is drawn by the encoder, bare entirely and with pulses as the newest fourteen`() {
        // The ceiling the screen hands the packet builder, measured against the encoder it feeds:
        // thirty-two bare instants fit the largest code; thirty-two lines carrying a pulse and its
        // reference do not, and the first version of the export screen threw here, inside
        // onCreate. From the shipped constants, so a longer line or a smaller code fails this.
        val t0 = 1_787_230_037_000L
        val start = t0 - 7_200_000L
        val end = t0 + 16 * 3_600_000L
        val bare = Array(IncidentLog.MAX_ENTRIES) { IncidentRecord(t0 + it * 600_000L) }
        val full = Array(IncidentLog.MAX_ENTRIES) {
            IncidentRecord(t0 + it * 600_000L, bpm = 48, baselineBpm = 72, baselineAtMillis = t0 + it * 600_000L - 3_600_000L)
        }
        val bareFitted = LogPacket.fitting(bare, "RAVEN-42", "10396EB9", start, end, QrCode.MAX_BYTES)
        assertEquals(LogPacket.build(bare, "RAVEN-42", "10396EB9", start, end), bareFitted)
        assertEquals(97, QrCode.encode(bareFitted).size)

        val wholeFull = LogPacket.build(full, "RAVEN-42", "10396EB9", start, end)
        assertTrue("the whole log must still be what the sound carries", wholeFull.contains("END 32 CRC32"))
        assertTrue(runCatching { QrCode.encode(wholeFull) }.isFailure)

        val fullFitted = LogPacket.fitting(full, "RAVEN-42", "10396EB9", start, end, QrCode.MAX_BYTES)
        assertTrue(fullFitted.contains("\r\nEND 14 OF 32 CRC32 "))
        assertEquals(97, QrCode.encode(fullFitted).size)
    }
}
