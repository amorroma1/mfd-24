// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.text

import kotlin.math.abs

/**
 * A position the way a chart, a deck log and a position report write it: latitude first, degrees
 * and minutes, the hemisphere after the figure — `50°27N 30°31E`. The aviation habit of the
 * letter in front (`N50°27`) and the flight plan's `5027N03031E` belong to one trade each; this
 * one is read by all of them.
 *
 * The site row it stands in for holds thirteen characters with room over (`GeometryBudgetTest`),
 * and that is [MAX_LENGTH] at the worst. The minute marks are dropped, and the space between the
 * two halves goes once the longitude needs three figures: `15°30N120°59E`. The hemisphere letters
 * already separate the two, and a row that changes width with the longitude is nothing new — the
 * site row does it with every site.
 *
 * Whole minutes, about 1.8 km: the precision of a fix a watch gets in the background, and no
 * finer than a screenshot of somebody's dial needs to say where they are.
 */
object Coordinates {

    const val MAX_LENGTH: Int = 13

    fun append(buf: TextBuf, latDeg: Double, lonDeg: Double): TextBuf {
        val latMinutes = Math.round(abs(latDeg) * 60.0).toInt()
        val lonMinutes = Math.round(abs(lonDeg) * 60.0).toInt()
        buf.pad2(latMinutes / 60).ch(DEGREE).pad2(latMinutes % 60)
            .ch(if (latDeg < 0.0 && latMinutes != 0) 'S' else 'N')
        val lonWhole = lonMinutes / 60
        if (lonWhole < 100) buf.space()
        return buf.uint(lonWhole).ch(DEGREE).pad2(lonMinutes % 60)
            .ch(if (lonDeg < 0.0 && lonMinutes != 0) 'W' else 'E')
    }

    private const val DEGREE = '°'
}
