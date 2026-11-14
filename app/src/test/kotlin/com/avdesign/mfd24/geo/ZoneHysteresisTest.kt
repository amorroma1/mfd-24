// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos

/**
 * The border band on the shipped grid, at the Spanish–Portuguese border between Badajoz and
 * Elvas: an hour apart, seventeen kilometres apart, and a road between them that a wearer could
 * drive every day. The border is found in the grid itself rather than written down, so the test
 * measures distances from where the dial actually believes the line is.
 */
class ZoneHysteresisTest {

    private val grid: TzGrid by lazy {
        val file = listOf(
            File("src/main/assets/${TzGrid.ASSET_NAME}"),
            File("app/src/main/assets/${TzGrid.ASSET_NAME}"),
        ).first { it.exists() }
        TzGrid.fromBytes(file.readBytes())!!
    }

    private val lat = 38.88
    private val kmOfLon = 1.0 / (111.32 * cos(Math.toRadians(lat)))

    private val lisbon by lazy { grid.zoneAt(lat, -7.40) }
    private val madrid by lazy { grid.zoneAt(lat, -6.70) }

    /** The first longitude east of Elvas that the grid puts in Spain. */
    private val border: Double by lazy {
        var lon = -7.40
        while (grid.zoneAt(lat, lon) == lisbon) lon += 0.001
        lon
    }

    @Test
    fun `the fixture straddles the border`() {
        assertEquals("Europe/Lisbon", grid.names(lisbon)[0])
        assertEquals("Europe/Madrid", grid.names(madrid)[0])
        assertTrue(border > -7.30 && border < -6.90)
    }

    @Test
    fun `a fix a kilometre into Spain keeps Portuguese time`() {
        val r = ZoneHysteresis.MIN_RADIUS_METERS
        assertTrue(ZoneHysteresis.keeps(grid, lat, border + 1 * kmOfLon, r, lisbon))
        // And the same holds the other way round.
        assertTrue(ZoneHysteresis.keeps(grid, lat, border - 1 * kmOfLon, r, madrid))
    }

    @Test
    fun `a fix well past the band gives the old zone up`() {
        val r = ZoneHysteresis.MIN_RADIUS_METERS
        assertFalse(ZoneHysteresis.keeps(grid, lat, border + 6 * kmOfLon, r, lisbon))
        assertFalse(ZoneHysteresis.keeps(grid, lat, border - 6 * kmOfLon, r, madrid))
    }

    @Test
    fun `a fix inside the zone in force always keeps it`() {
        assertTrue(ZoneHysteresis.keeps(grid, lat, -7.40, 0.0, lisbon))
    }

    @Test
    fun `wandering across the line inside the band never changes the answer`() {
        // A fix jittering two kilometres either side of the border, starting in Portugal: the
        // zone in force must stay Portuguese throughout. Once it has been given up — far into
        // Spain — the same jitter must keep it Spanish.
        val r = ZoneHysteresis.MIN_RADIUS_METERS
        for (step in -20..20) {
            val lon = border + step / 10.0 * kmOfLon
            assertTrue("at ${step / 10.0} km", ZoneHysteresis.keeps(grid, lat, lon, r, lisbon))
            assertTrue("at ${step / 10.0} km", ZoneHysteresis.keeps(grid, lat, lon, r, madrid))
        }
    }

    @Test
    fun `a fix that reports poor accuracy widens the band`() {
        assertEquals(3_000.0, ZoneHysteresis.radiusFor(Float.NaN), 0.0)
        assertEquals(3_000.0, ZoneHysteresis.radiusFor(0f), 0.0)
        assertEquals(3_000.0, ZoneHysteresis.radiusFor(40f), 0.0)
        assertEquals(8_000.0, ZoneHysteresis.radiusFor(8_000f), 0.0)
        // Six kilometres in is past a 3 km band, but not past an 8 km one.
        val lon = border + 6 * kmOfLon
        assertTrue(ZoneHysteresis.keeps(grid, lat, lon, ZoneHysteresis.radiusFor(8_000f), lisbon))
    }
}
