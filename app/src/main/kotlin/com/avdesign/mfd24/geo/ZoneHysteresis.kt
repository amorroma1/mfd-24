// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.geo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * When a measured position may move the dial into another time zone.
 *
 * A fix near a zone border is not one point but a cloud: GPS wanders by tens of metres, the
 * network fix a watch gets in the background by hundreds, a cell-tower fix by a kilometre or two.
 * Looked up raw, a wearer standing on the border would have the dial glide an hour one way and
 * back again on alternate refreshes. So the zone in force is kept while any point within
 * [radiusMeters] of the fix still lies in it, and given up only once the fix is that far inside
 * the new one. Crossing from A to B switches 3 km into B; coming back switches 3 km into A; a
 * fix has to jump six kilometres across the line to make the dial move twice, and that is a
 * journey, not noise.
 *
 * The radius is [MIN_RADIUS_METERS] or the fix's own reported accuracy, whichever is larger:
 *
 * - **Not less than 3 km**, because twice that has to cover the ordinary worst of a background fix
 *   — one to two kilometres from towers in a town — and because the grid itself only knows a
 *   border to its 0.05° cell, about 5.5 km; a band narrower than half a cell would be decided by
 *   the grid's quantisation rather than by distance.
 * - **Not more by default**, because the band is also a delay: after a real crossing the dial
 *   keeps the old hour for those 3 km — three minutes by road, twelve seconds at cruise — and at
 *   5 km a town on a border (Badajoz and Elvas are 17 km apart, an hour apart) would spend much of
 *   the way between them on the wrong one.
 * - **More when the fix says so.** A fix reporting 5 km of accuracy is a cloud 5 km wide, and the
 *   band has to be at least that.
 *
 * A typed position is never put through this: it is a decision, not a measurement.
 */
object ZoneHysteresis {

    const val MIN_RADIUS_METERS: Double = 3_000.0

    /** Points on each ring. Eight at 45° leave gaps of 2.3 km at 3 km, under half a grid cell. */
    private const val RING_POINTS = 8

    /** The band for a fix of [accuracyMeters]; zero or unknown accuracy gives the floor. */
    fun radiusFor(accuracyMeters: Float): Double =
        max(MIN_RADIUS_METERS, if (accuracyMeters.isNaN()) 0.0 else accuracyMeters.toDouble())

    /**
     * Whether [currentZone] should be kept at a fix of ([latDeg], [lonDeg]): true when the fix, or
     * any point on two rings around it at half and the whole of [radiusMeters], lies in it.
     */
    fun keeps(grid: TzGrid, latDeg: Double, lonDeg: Double, radiusMeters: Double, currentZone: Int): Boolean {
        if (grid.zoneAt(latDeg, lonDeg) == currentZone) return true
        for (ring in 1..2) {
            val r = radiusMeters * ring / 2.0
            val dLat = Geo.latDeltaDegrees(r)
            val dLon = Geo.lonDeltaDegrees(latDeg, r)
            for (i in 0 until RING_POINTS) {
                val a = 2.0 * Math.PI * i / RING_POINTS
                val lat = (latDeg + dLat * sin(a)).coerceIn(-90.0, 90.0)
                val lon = lonDeg + dLon * cos(a)
                if (grid.zoneAt(lat, lon) == currentZone) return true
            }
        }
        return false
    }
}
