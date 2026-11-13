// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import com.avdesign.mfd24.geo.PoiFormat

/**
 * Which pictogram a site gets, from its type and its flags.
 *
 * Split out of [TelemetryLayer] and kept Android-free so the choice can be unit-tested. It is not
 * arithmetic, but it is a priority order, and a priority order with four inputs is exactly the kind
 * of thing that is quietly wrong for a year: a military helipad has both flags set, and which of
 * the two wins is a decision, not an accident.
 */
object SiteGlyph {

    const val AIRLINER: Int = 0
    const val FIGHTER: Int = 1
    const val HELICOPTER: Int = 2
    const val ANCHOR: Int = 3
    const val ROCKET: Int = 4

    /**
     * Whether a site should be drawn in the accent colour rather than the primary one.
     *
     * Ownership is orthogonal to what kind of place somewhere is, so it is carried by colour
     * rather than by shape. At the 22 px the glyph box allows, a silhouette can just about say
     * "anchor" or "rotorcraft"; asking it to also say "military" was costing more than it
     * bought, and a second ship and a second helicopter were cut once the watch had shown both
     * pairs side by side. Only the airfield still has two shapes, and even there the colour is
     * what reads first.
     */
    fun isMilitary(flags: Int): Boolean = flags and PoiFormat.FLAG_MILITARY != 0

    /**
     * @param type one of `PoiFormat.TYPE_*`
     * @param flags bitfield of `PoiFormat.FLAG_*`
     */
    fun forSite(type: Int, flags: Int): Int {
        val military = flags and PoiFormat.FLAG_MILITARY != 0
        return when (type) {
            // A launch site stays a launch vehicle whoever owns the range: the rocket already says
            // the specific thing, and no second silhouette would say more.
            PoiFormat.TYPE_SPACEPORT -> ROCKET

            // A harbour is an anchor whoever runs it; the accent colour says naval.
            PoiFormat.TYPE_PORT -> ANCHOR

            // Rotorcraft-only decides the family, and ownership is colour. "You cannot land a
            // fixed wing here" is the more useful fact about a landing site, so a military
            // helipad is a helicopter in the accent and never a fast jet.
            else -> when {
                flags and PoiFormat.FLAG_HELIPAD != 0 -> HELICOPTER

                military -> FIGHTER
                else -> AIRLINER
            }
        }
    }
}
