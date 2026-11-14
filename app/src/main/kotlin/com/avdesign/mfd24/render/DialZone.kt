// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import com.avdesign.mfd24.data.TelemetryState

/**
 * Which clock the hour scale runs on, and whether the sky can be drawn on it.
 *
 * The dial carries two kinds of mark that agree only when the clock is the place's own. The hour
 * hand and the daylight band are clock-time: the band runs from sunrise to sunset *as the dial's
 * zone reads them*, so the hand inside it means day. The sun and moon marks are sky-time: each
 * sits at its body's hour angle, `12 h + HA`, which is what makes the dial a compass — turn the
 * mark to the real sun and noon faces the equator. Between a place and its own zone the two
 * differ by the equation of time and the zone's lean against the meridian, an hour at most, and
 * that difference is the compass's whole accuracy. Between a place and someone else's zone they
 * differ by the gap between the zones: a hand-typed JFK on a watch kept in Kyiv put the sun seven
 * hours outside its own daylight band, true by each measure and absurd on one ring — and the
 * flight film had shown the same thing over the Atlantic, explained rather than fixed.
 *
 * So the dial's clock is the place's whenever the wearer has said the dial is *there*:
 *
 * - **A typed position always brings its clock with it.** Everything else on the dial — site,
 *   weather, band, sun and moon — already followed it; the hour scale now does too.
 * - **A measured position brings its clock when `LOCAL TIME` is set to the position.** The dial
 *   then crosses zones under the wearer, half-hour zones included, with a border band against a
 *   wandering fix (see `ZoneHysteresis`) and no delay beyond it: arriving in St John's shows St
 *   John's time as soon as the fix does.
 * - **Otherwise the device's zone rules**, the phone's or one the wearer set by hand, UTC on a
 *   pilot's wrist included. The face does not overrule it — but a compass drawn against another
 *   place's clock points somewhere false, so when the device's offset differs from the place's
 *   the sky marks leave the dial, as they already do with no position and no band. Offsets, not
 *   zone names: Kiev and Kyiv are one clock.
 *
 * Whatever the rule, the change glides the scale through the same transition as a border does.
 */
object DialZone {

    /** The UTC offset the hour scale should be set to at [epochMillis]. */
    fun dialOffsetMillis(deviceOffsetMillis: Long, state: TelemetryState, epochMillis: Long): Long =
        if (state.dialFollowsPlace && state.placeZoneValid) {
            state.placeOffsetAt(epochMillis)
        } else {
            deviceOffsetMillis
        }

    /**
     * Whether the sky marks may be drawn on a scale set to [dialOffsetMillis]. With no zone for the
     * place there is nothing to disagree with, and the marks keep the behaviour they always had.
     */
    fun skyAgrees(dialOffsetMillis: Long, state: TelemetryState, epochMillis: Long): Boolean =
        !state.placeZoneValid || state.placeOffsetAt(epochMillis) == dialOffsetMillis
}
