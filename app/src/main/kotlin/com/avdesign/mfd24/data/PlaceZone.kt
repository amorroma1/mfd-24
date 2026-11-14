// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The clock of the place the dial is set to, as far as the platform can keep it.
 *
 * Two jobs, both done off the render thread so that `render()` can stay allocation-free:
 *
 * - [choose] turns the grid's list of names into a [ZoneId] this device actually knows. The grid
 *   is cut from current IANA data and a watch's tzdata can be years older — `Europe/Kyiv` is a
 *   2022 name and `America/Ciudad_Juarez` a 2022 zone — so the list carries older and equivalent
 *   names after the newest, and the build-time standard offset as the floor. A fixed offset loses
 *   summer time, which is why it is the last thing tried rather than the first.
 * - [window] reduces the zone to three numbers the renderer can read with no `Instant` and no
 *   rules lookup: the offset now, the next instant it changes, and the offset after that. The
 *   repository refreshes them every half hour, far inside the months between transitions, so a
 *   summer-time change lands on the second without anything having to run at that second.
 */
object PlaceZone {

    /** The first of [names] the device knows, else a fixed zone at [standardOffsetSeconds]. */
    fun choose(names: Array<String>, standardOffsetSeconds: Int, known: (String) -> Boolean): ZoneId {
        for (name in names) {
            if (known(name)) {
                // A known id can still be refused by an odd provider; a refusal is the same as unknown.
                runCatching { return ZoneId.of(name) }
            }
        }
        return ZoneOffset.ofTotalSeconds(standardOffsetSeconds)
    }

    /**
     * The zone's offset at [nowMillis], the next transition after it and the offset that follows.
     * A zone with no transition ahead — a fixed offset, or a country that has abolished summer
     * time — reports [NO_TRANSITION] and the same offset twice.
     */
    fun window(zone: ZoneId, nowMillis: Long, out: Window) {
        val rules = zone.rules
        val now = Instant.ofEpochMilli(nowMillis)
        out.offsetMillis = rules.getOffset(now).totalSeconds * 1000L
        val next = rules.nextTransition(now)
        if (next == null) {
            out.transitionMillis = NO_TRANSITION
            out.offsetAfterMillis = out.offsetMillis
        } else {
            out.transitionMillis = next.toEpochSecond() * 1000L
            out.offsetAfterMillis = next.offsetAfter.totalSeconds * 1000L
        }
    }

    class Window {
        var offsetMillis: Long = 0L
        var transitionMillis: Long = NO_TRANSITION
        var offsetAfterMillis: Long = 0L
    }

    const val NO_TRANSITION: Long = Long.MAX_VALUE
}
