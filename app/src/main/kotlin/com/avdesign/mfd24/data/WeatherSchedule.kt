// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

/**
 * When the weather is asked for again: after a reading, and after a failure.
 *
 * Neither source changes faster than a quarter of an hour — Open-Meteo's current conditions are
 * a fifteen-minute series at best, a METAR is hourly — so the wearer's interval starts at fifteen
 * minutes. A failure does not hammer the service: [RETRY_DELAYS_MILLIS] spaces four more tries over
 * about nine minutes and then hands back to the schedule.
 */
object WeatherSchedule {

    /** The choices offered in the editor, in minutes. */
    val INTERVAL_MINUTES = intArrayOf(15, 30, 60)

    const val DEFAULT_INTERVAL_MINUTES = 30

    /** Five seconds, thirty, three minutes, five — then the ordinary schedule. */
    val RETRY_DELAYS_MILLIS = longArrayOf(5_000L, 30_000L, 180_000L, 300_000L)

    /**
     * When the next METAR can be expected after one observed at a given time: routine reports are
     * hourly, and the feed has them a few minutes after the observation.
     */
    const val METAR_NEXT_MILLIS = 65 * 60_000L

    /** The longest any reading goes unasked, whatever the source says. */
    const val MAX_INTERVAL_MILLIS = 60 * 60_000L

    /** [minutes] if it is one of the offered intervals, the default otherwise. */
    fun intervalMinutes(minutes: Int): Int =
        if (minutes in INTERVAL_MINUTES) minutes else DEFAULT_INTERVAL_MINUTES

    /**
     * The wait before the next try after [failures] in a row, or -1 once the ladder is spent and
     * the schedule takes over again.
     */
    fun retryDelayMillis(failures: Int): Long =
        if (failures in 1..RETRY_DELAYS_MILLIS.size) RETRY_DELAYS_MILLIS[failures - 1] else -1L

    /**
     * When a reading is due to be replaced. Open-Meteo's after the wearer's interval. A METAR's
     * when the next issue should be out — no sooner than the interval, since asking earlier only
     * fetches the same report again, and no later than an hour, since a special report can come
     * between routine ones.
     */
    fun dueAt(entry: WeatherPlaces.Entry, intervalMillis: Long): Long {
        val earliest = entry.fetchedAt + intervalMillis
        if (!entry.metar) return earliest
        val latest = entry.fetchedAt + MAX_INTERVAL_MILLIS
        return (entry.issuedAt + METAR_NEXT_MILLIS).coerceIn(earliest, maxOf(earliest, latest))
    }
}
