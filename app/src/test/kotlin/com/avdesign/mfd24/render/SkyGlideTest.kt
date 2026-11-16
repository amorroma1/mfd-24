// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a sky mark gets from one place's sky to another's. */
class SkyGlideTest {

    private val t0 = 1_800_000_000_000L
    private val rate = SkyGlide.SUN_DEGREES_PER_HOUR
    private val half = 90.0 // an equinox day: up while the hour angle is within six hours

    /** The sun's hour angle at [at], for a body standing at [start] degrees at t0. */
    private fun sun(start: Double, at: Long) = SkyGlide.wrap(start + rate * (at - t0) / 3_600_000.0)

    private fun glide(angle: Double): SkyGlide = SkyGlide(rate).apply {
        update(t0, angle, half, false, Math.abs(angle) < half)
    }

    /** Frames at 16 ms towards a body at [start]; the hour angles at which the mark was drawn. */
    private fun run(g: SkyGlide, start: Double, until: Long): List<Double> {
        val shown = mutableListOf<Double>()
        var t = t0 + 16L
        while (t <= until) {
            val a = sun(start, t)
            g.update(t, a, half, false, Math.abs(a) < half)
            if (g.level > 0f) shown += g.hourAngle
            t += 16L
        }
        return shown
    }

    @Test
    fun `time passing is not a jump, however long the screen was off`() {
        val g = glide(-30.0)
        val later = t0 + 3_600_000L
        g.update(later, sun(-30.0, later), half, false, true)
        assertFalse(g.animating)
        assertEquals(-15.0, g.hourAngle, 1e-6)
    }

    @Test
    fun `jumping east before dawn rolls the sun up on the sunrise side`() {
        // An hour before sunrise; the wearer jumps 30 degrees east, two hours of hour angle.
        val g = glide(-105.0)
        assertEquals(0f, g.level)
        val shown = run(g, -75.0, t0 + 5_000L)
        assertTrue(shown.isNotEmpty())
        // It appears at the eastern horizon and moves forwards from there.
        assertEquals(-half, shown.first(), 3.0)
        for (k in 1 until shown.size) assertTrue(shown[k] >= shown[k - 1] - 1e-9)
        assertEquals(1f, g.level)
    }

    @Test
    fun `jumping west after dusk brings the sun back over the sunset side`() {
        val g = glide(105.0)
        // Within the glide: once it has landed the sun goes on with its own westward day.
        val shown = run(g, 75.0, t0 + DialTransition.DURATION_MILLIS - 16L)
        assertTrue(shown.isNotEmpty())
        assertEquals(half, shown.first(), 3.0)
        for (k in 1 until shown.size) assertTrue(shown[k] <= shown[k - 1] + 1e-9)
    }

    @Test
    fun `a zone change moves nothing, because the hour angle does not change`() {
        val g = glide(20.0)
        g.update(t0 + 16L, sun(20.0, t0 + 16L), half, false, true)
        assertFalse(g.animating)
    }

    @Test
    fun `crossing to the far side of the body is faded, not swept`() {
        val g = glide(20.0)
        g.update(t0 + 16L, 20.0, half, true, true)
        assertEquals(20.0, g.hourAngle, 0.01)
        assertTrue(g.level < 0.1f)
        g.update(t0 + 1_000L, 20.0, half, true, true)
        assertEquals(1f, g.level)
    }

    @Test
    fun `the glide ends exactly on the body`() {
        val g = glide(-105.0)
        val end = t0 + DialTransition.DURATION_MILLIS + 500L
        g.update(t0 + 16L, sun(-75.0, t0 + 16L), half, false, false)
        g.update(end, sun(-75.0, end), half, false, true)
        assertEquals(sun(-75.0, end), g.hourAngle, 1e-6)
        assertEquals(1f, g.level)
        assertFalse(g.animating)
    }

    @Test
    fun `half the day arc`() {
        assertEquals(90.0, SkyGlide.horizonOf(50.0, 0.0), 1e-9)
        assertEquals(180.0, SkyGlide.horizonOf(80.0, 20.0), 1e-9)
        assertEquals(0.0, SkyGlide.horizonOf(80.0, -20.0), 1e-9)
        assertTrue(SkyGlide.horizonOf(50.0, 23.4) > 90.0)
    }

    @Test
    fun `always-on gets the end of a glide, never the middle`() {
        val g = glide(-105.0)
        val at = t0 + 500L
        g.update(at, sun(-75.0, at), half, false, true, settle = true)
        assertEquals(sun(-75.0, at), g.hourAngle, 1e-6)
        assertEquals(1f, g.level)
        assertFalse(g.animating)
    }
}
