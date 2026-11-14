// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped grid, read by the shipped reader, at the places where time zones are hardest.
 *
 * Every case here is somewhere a naive answer — longitude over fifteen, the nearest city, the
 * zone of the country — gives the wrong clock: half- and three-quarter-hour offsets, a summer time
 * of thirty minutes, a reservation inside a state that ignores summer time, the date line with
 * fourteen hours either side of it, two capitals seventeen kilometres apart on different hours,
 * and open water, where the nautical bands take over.
 */
class TzGridTest {

    private val grid: TzGrid by lazy {
        val file = listOf(
            File("src/main/assets/${TzGrid.ASSET_NAME}"),
            File("app/src/main/assets/${TzGrid.ASSET_NAME}"),
        ).first { it.exists() }
        TzGrid.fromBytes(file.readBytes()) ?: error("the shipped grid does not parse")
    }

    private fun zone(lat: Double, lon: Double): String = grid.names(grid.zoneAt(lat, lon))[0]

    @Test
    fun `cities whose offsets are not whole hours`() {
        assertEquals("Asia/Kolkata", zone(19.08, 72.88))           // +5:30
        assertEquals("Asia/Kathmandu", zone(27.70, 85.32))         // +5:45
        assertEquals("Australia/Eucla", zone(-31.68, 128.88))      // +8:45
        assertEquals("Pacific/Chatham", zone(-43.95, -176.55))     // +12:45, +13:45 in summer
        assertEquals("Australia/Lord_Howe", zone(-31.55, 159.08))  // a 30-minute summer time
        assertEquals("America/St_Johns", zone(47.56, -52.71))      // -3:30
    }

    @Test
    fun `the date line and the ends of the clock`() {
        assertEquals("Pacific/Kiritimati", zone(1.87, -157.43))    // +14, though west of Hawaii's -10
        assertEquals("Pacific/Apia", zone(-13.83, -171.76))        // +13
        assertEquals("Pacific/Pago_Pago", zone(-14.28, -170.70))   // -11, a hundred kilometres away
        // Either side of 180 degrees is the same column, however it is written.
        assertEquals(grid.zoneAt(-17.0, 180.0), grid.zoneAt(-17.0, -180.0))
    }

    @Test
    fun `borders a few kilometres apart`() {
        assertEquals("Europe/Madrid", zone(38.88, -6.97))          // Badajoz
        assertEquals("Europe/Lisbon", zone(38.88, -7.16))          // Elvas, 17 km west, an hour back
        assertEquals("America/Phoenix", zone(33.45, -112.07))      // Arizona keeps standard time
        assertEquals("America/Denver", zone(35.68, -109.05))       // the Navajo Nation does not
        assertEquals("America/Phoenix", zone(35.80, -110.50))      // and the Hopi inside it does again
        assertEquals("Asia/Urumqi", zone(39.47, 75.99))            // Kashgar, as the IANA data has it
    }

    @Test
    fun `the places this face is staged and worn`() {
        assertEquals("America/New_York", zone(40.6413, -73.7781))  // JFK
        assertEquals("Europe/Kyiv", zone(50.45, 30.52))
        assertEquals("Europe/London", zone(51.47, -0.45))          // LHR
    }

    @Test
    fun `open water takes the nautical band`() {
        // 40 W: fifteen-degree bands centred on multiples of 15, so -45 is 3 hours behind UTC —
        // and the POSIX-style name says it the other way round, which is IANA's, not ours.
        assertEquals("Etc/GMT+3", zone(30.0, -40.0))
        assertEquals("Etc/GMT-11", zone(-35.0, 165.0))           // the Tasman Sea, +11
    }

    @Test
    fun `a renamed zone carries the name a dated watch knows`() {
        // Europe/Kyiv is a 2022 spelling; the TicWatch's own clock still says Europe/Kiev.
        val kyiv = grid.names(grid.zoneAt(50.45, 30.52)).toList()
        assertTrue(kyiv.toString(), "Europe/Kiev" in kyiv)

        // A zone created after the oldest supported tzdata gets an equivalent established one.
        val juarez = grid.names(grid.zoneAt(31.69, -106.42)).toList()
        assertEquals("America/Ciudad_Juarez", juarez[0])
        assertTrue(juarez.toString(), juarez.size > 1)
    }

    @Test
    fun `standard offsets are the floor a watch with none of the names falls back to`() {
        assertEquals(-5 * 3600, grid.standardOffsetSeconds(grid.zoneAt(40.64, -73.78)))
        assertEquals(19800, grid.standardOffsetSeconds(grid.zoneAt(19.08, 72.88)))
    }

    @Test
    fun `the poles and the edges of the grid do not throw`() {
        assertNotNull(grid.names(grid.zoneAt(90.0, 0.0)))
        assertNotNull(grid.names(grid.zoneAt(-90.0, 0.0)))
        assertNotNull(grid.names(grid.zoneAt(95.0, 540.0)))
        assertNotNull(grid.names(grid.zoneAt(-95.0, -540.0)))
    }

    @Test
    fun `every cell of the world has a zone`() {
        var lat = -89.975
        while (lat < 90.0) {
            var lon = -179.975
            while (lon < 180.0) {
                val zone = grid.zoneAt(lat, lon)
                assertTrue(zone in 0 until grid.zoneCount)
                lon += 1.0
            }
            lat += 1.0
        }
    }

    @Test
    fun `rejects bytes that are not a grid`() {
        assertNull(TzGrid.fromBytes(ByteArray(8)))
        assertNull(TzGrid.fromBytes(ByteArray(64)))
        val truncated = File("src/main/assets/${TzGrid.ASSET_NAME}").takeIf { it.exists() }
            ?.readBytes()?.copyOf(4096)
        if (truncated != null) assertNull(TzGrid.fromBytes(truncated))
    }
}
