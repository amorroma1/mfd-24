// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which drums turn, which way, and when a change is simply set instead. */
class DrumRowTest {

    private val t0 = 1_800_000_000_000L

    /** A drum that has been on screen, awake, showing [text]. */
    private fun watching(text: String): DrumRow = DrumRow(40).apply {
        show(this, text, t0 - 1_000L)
        show(this, text, t0)
    }

    private fun show(drum: DrumRow, text: String, at: Long, ambient: Boolean = false) {
        val chars = text.toCharArray()
        drum.update(chars, chars.size, at, ambient)
    }

    private fun turning(drum: DrumRow, length: Int, at: Long): List<Int> =
        (0 until length).filter { drum.progress(it, at) < 1f }

    @Test
    fun `only the figure that changed turns, and it starts at once`() {
        val drum = watching("QFE 1007")
        val at = t0 + 1_000L
        show(drum, "QFE 1008", at)
        assertTrue(drum.rolling)
        assertEquals(listOf(7), turning(drum, 8, at + 20L))
        assertTrue(drum.progress(7, at + 20L) > 0f)
        assertTrue(drum.upward[7])
    }

    @Test
    fun `a clock turns forwards past the hour`() {
        val drum = DrumRow(24, forwardOnly = true).apply {
            show(this, "Z 15NOV 13:59", t0 - 1_000L)
            show(this, "Z 15NOV 13:59", t0)
        }
        show(drum, "Z 15NOV 14:00", t0 + 1_000L)
        val end = t0 + 1_000L + 4 * DrumRow.STAGGER_MILLIS
        assertEquals(listOf(9, 11, 12), turning(drum, 13, end))
        for (i in listOf(9, 11, 12)) assertTrue("column $i", drum.upward[i])
    }

    @Test
    fun `a row that grew at the front lines the units up under the units`() {
        val drum = watching("9°C FEW Q1017")
        show(drum, "10°C FEW Q1017", t0 + 1_000L)
        assertEquals(' ', drum.fromChars[0])
        assertEquals('9', drum.fromChars[1])
        assertEquals('°', drum.fromChars[2])
    }

    @Test
    fun `a number that rose rolls up and one that fell rolls down`() {
        val drum = watching("5°C FEW Q1017")
        show(drum, "6°C FEW Q1009", t0 + 1_000L)
        assertTrue(drum.upward[0])
        // 1017 to 1009 is a fall: the 1 and the 7 both turn down, though 7 to 9 alone would rise.
        assertFalse(drum.upward[11])
        assertFalse(drum.upward[12])
    }

    @Test
    fun `below zero a growing digit is a falling temperature`() {
        val drum = watching("-3°C OVC Q1013")
        show(drum, "-5°C OVC Q1013", t0 + 1_000L)
        assertFalse(drum.upward[1])
    }

    @Test
    fun `the decimal point does not split a distance`() {
        val drum = watching("KJFK 9.9KM")
        show(drum, "KJFK 10.0KM", t0 + 1_000L)
        // 9.9 to 10.0 is a rise, so the tenths turn up as well, from 9 to 0.
        for (i in turning(drum, 11, t0 + 1_000L)) assertTrue("column $i", drum.upward[i])
    }

    @Test
    fun `a number that lost a figure lines up from the right`() {
        val drum = watching("KJFK 10.0KM")
        show(drum, "KJFK 1.2KM", t0 + 1_000L)
        // Units under units: the old 0 of 10 turns into 1, and the whole number turns down.
        assertEquals('0', drum.fromChars[5])
        assertEquals('0', drum.fromChars[7])
        assertFalse(drum.upward[5])
        assertFalse(drum.upward[7])
    }

    @Test
    fun `columns start one after another and all come to rest`() {
        val drum = watching("5°C FEW Q1017")
        show(drum, "6°C BKN Q1017", t0 + 1_000L)
        val start = t0 + 1_000L
        assertTrue(drum.progress(0, start + 20L) > 0f)
        assertEquals(0f, drum.progress(4, start + 20L))
        val end = start + 12 * DrumRow.STAGGER_MILLIS + DrumRow.DURATION_MILLIS
        show(drum, "6°C BKN Q1017", end)
        assertFalse(drum.rolling)
    }

    @Test
    fun `a change met in always-on is set at once, and so is the first frame after it`() {
        val drum = watching("5°C FEW Q1017")
        show(drum, "6°C FEW Q1017", t0 + 60_000L, ambient = true)
        assertFalse(drum.rolling)
        val back = watching("5°C FEW Q1017")
        show(back, "5°C FEW Q1017", t0 + 60_000L, ambient = true)
        show(back, "6°C FEW Q1017", t0 + 61_000L)
        assertFalse(back.rolling)
    }

    @Test
    fun `a change after the face was long away is set at once`() {
        val drum = watching("5°C FEW Q1017")
        show(drum, "6°C FEW Q1017", t0 + DrumRow.MAX_AWAY_MILLIS + 1L)
        assertFalse(drum.rolling)
    }

    @Test
    fun `a change made in the editor turns on the way back`() {
        val drum = watching("KJFK 0.1KM")
        show(drum, "IEV 0.3KM", t0 + 40_000L)
        assertTrue(drum.rolling)
    }

    @Test
    fun `the first frame ever turns nothing`() {
        val drum = DrumRow(40)
        show(drum, "5°C FEW Q1017", t0)
        assertFalse(drum.rolling)
    }

    @Test
    fun `a clock that jumped backwards settles the drums`() {
        val drum = watching("5°C FEW Q1017")
        show(drum, "6°C FEW Q1017", t0 + 1_000L)
        assertEquals(1f, drum.progress(0, t0 - 60_000L))
    }

    @Test
    fun `the easing starts at rest and ends at rest`() {
        assertEquals(0f, DrumRow.ease(0L))
        assertEquals(1f, DrumRow.ease(DrumRow.DURATION_MILLIS))
        var last = 0f
        for (ms in 0L..DrumRow.DURATION_MILLIS step 10L) {
            val v = DrumRow.ease(ms)
            assertTrue(v >= last)
            last = v
        }
    }
}
