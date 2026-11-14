// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The position as a chart writes it, inside the site row's thirteen characters. */
class CoordinatesTest {

    private fun format(lat: Double, lon: Double): String {
        val buf = TextBuf(40)
        Coordinates.append(buf, lat, lon)
        return String(buf.chars, 0, buf.length)
    }

    @Test
    fun `latitude first, hemisphere after, whole minutes`() {
        assertEquals("50°27N 30°31E", format(50.4501, 30.5234))
        assertEquals("40°38N 73°47W", format(40.6413, -73.7781))
        assertEquals("33°52S151°13E", format(-33.8688, 151.2093))
        assertEquals("05°03N 3°02W", format(5.05, -3.04))
    }

    @Test
    fun `a three-figure longitude gives up the space`() {
        assertEquals("15°30N120°59E", format(15.5, 120.9833))
    }

    @Test
    fun `minutes round, and carry into the degree`() {
        assertEquals("51°00N 0°00E", format(50.9999, 0.0001))
    }

    @Test
    fun `zero is north and east, never a signed nought`() {
        assertEquals("00°00N 0°00E", format(-0.001, -0.001))
    }

    @Test
    fun `the worst corner of the world is thirteen characters`() {
        for (lat in listOf(-89.99, -45.5, 0.0, 45.5, 89.99)) {
            for (lon in listOf(-179.99, -100.0, -99.99, 0.0, 99.99, 100.0, 179.99)) {
                val text = format(lat, lon)
                assertTrue("$text is ${text.length}", text.length <= Coordinates.MAX_LENGTH)
            }
        }
        assertEquals(13, format(-89.99, -179.99).length)
    }
}
