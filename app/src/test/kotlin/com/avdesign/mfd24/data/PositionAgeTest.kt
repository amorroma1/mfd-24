// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** How much a measured position is still worth: speed times age, never age alone. */
class PositionAgeTest {

    private val minute = 60_000L
    private val fix = 1_800_000_000_000L
    private val cruise = 250f // m/s, an airliner
    private val car = 25f // m/s, 90 km/h

    @Test
    fun `a watch that was standing still keeps a fix of any age`() {
        assertEquals(PositionAge.FRESH, PositionAge.of(fix + 600 * minute, fix, 0f))
        assertEquals(PositionAge.FRESH, PositionAge.of(fix + 600 * minute, fix, 1.4f))
    }

    @Test
    fun `a fix stored before ages were kept is fresh`() {
        assertEquals(PositionAge.FRESH, PositionAge.of(fix, 0L, cruise))
    }

    @Test
    fun `on the move it dims once it may be past the site lock`() {
        // 25 m/s covers five kilometres in 200 s.
        assertEquals(PositionAge.FRESH, PositionAge.of(fix + 190_000L, fix, car))
        assertEquals(PositionAge.DRIFTING, PositionAge.of(fix + 210_000L, fix, car))
        // An airliner gets there in twenty seconds.
        assertEquals(PositionAge.DRIFTING, PositionAge.of(fix + 21_000L, fix, cruise))
    }

    @Test
    fun `half an hour on the move without a fix and it is lost`() {
        assertEquals(PositionAge.DRIFTING, PositionAge.of(fix + 30 * minute, fix, car))
        assertEquals(PositionAge.LOST, PositionAge.of(fix + 31 * minute, fix, car))
    }

    @Test
    fun `a clock behind the fix does not age it`() {
        assertEquals(PositionAge.FRESH, PositionAge.of(fix - minute, fix, cruise))
    }

    @Test
    fun `the platform's own speed wins`() {
        assertEquals(12f, PositionAge.speedFor(12f, 50.0, 30.0, fix, 50.0, 31.0, fix - minute), 0f)
    }

    @Test
    fun `two fixes close in time give their average speed`() {
        // 0.1 degree of latitude, about 11.1 km, in ten minutes: 18.5 m/s.
        val speed = PositionAge.speedFor(-1f, 50.1, 30.0, fix, 50.0, 30.0, fix - 10 * minute)
        assertEquals(18.5f, speed, 0.2f)
    }

    @Test
    fun `fixes hours apart say nothing about the speed now`() {
        // JFK at the gate, Heathrow seven hours later: standing on the jet bridge.
        val speed = PositionAge.speedFor(-1f, 51.47, -0.45, fix, 40.64, -73.78, fix - 420 * minute)
        assertEquals(0f, speed, 0f)
    }

    @Test
    fun `fixes seconds apart are jitter, not speed`() {
        assertEquals(0f, PositionAge.speedFor(-1f, 50.001, 30.0, fix, 50.0, 30.0, fix - 20_000L), 0f)
    }
}
