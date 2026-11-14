// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The astro compass's three claims: which way a mark runs, which end of the meridian is north,
 * and when aiming a mark is good enough for the tick to be pointed.
 */
class CompassTicksTest {

    private val kyiv = 50.45
    private val sydney = -33.87
    private val december = -23.44
    private val june = 23.44

    @Test
    fun `a sun culminating south runs with the hands, one culminating north against them`() {
        assertFalse(CompassTicks.mirrored(kyiv, december))
        assertFalse(CompassTicks.mirrored(kyiv, june))
        assertTrue(CompassTicks.mirrored(sydney, december))
        assertTrue(CompassTicks.mirrored(sydney, june))
        // Singapore: the June sun stands north of it, the December sun south.
        assertTrue(CompassTicks.mirrored(1.35, june))
        assertFalse(CompassTicks.mirrored(1.35, december))
    }

    @Test
    fun `north is the midnight tick in Kyiv and the noon tick in Sydney`() {
        assertTrue(CompassTicks.northAtMidnight(kyiv, june))
        assertFalse(CompassTicks.northAtMidnight(sydney, december))
    }

    @Test
    fun `a Kyiv winter day is a compass from sunrise to sunset`() {
        for (hours in -3..3) {
            val error = CompassTicks.errorDeg(kyiv, december, hours * 15.0)
            assertTrue("$hours h: $error", abs(error) < 5.0)
        }
    }

    @Test
    fun `the mirrored southern sky is the northern one seen in a mirror`() {
        for (hourAngle in -75..75 step 15) {
            for (declination in listOf(-23.0, -10.0, 0.0, 10.0, 23.0)) {
                assertEquals(
                    "ha $hourAngle dec $declination",
                    -CompassTicks.errorDeg(40.0, declination, hourAngle.toDouble()),
                    CompassTicks.errorDeg(-40.0, -declination, hourAngle.toDouble()),
                    1e-6,
                )
            }
        }
    }

    @Test
    fun `Sydney in its winter afternoon is pointed, as Kyiv in its winter afternoon is`() {
        // Unmirrored, a mark aimed at the sun at three left the noon tick ninety degrees off.
        assertTrue(CompassTicks.pointed(sydney, june, 45.0))
        assertTrue(CompassTicks.pointed(kyiv, december, 45.0))
    }

    @Test
    fun `the high summer sun is not pointed`() {
        // Sydney at three on a December afternoon, the sun 50 degrees up: forty degrees out.
        assertFalse(CompassTicks.pointed(sydney, december, 45.0))
        // Kyiv at nine on a June morning.
        assertFalse(CompassTicks.pointed(kyiv, june, -45.0))
    }

    @Test
    fun `the equator at noon is never pointed`() {
        for (hourAngle in listOf(-10.0, -5.0, 5.0, 10.0)) {
            assertFalse("ha $hourAngle", CompassTicks.pointed(0.5, 0.0, hourAngle))
        }
    }
}
