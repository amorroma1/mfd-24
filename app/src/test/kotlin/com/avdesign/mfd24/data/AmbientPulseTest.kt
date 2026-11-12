// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pulse slot's always-on window: when a minute opens one, and when a reading comes off.
 *
 * Both are arithmetic on instants and three booleans, and both have a failure that is slow to see
 * on a wrist and costly when seen: a window that opens on every minute regardless lights the LED
 * against a table all night, and a reading that never expires is the frozen figure this
 * arrangement replaced.
 */
class AmbientPulseTest {

    private val now = 1_000_000L

    @Test
    fun `a minute with no reading on the dial opens a window`() {
        assertTrue(
            SensorSlots.windowDue(now, SensorSlots.NEVER, onBody = true, interactive = false, windowOpen = false),
        )
    }

    @Test
    fun `a reading from the last half minute is not replaced`() {
        // The wrist came down seconds after the screen filled the slot: nothing to gain.
        assertFalse(
            SensorSlots.windowDue(now, now - 10_000L, onBody = true, interactive = false, windowOpen = false),
        )
        // The next minute's tick is always past the threshold.
        assertTrue(
            SensorSlots.windowDue(now, now - 60_000L, onBody = true, interactive = false, windowOpen = false),
        )
    }

    @Test
    fun `no window off the wrist, with the screen on, or over an open one`() {
        assertFalse(
            SensorSlots.windowDue(now, SensorSlots.NEVER, onBody = false, interactive = false, windowOpen = false),
        )
        assertFalse(
            SensorSlots.windowDue(now, SensorSlots.NEVER, onBody = true, interactive = true, windowOpen = false),
        )
        assertFalse(
            SensorSlots.windowDue(now, SensorSlots.NEVER, onBody = true, interactive = false, windowOpen = true),
        )
    }

    @Test
    fun `three empty windows slow the cadence to a quarter of an hour, and a wrist resets it`() {
        val misses = SensorSlots.BACKOFF_AFTER_MISSES
        // Under the threshold the minute cadence stands, whatever the last window's age.
        assertTrue(
            SensorSlots.windowDue(
                now, SensorSlots.NEVER, onBody = true, interactive = false, windowOpen = false,
                missedWindows = misses - 1, lastWindowElapsed = now - 60_000L,
            ),
        )
        // At the threshold a window a minute ago is too soon; one a quarter of an hour ago is due.
        assertFalse(
            SensorSlots.windowDue(
                now, SensorSlots.NEVER, onBody = true, interactive = false, windowOpen = false,
                missedWindows = misses, lastWindowElapsed = now - 60_000L,
            ),
        )
        assertTrue(
            SensorSlots.windowDue(
                now, SensorSlots.NEVER, onBody = true, interactive = false, windowOpen = false,
                missedWindows = misses, lastWindowElapsed = now - SensorSlots.BACKOFF_MILLIS,
            ),
        )
        // The night of 2026-11-12: 600 windows on a table that the detector called a wrist.
        // Forty-odd is the bill with the back-off, and it never needs the detector.
        val night = 10 * 60 * 60_000L
        var opened = 0
        var missed = 0
        var last = SensorSlots.NEVER
        var t = now
        while (t < now + night) {
            if (SensorSlots.windowDue(t, SensorSlots.NEVER, true, false, false, missed, last)) {
                opened++
                missed++
                last = t
            }
            t += 60_000L
        }
        assertTrue("$opened windows in a night", opened in 40..44)
        // The back-off is a cadence, not a lockout: the count is what a wrist resets.
        assertTrue(
            SensorSlots.windowDue(
                now, SensorSlots.NEVER, onBody = true, interactive = false, windowOpen = false,
                missedWindows = 0, lastWindowElapsed = now - 60_000L,
            ),
        )
    }

    @Test
    fun `a reading stands for three windows and then comes off`() {
        assertFalse(SensorSlots.stale(now, now - SensorSlots.STALE_MILLIS))
        assertTrue(SensorSlots.stale(now, now - SensorSlots.STALE_MILLIS - 1L))
        // No reading cannot be stale: there is nothing to take off.
        assertFalse(SensorSlots.stale(now, SensorSlots.NEVER))
    }

    @Test
    fun `the window closes before the wake lock would, and a reading outlives a missed window`() {
        // A window as long as its lock is a window whose last reading arrives with the lock gone;
        // an expiry shorter than a window would take a reading off while its replacement is
        // still being waited for.
        assertTrue(SensorSlots.WINDOW_MILLIS < 30_000L)
        assertTrue(SensorSlots.STALE_MILLIS >= 3 * 60_000L)
        assertTrue(SensorSlots.FRESH_MILLIS < 60_000L)
    }
}
