// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The `QFE` figure: steady through the sensor's noise, honest about a real change. */
class PressureHoldTest {

    private val none = SensorSlots.NO_READING

    @Test
    fun `the first reading is shown, rounded to the hectopascal`() {
        assertEquals(10070, PressureHold.next(none, 10074))
        assertEquals(10080, PressureHold.next(none, 10075))
    }

    @Test
    fun `a reading wandering around the half does not flip the last figure`() {
        var shown = PressureHold.next(none, 10074)
        for (raw in intArrayOf(10076, 10073, 10078, 10075, 10079, 10071, 10077)) {
            shown = PressureHold.next(shown, raw)
            assertEquals(10070, shown)
        }
    }

    @Test
    fun `a real change moves the figure, both ways`() {
        assertEquals(10080, PressureHold.next(10070, 10080))
        assertEquals(10060, PressureHold.next(10070, 10060))
        assertEquals(10120, PressureHold.next(10070, 10123))
    }

    @Test
    fun `an implausible event keeps the reading already shown`() {
        assertEquals(10070, PressureHold.next(10070, none))
        assertEquals(none, PressureHold.next(none, none))
    }
}
