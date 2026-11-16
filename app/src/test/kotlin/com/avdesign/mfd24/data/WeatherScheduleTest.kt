// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** When the weather is asked for again, after a reading and after a failure. */
class WeatherScheduleTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun reading(fetchedAt: Long, issuedAt: Long = fetchedAt, metar: Boolean = false) =
        WeatherPlaces.Entry(50.45, 30.52, fetchedAt, issuedAt, metar, 50, 10_160, 0)

    @Test
    fun `a failure is retried after five seconds, thirty, three minutes and five, then not`() {
        val waits = (1..5).map { WeatherSchedule.retryDelayMillis(it) }
        assertEquals(listOf(5_000L, 30_000L, 180_000L, 300_000L, -1L), waits)
        assertEquals(-1L, WeatherSchedule.retryDelayMillis(0))
    }

    @Test
    fun `a model reading is due after the wearer's interval`() {
        for (minutes in WeatherSchedule.INTERVAL_MINUTES) {
            assertEquals(now + minutes * minute, WeatherSchedule.dueAt(reading(now), minutes * minute))
        }
    }

    @Test
    fun `a METAR is due when the next issue is out, not when the interval says`() {
        // Observed at :50, fetched at :55: the next routine report is an hour after the first.
        val metar = reading(now, issuedAt = now - 5 * minute, metar = true)
        assertEquals(now + 60 * minute, WeatherSchedule.dueAt(metar, 15 * minute))
        assertEquals(now + 60 * minute, WeatherSchedule.dueAt(metar, 30 * minute))
    }

    @Test
    fun `a METAR fetched late in its hour waits at least the interval`() {
        // Observed fifty-five minutes before the fetch: the next is due in ten, the interval rules.
        val metar = reading(now, issuedAt = now - 55 * minute, metar = true)
        assertEquals(now + 15 * minute, WeatherSchedule.dueAt(metar, 15 * minute))
        assertEquals(now + 30 * minute, WeatherSchedule.dueAt(metar, 30 * minute))
    }

    @Test
    fun `no reading goes unasked longer than an hour`() {
        // An issue time in the future is a clock disagreement, not a reason to wait for hours.
        val metar = reading(now, issuedAt = now + 3 * 60 * minute, metar = true)
        assertEquals(now + 60 * minute, WeatherSchedule.dueAt(metar, 15 * minute))
        assertEquals(now + 60 * minute, WeatherSchedule.dueAt(metar, 60 * minute))
    }

    @Test
    fun `only the offered intervals are taken`() {
        assertEquals(15, WeatherSchedule.intervalMinutes(15))
        assertEquals(60, WeatherSchedule.intervalMinutes(60))
        assertEquals(WeatherSchedule.DEFAULT_INTERVAL_MINUTES, WeatherSchedule.intervalMinutes(5))
        assertEquals(30, WeatherSchedule.DEFAULT_INTERVAL_MINUTES)
    }
}
