// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import com.avdesign.mfd24.geo.PoiResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The readout width budget, as arithmetic instead of prose.
 *
 * [Geometry] lays every row out on one figure — a 0.60 em monospace advance at one size — and the
 * rule that a horizontal line at offset y with half-width w reaches sqrt(w² + y²), which must stay
 * inside the hour numerals' inner edge. That arithmetic used to live only in comments, which is
 * exactly where a lengthened row or a nudged baseline goes unnoticed until it touches a numeral on
 * a wrist. This test re-derives every row's worst corner from the shipped constants, so changing
 * either side of the bargain fails the build instead.
 *
 * Since the one-size readout the worst corners are five to seven pixels inside the numerals, so
 * there is no slack here to spend: a longer row or a larger size has to come with a shorter one.
 */
class GeometryBudgetTest {

    /** Corner radius of a centred row of [chars] characters led by [lead] r of pictogram and gap. */
    private fun corner(chars: Int, y: Float, lead: Float = 0f): Float {
        val half = (chars * Geometry.MONO_ADVANCE * Geometry.TEXT_SIZE + lead) / 2f
        return sqrt((half * half + y * y).toDouble()).toFloat()
    }

    private fun assertInside(name: String, reach: Float) {
        assertTrue(
            "$name reaches ${"%.3f".format(reach)} r, past the numerals' " +
                "${Geometry.NUMERAL_INNER_EDGE} r",
            reach < Geometry.NUMERAL_INNER_EDGE,
        )
    }

    @Test
    fun `every readout row clears the hour numerals at its worst width`() {
        // `DUTY: 03:42 REM` — 15 characters; the incident form `21:14Z +01:09` is shorter.
        assertInside("duty row", corner(15, Geometry.LINE_BASELINES[0]))
        // `Z 18AUG 18:42` — 13 characters, no seconds.
        assertInside("zulu row", corner(13, Geometry.LINE_BASELINES[1]))
        // `-40°C TSGR Q1013` — 16 characters, bare: the Earth face draws no reference-frame
        // symbol. The multi-world faces lead the row with one, and their longest such row is
        // `♂ SOL 54321`.
        assertInside("weather row", corner(16, Geometry.LINE_BASELINES[2]))
        assertInside(
            "symbol-led row",
            corner(11, Geometry.LINE_BASELINES[2], Geometry.SYMBOL_SIZE + Geometry.GLYPH_GAP),
        )
        // Site row: a text-size pictogram, the gap, then `ABANDO 5.0KM` — six characters is the
        // longest code in the bundled data (the naval bases), and the lock radius keeps the
        // distance to one figure before the point.
        assertTrue(PoiResolver.DEFAULT_RADIUS_METERS < 10_000.0)
        assertInside(
            "site row",
            corner(12, Geometry.LINE_BASELINES[3], Geometry.TEXT_SIZE + Geometry.GLYPH_GAP),
        )
    }

    @Test
    fun `the outlying rows hold their own bargains`() {
        // `BAT 100%` — eight characters at full charge.
        assertInside("battery row", corner(8, Geometry.BATTERY_BASELINE))
        // `ACKNOWLEDGE` is the longest status word; `TAP AGAIN` and `MAN DOWN` are shorter.
        assertInside("status line", corner(11, Geometry.STATUS_BASELINE))
        // A sensor slot's outer edge: `QFE 1013` is the widest line, centred on the slot offset.
        val slotHalf = 8 * Geometry.MONO_ADVANCE * Geometry.TEXT_SIZE / 2f
        val outer = Geometry.SENSOR_OFFSET_X + slotHalf
        assertInside(
            "sensor slot",
            sqrt(
                (outer * outer + Geometry.SENSOR_BASELINE * Geometry.SENSOR_BASELINE).toDouble()
            ).toFloat(),
        )
    }

    @Test
    fun `every baseline on the dial sits on the one pitch from the hub line`() {
        val rows = floatArrayOf(
            Geometry.STATUS_BASELINE,
            Geometry.LINE_BASELINES[0],
            Geometry.LINE_BASELINES[1],
            Geometry.SENSOR_BASELINE,
            Geometry.LINE_BASELINES[2],
            Geometry.LINE_BASELINES[3],
            Geometry.BATTERY_BASELINE,
        )
        for (i in 1 until rows.size) {
            assertEquals("pitch between rows $i and ${i - 1}", Geometry.LINE_PITCH, rows[i] - rows[i - 1], 1e-6f)
        }
        assertEquals("the sensor slots sit on the hub line", Geometry.HUB_BASELINE, Geometry.SENSOR_BASELINE, 0f)
    }
}
