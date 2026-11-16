// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The weather kept per place: which reading the dial may show where, and for how long. The case
 * that started it — KJFK's METAR standing over Kyiv with the watch offline — is the first test.
 */
class WeatherPlacesTest {

    private val now = 1_800_000_000_000L
    private val kyivLat = 50.45
    private val kyivLon = 30.52
    private val jfkLat = 40.64
    private val jfkLon = -73.78

    private fun entry(
        lat: Double = kyivLat,
        lon: Double = kyivLon,
        fetchedAt: Long = now - 60_000L,
        temp: Int = 50,
    ) = WeatherPlaces.Entry(lat, lon, fetchedAt, fetchedAt, false, temp, 10_160, 0)

    @Test
    fun `another place's reading is never shown here`() {
        val places = WeatherPlaces().apply { put(entry(jfkLat, jfkLon)) }
        assertNull(places.find(kyivLat, kyivLon, now))
    }

    @Test
    fun `each place keeps its own reading across a jump and back`() {
        val home = entry(temp = 50)
        val jfk = entry(jfkLat, jfkLon, temp = 120)
        val places = WeatherPlaces().apply {
            put(home)
            put(jfk)
        }
        assertSame(jfk, places.find(jfkLat, jfkLon, now))
        assertSame(home, places.find(kyivLat, kyivLon, now))
    }

    @Test
    fun `a reading reaches five kilometres and no further`() {
        val places = WeatherPlaces().apply { put(entry()) }
        // A hundredth of a degree of latitude is 1.11 km.
        assertNotNull(places.find(kyivLat + 0.044, kyivLon, now))
        assertNull(places.find(kyivLat + 0.046, kyivLon, now))
    }

    @Test
    fun `the reach is a distance not a coordinate delta`() {
        // A degree of longitude is 19 km at 80 degrees north and 111 km at the equator.
        val north = WeatherPlaces().apply { put(entry(80.0, 15.0)) }
        assertNotNull(north.find(80.0, 15.2, now))
        val equator = WeatherPlaces().apply { put(entry(0.0, 15.0)) }
        assertNull(equator.find(0.0, 15.2, now))
    }

    @Test
    fun `a reading is shown for an hour after the fetch and not after`() {
        val limit = WeatherPlaces.SHOW_MAX_AGE_MILLIS
        val places = WeatherPlaces().apply { put(entry(fetchedAt = now - limit + 1)) }
        assertNotNull(places.find(kyivLat, kyivLon, now))
        assertNull(places.find(kyivLat, kyivLon, now + 1))
    }

    @Test
    fun `a clock that jumped backwards is not treated as fresh`() {
        val places = WeatherPlaces().apply { put(entry(fetchedAt = now + 60_000L)) }
        assertNull(places.find(kyivLat, kyivLon, now))
    }

    @Test
    fun `a new reading replaces the old one of the same place`() {
        val places = WeatherPlaces().apply {
            put(entry(temp = 50))
            put(entry(kyivLat + 0.01, kyivLon, temp = 70))
        }
        assertEquals(1, places.size)
        assertEquals(70, places.find(kyivLat, kyivLon, now)!!.temperatureDeciC)
    }

    @Test
    fun `the oldest place goes when the cache is full`() {
        val places = WeatherPlaces()
        for (i in 0..WeatherPlaces.CAPACITY) places.put(entry(lat = i.toDouble(), lon = 0.0))
        assertEquals(WeatherPlaces.CAPACITY, places.size)
        assertNull(places.find(0.0, 0.0, now))
        assertNotNull(places.find(WeatherPlaces.CAPACITY.toDouble(), 0.0, now))
    }

    @Test
    fun `what is stored reads back the same, newest first`() {
        val places = WeatherPlaces().apply {
            put(entry(temp = 50))
            put(WeatherPlaces.Entry(jfkLat, jfkLon, now - 1_000L, now - 600_000L, true, -15, 0, 3))
        }
        val back = WeatherPlaces.decode(places.encode())
        assertEquals(places.encode(), back.encode())
        val jfk = back.find(jfkLat, jfkLon, now)!!
        assertEquals(true, jfk.metar)
        assertEquals(now - 600_000L, jfk.issuedAt)
        assertEquals(-15, jfk.temperatureDeciC)
    }

    @Test
    fun `a damaged record is dropped, never thrown`() {
        val good = WeatherPlaces().apply { put(entry()) }.encode()
        val back = WeatherPlaces.decode("garbage;1,2,3;$good;")
        assertEquals(1, back.size)
        assertEquals(0, WeatherPlaces.decode(null).size)
        assertEquals(0, WeatherPlaces.decode("").size)
    }
}
