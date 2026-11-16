// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

/**
 * What the `QFE` slot shows, given what the barometer just said.
 *
 * The slot shows whole hectopascals, and the barometer is finer and noisier than that: lifting the
 * wrist a metre is a tenth of a hectopascal, and a reading that sits near a half flips the last
 * figure back and forth, `1007`, `1008`, `1007`, several times a minute — with `ROLLING DIGITS` on,
 * a drum that never rests. A last figure that moves by one says nothing the sensor's own error
 * does not, so the shown figure moves only once the reading is [MARGIN_TENTHS] past the half-way
 * point to the next one. The cost is a lag of under a hectopascal, which no reading of station
 * pressure on a wrist can tell from noise.
 *
 * And a reading the sensor cannot mean does not throw away one it did. Every time the slot starts
 * again — a trip into the editor and back — the first event can carry the register's power-on
 * value (2048 hPa on this watch); taken at its word it put dashes up for a moment and then turned
 * every drum of the slot over to arrive at the same figure as before.
 *
 * Pure; values in tenths of a hectopascal, whole hectopascals out (still in tenths).
 */
object PressureHold {

    /** How far past the half-way point a reading must go before the shown figure follows: 0.4 hPa. */
    const val MARGIN_TENTHS = 4

    /**
     * The figure to show after [rawTenths], given [shownTenths] (a whole hectopascal in tenths, or
     * [SensorSlots.NO_READING]). [rawTenths] is [SensorSlots.NO_READING] for an implausible event.
     */
    fun next(shownTenths: Int, rawTenths: Int): Int {
        if (rawTenths == SensorSlots.NO_READING) return shownTenths
        val whole = roundToWhole(rawTenths)
        if (shownTenths == SensorSlots.NO_READING) return whole
        val distance = rawTenths - shownTenths
        return if (distance > 5 + MARGIN_TENTHS || distance < -5 - MARGIN_TENTHS) whole else shownTenths
    }

    private fun roundToWhole(tenths: Int): Int = (tenths + 5) / 10 * 10
}
