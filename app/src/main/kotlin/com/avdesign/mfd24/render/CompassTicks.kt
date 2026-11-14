// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The cheap astro compass: which way the sky marks run, which end of the meridian is north, and
 * whether the dial may point at it.
 *
 * A sky mark stands at its body's hour angle, so turning the watch until the mark points at the
 * real sun or moon lays the dial's noon tick along the body's meridian. That only works while
 * the mark crosses the dial the way the body crosses the sky. Seen from above, a body that
 * culminates south of the wearer — the northern hemisphere outside the tropics — runs east, south,
 * west: clockwise, like the hands. One that culminates north runs anticlockwise, and a clockwise
 * mark aimed at it at three in the afternoon left the noon tick ninety degrees from north. So the
 * mark is [mirrored] there, at minus the hour angle: it runs against the hands, as the body runs
 * against them in that sky, and the noon tick points north. Decided per body, from its own
 * declination, because in the tropics the sun and the moon can culminate on opposite sides.
 *
 * The method is still approximate — hour angle is not azimuth — and it fails outright as the body
 * passes near the zenith, where its azimuth swings half a turn in minutes: the equator at noon.
 * The watch knows the true azimuth, so it knows its own error, and the tick is **pointed** only
 * while that error is within [MAX_ERROR_DEG]. The colours stay either way: which end is north
 * is true wherever the body is.
 */
object CompassTicks {

    /** Past this the point would promise more than aiming a mark can deliver. */
    const val MAX_ERROR_DEG: Double = 15.0

    /** Whether a body of [declinationDeg] culminates north of the wearer and runs anticlockwise. */
    fun mirrored(latitudeDeg: Double, declinationDeg: Double): Boolean =
        latitudeDeg < declinationDeg

    /** North is the midnight end of the meridian when the body culminates south of the wearer. */
    fun northAtMidnight(latitudeDeg: Double, declinationDeg: Double): Boolean =
        !mirrored(latitudeDeg, declinationDeg)

    /**
     * How far from true north the dial's north end points when the body's mark is aimed at the
     * body, in degrees, signed. [hourAngleDeg] is positive west of the meridian.
     */
    fun errorDeg(latitudeDeg: Double, declinationDeg: Double, hourAngleDeg: Double): Double {
        val phi = Math.toRadians(latitudeDeg)
        val delta = Math.toRadians(declinationDeg)
        val h = Math.toRadians(hourAngleDeg)
        // Azimuth from north, clockwise.
        val azimuth =
            Math.toDegrees(atan2(sin(h), cos(h) * sin(phi) - tan(delta) * cos(phi))) + 180.0
        val mirror = mirrored(latitudeDeg, declinationDeg)
        val markFromNoon = if (mirror) -hourAngleDeg else hourAngleDeg
        val noonTick = azimuth - markFromNoon
        val north = if (mirror) noonTick else noonTick + 180.0
        return wrap(north)
    }

    fun pointed(latitudeDeg: Double, declinationDeg: Double, hourAngleDeg: Double): Boolean =
        abs(errorDeg(latitudeDeg, declinationDeg, hourAngleDeg)) <= MAX_ERROR_DEG

    private fun wrap(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
