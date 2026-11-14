// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import com.avdesign.mfd24.data.PlaceZone
import com.avdesign.mfd24.data.TelemetryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The rules of [DialZone], on the cases that found them: JFK typed into a watch kept in Kyiv,
 * which put the sun seven hours outside its own daylight band; the flight over the Atlantic,
 * which did the same with a measured position; and a pilot's watch kept on UTC.
 */
class DialZoneTest {

    private val now = Instant.parse("2026-11-13T13:11:00Z").toEpochMilli()
    private val kyivOffset = 2 * HOUR

    /** [follows] is what the repository publishes: typed, or measured with LOCAL TIME at POSITION. */
    private fun stateAt(zone: String, source: Int, follows: Boolean): TelemetryState {
        val state = TelemetryState()
        val window = PlaceZone.Window()
        PlaceZone.window(ZoneId.of(zone), now, window)
        state.setPlaceZone(window)
        state.positionSource = source
        state.dialFollowsPlace = follows
        return state
    }

    @Test
    fun `a typed position brings its clock with it`() {
        val state = stateAt("America/New_York", TelemetryState.POSITION_MANUAL, follows = true)
        val dial = DialZone.dialOffsetMillis(kyivOffset, state, now)
        assertEquals(-5 * HOUR, dial)
        assertTrue("the sky agrees with the place's own clock", DialZone.skyAgrees(dial, state, now))
    }

    @Test
    fun `a measured position with LOCAL TIME at POSITION crosses zones under the wearer`() {
        // Over Newfoundland on BA 178: the dial is at St John's half-hour zone, not the phone's.
        val state = stateAt("America/St_Johns", TelemetryState.POSITION_DEVICE, follows = true)
        val dial = DialZone.dialOffsetMillis(-5 * HOUR, state, now)
        assertEquals(-3 * HOUR - HOUR / 2, dial)
        assertTrue(DialZone.skyAgrees(dial, state, now))
    }

    @Test
    fun `a measured position with LOCAL TIME at PHONE leaves the device's clock alone`() {
        val state = stateAt("America/New_York", TelemetryState.POSITION_DEVICE, follows = false)
        assertEquals(0L, DialZone.dialOffsetMillis(0L, state, now))
    }

    @Test
    fun `a pilot on UTC in New York keeps UTC and loses the sky`() {
        val state = stateAt("America/New_York", TelemetryState.POSITION_DEVICE, follows = false)
        val dial = DialZone.dialOffsetMillis(0L, state, now)
        assertFalse(DialZone.skyAgrees(dial, state, now))
    }

    @Test
    fun `the sky stays when the device's zone is the place's, under any of its names`() {
        // The TicWatch reports Europe/Kiev; the grid says Europe/Kyiv. Offsets are compared.
        val state = stateAt("Europe/Kyiv", TelemetryState.POSITION_DEVICE, follows = false)
        val dial = DialZone.dialOffsetMillis(kyivOffset, state, now)
        assertTrue(DialZone.skyAgrees(dial, state, now))
    }

    @Test
    fun `without a zone for the place nothing changes`() {
        val state = TelemetryState()
        state.positionSource = TelemetryState.POSITION_MANUAL
        state.dialFollowsPlace = true
        assertEquals(kyivOffset, DialZone.dialOffsetMillis(kyivOffset, state, now))
        assertTrue(DialZone.skyAgrees(kyivOffset, state, now))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
