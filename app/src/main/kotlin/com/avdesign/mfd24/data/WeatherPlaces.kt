// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import com.avdesign.mfd24.geo.Geo

/**
 * The last few weather readings, each kept against the place it was asked for.
 *
 * One reading used to be the whole cache, so a hand-typed jump to KJFK and back threw the home
 * reading away twice: the row emptied, and offline it stayed empty, although the watch had held
 * a perfectly good observation of home a minute earlier. Now a reading is reused wherever the
 * wearer is within [RADIUS_METERS] of where it was asked for, for up to [SHOW_MAX_AGE_MILLIS]
 * after the fetch, with no network involved. Beyond either, the row is empty rather than wrong.
 *
 * Pure and Android-free; [encode] and [decode] are what the store persists.
 */
class WeatherPlaces {

    /**
     * One reading. [fetchedAt] is when it was asked for, which is what the cache ages from;
     * [issuedAt] is when the source says it was observed — the METAR's own time, which is what
     * [WeatherSchedule] reads to know when the next one is out.
     */
    class Entry(
        val latitude: Double,
        val longitude: Double,
        val fetchedAt: Long,
        val issuedAt: Long,
        val metar: Boolean,
        val temperatureDeciC: Int,
        val pressureDeciHpa: Int,
        val conditionIndex: Int,
    )

    private val entries = ArrayList<Entry>(CAPACITY + 1)

    val size: Int get() = entries.size

    /** The nearest reading within reach of the position and young enough to show, or null. */
    fun find(latitude: Double, longitude: Double, nowMillis: Long): Entry? {
        var best: Entry? = null
        var bestMeters = Double.MAX_VALUE
        for (entry in entries) {
            // A clock that has gone backwards is not evidence of freshness.
            if (nowMillis - entry.fetchedAt !in 0 until SHOW_MAX_AGE_MILLIS) continue
            val meters = Geo.haversineMeters(entry.latitude, entry.longitude, latitude, longitude)
            if (meters <= RADIUS_METERS && meters < bestMeters) {
                best = entry
                bestMeters = meters
            }
        }
        return best
    }

    /** Keeps [entry], replacing anything within reach of it, newest first, [CAPACITY] at most. */
    fun put(entry: Entry) {
        entries.removeAll {
            Geo.haversineMeters(it.latitude, it.longitude, entry.latitude, entry.longitude) <=
                RADIUS_METERS
        }
        entries.add(0, entry)
        while (entries.size > CAPACITY) entries.removeAt(entries.size - 1)
    }

    fun encode(): String = entries.joinToString(";") {
        listOf(
            it.latitude, it.longitude, it.fetchedAt, it.issuedAt, if (it.metar) 1 else 0,
            it.temperatureDeciC, it.pressureDeciHpa, it.conditionIndex,
        ).joinToString(",")
    }

    companion object {
        /** Enough for home, work and a few hand-typed places, and small enough to scan per frame. */
        const val CAPACITY = 8

        /** How far a reading reaches: the wearer's own five-kilometre square, as asked. */
        const val RADIUS_METERS = 5_000.0

        /**
         * How long a reading may be shown. A METAR is an hourly product, so an hour is one issue;
         * past it the row says nothing rather than something an hour gone.
         */
        const val SHOW_MAX_AGE_MILLIS = 60 * 60_000L

        /** Reads what [encode] wrote; anything malformed is dropped, never thrown. */
        fun decode(text: String?): WeatherPlaces {
            val places = WeatherPlaces()
            if (text.isNullOrEmpty()) return places
            for (record in text.split(';').asReversed()) {
                val f = record.split(',')
                if (f.size != 8) continue
                val entry = runCatching {
                    Entry(
                        f[0].toDouble(), f[1].toDouble(), f[2].toLong(), f[3].toLong(),
                        f[4] == "1", f[5].toInt(), f[6].toInt(), f[7].toInt(),
                    )
                }.getOrNull() ?: continue
                places.put(entry)
            }
            return places
        }
    }
}
