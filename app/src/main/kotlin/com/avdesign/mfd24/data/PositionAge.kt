// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import com.avdesign.mfd24.geo.Geo

/**
 * How far a measured position may have moved since it was taken — which is what decides whether
 * the dial may go on presenting it as where the wearer is.
 *
 * The last fix is never thrown away: on a flight the cabin hides the sky for hours, and a dial
 * that went to dashes there would give up the band, the sun and the zone exactly when they are
 * most wanted. So the position stays and the symbol at the end of the location row says how much
 * it is worth: [FRESH], [DRIFTING] once it may be further off than the site lock's five kilometres,
 * [LOST] after half an hour without a fix while moving.
 *
 * Age alone cannot decide it. The worker asks every half hour, Doze stretches that, and a watch on
 * a desk routinely holds a fix an hour old that is still exactly right — a rule on time alone
 * would strike the symbol out at home most of the day. Distance is speed × age, and a watch that
 * was standing still when it last knew where it was is taken to be standing still yet.
 */
object PositionAge {

    const val FRESH: Int = 0
    const val DRIFTING: Int = 1
    const val LOST: Int = 2

    /** Twenty kilometres an hour: faster than anybody walks or a ship drifts at anchor. */
    const val MOVING_MPS: Float = 20f / 3.6f

    /** The site lock's radius: past this the row could be naming the wrong place. */
    const val DRIFT_METERS: Double = 5_000.0

    /** Half an hour without a fix while moving, and the position is history. */
    const val LOST_MILLIS: Long = 30 * 60 * 1000L

    /**
     * Two fixes further apart than this say nothing about the speed now: JFK at the gate and
     * Heathrow seven hours later average 220 m/s for a watch that is standing on the jet bridge.
     */
    const val SPEED_WINDOW_MILLIS: Long = 30 * 60 * 1000L

    /** Closer together than this, a few metres of jitter would read as a sprint. */
    const val SPEED_MIN_MILLIS: Long = 60 * 1000L

    fun of(nowMillis: Long, fixMillis: Long, speedMps: Float): Int {
        if (fixMillis <= 0L || !(speedMps >= MOVING_MPS)) return FRESH
        val age = nowMillis - fixMillis
        if (age <= 0L) return FRESH
        if (age > LOST_MILLIS) return LOST
        return if (speedMps * (age / 1000.0) > DRIFT_METERS) DRIFTING else FRESH
    }

    /**
     * The speed to keep with a new fix: the platform's own when it gave one (GPS does), otherwise
     * the average from the previous fix if the two are close enough in time to mean anything, and
     * otherwise zero. [previousMillis] is 0 when there is none.
     */
    fun speedFor(
        reportedMps: Float,
        lat: Double,
        lon: Double,
        fixMillis: Long,
        previousLat: Double,
        previousLon: Double,
        previousMillis: Long,
    ): Float {
        if (reportedMps >= 0f && !reportedMps.isNaN()) return reportedMps
        if (previousMillis <= 0L || previousLat.isNaN() || previousLon.isNaN()) return 0f
        val dt = fixMillis - previousMillis
        if (dt < SPEED_MIN_MILLIS || dt > SPEED_WINDOW_MILLIS) return 0f
        val meters = Geo.haversineMeters(previousLat, previousLon, lat, lon)
        return (meters / (dt / 1000.0)).toFloat()
    }
}
