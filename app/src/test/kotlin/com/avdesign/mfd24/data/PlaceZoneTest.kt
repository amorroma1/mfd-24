// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Choosing a zone a dated device knows, and the three numbers the renderer reads instead of the
 * rules. The windows are checked against instants worked out by hand from the published rules,
 * not against `java.time` itself, so a mistake in the reduction cannot hide behind the library.
 */
class PlaceZoneTest {

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `the newest name the device knows wins`() {
        val names = arrayOf("Europe/Kyiv", "Europe/Kiev")
        assertEquals(ZoneId.of("Europe/Kyiv"), PlaceZone.choose(names, 7200) { true })
        // A watch whose tzdata predates the 2022 spelling.
        assertEquals(ZoneId.of("Europe/Kiev"), PlaceZone.choose(names, 7200) { it == "Europe/Kiev" })
    }

    @Test
    fun `a device that knows none of the names gets the standard offset, not UTC`() {
        val zone = PlaceZone.choose(arrayOf("Mars/Olympus_Mons"), 19800) { false }
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), zone)
    }

    @Test
    fun `a name the device claims but cannot load is the same as an unknown one`() {
        val zone = PlaceZone.choose(arrayOf("Not/A_Zone", "Asia/Kathmandu"), 0) { true }
        assertEquals(ZoneId.of("Asia/Kathmandu"), zone)
    }

    @Test
    fun `New York in November waits for the second Sunday of March`() {
        val window = PlaceZone.Window()
        PlaceZone.window(ZoneId.of("America/New_York"), millis("2026-11-13T13:11:00Z"), window)
        assertEquals(-5 * HOUR, window.offsetMillis)
        assertEquals(millis("2027-03-14T07:00:00Z"), window.transitionMillis)
        assertEquals(-4 * HOUR, window.offsetAfterMillis)
    }

    @Test
    fun `the southern summer runs the other way`() {
        val window = PlaceZone.Window()
        // Sydney in November is on daylight time, which ends on the first Sunday of April.
        PlaceZone.window(ZoneId.of("Australia/Sydney"), millis("2026-11-13T00:00:00Z"), window)
        assertEquals(11 * HOUR, window.offsetMillis)
        assertEquals(millis("2027-04-03T16:00:00Z"), window.transitionMillis)
        assertEquals(10 * HOUR, window.offsetAfterMillis)
    }

    @Test
    fun `Lord Howe moves its clocks by half an hour`() {
        val window = PlaceZone.Window()
        PlaceZone.window(ZoneId.of("Australia/Lord_Howe"), millis("2026-11-13T00:00:00Z"), window)
        assertEquals(11 * HOUR, window.offsetMillis)
        assertEquals(10 * HOUR + HOUR / 2, window.offsetAfterMillis)
    }

    @Test
    fun `a zone without summer time has no transition ahead`() {
        val window = PlaceZone.Window()
        PlaceZone.window(ZoneId.of("Asia/Kathmandu"), millis("2026-11-13T00:00:00Z"), window)
        assertEquals(5 * HOUR + 45 * MINUTE, window.offsetMillis)
        assertEquals(PlaceZone.NO_TRANSITION, window.transitionMillis)
        assertEquals(window.offsetMillis, window.offsetAfterMillis)

        PlaceZone.window(ZoneOffset.ofHours(-3), millis("2026-11-13T00:00:00Z"), window)
        assertEquals(-3 * HOUR, window.offsetMillis)
        assertEquals(PlaceZone.NO_TRANSITION, window.transitionMillis)
    }

    @Test
    fun `the state switches offset exactly at the transition`() {
        val state = TelemetryState()
        val window = PlaceZone.Window()
        PlaceZone.window(ZoneId.of("Europe/Kyiv"), millis("2027-03-01T00:00:00Z"), window)
        state.setPlaceZone(window)
        val change = millis("2027-03-28T01:00:00Z")
        assertEquals(change, window.transitionMillis)
        assertEquals(2 * HOUR, state.placeOffsetAt(change - 1))
        assertEquals(3 * HOUR, state.placeOffsetAt(change))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
