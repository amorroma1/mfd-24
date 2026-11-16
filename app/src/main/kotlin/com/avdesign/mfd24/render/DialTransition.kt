// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

/**
 * Eases the dial from one frame of reference to another.
 *
 * Cross a time zone and every hour-scale element — the hour hand, both ends of the duty arc, the
 * daylight band — has to move by the same amount at once. Snapping them looks like a glitch; the
 * dial glides to its new setting instead.
 *
 * ### Why there are two offsets
 * Everything is derived from a UTC offset, so the obvious move is to ease that one number and let
 * it carry the whole dial. It does — and it also drags the minute hand through a full revolution
 * per hour of change, because the minute hand is the *fractional* part of the same value. An
 * eight-hour flight span it eight times round, which is nobody's idea of elegant.
 *
 * So the offset is eased twice, at two different moduli. The hour scale takes the whole change,
 * the short way round a day. The minute hand takes only what is left over inside an hour, which is
 * zero for almost every zone on earth and half a turn for the half-hour ones. Seconds are never
 * affected: no zone offset has ever had a fractional minute in it.
 *
 * ### A train of gears, not one picture turned
 * The hands lead, the daylight band follows [BAND_DELAY_MILLIS] behind and the sky marks
 * [SKY_DELAY_MILLIS] behind (see [SkyGlide]), each on the same curve over [STAGE_MILLIS], so the
 * whole re-set still fits [DURATION_MILLIS]. Read on a wrist it is a driven wheel taking up its
 * play. The duty arc is not delayed: it is drawn on the hour hand's own scale, and the rule that
 * the hand never stands inside a booked arc holds only while the two move as one.
 *
 * ### Always-on gets the end, never the middle
 * An always-on frame stands for a minute. Drawn mid-glide it would hold a dial half-way between
 * two settings for that whole minute, so [update] with `settle` lands everything on its target.
 *
 * Holds only primitives and allocates nothing.
 */
class DialTransition {

    /** Offset for the 24-hour scale: hour hand, duty arc, daylight band. */
    var hourOffsetMillis: Long = 0L
        private set

    /** Offset for the minute hand, which only ever differs in half-hour and quarter-hour zones. */
    var minuteOffsetMillis: Long = 0L
        private set

    /** Daylight band start, degrees clockwise from 12 o'clock. */
    var daylightStart: Float = 0f
        private set

    var daylightSweep: Float = 0f
        private set

    val animating: Boolean
        get() = progress < 1f

    private var initialised = false
    private var startedAt = 0L
    private var progress = 1f

    /** The real target, kept only to notice when it changes. */
    private var offsetTarget = 0L

    private var hourFrom = 0L
    private var hourTo = 0L
    private var minuteFrom = 0L
    private var minuteTo = 0L
    private var startFrom = 0f
    private var startTo = 0f
    private var sweepFrom = 0f
    private var sweepTo = 0f

    /**
     * Feeds in what the dial should be showing and returns the eased values through the properties.
     * Call once per frame; a change of target starts a fresh glide from wherever the last one had
     * got to, so a second change mid-flight does not jump.
     */
    fun update(
        nowMillis: Long,
        targetOffsetMillis: Long,
        targetDaylightStart: Float,
        targetDaylightSweep: Float,
        settle: Boolean = false,
    ) {
        if (!initialised) {
            initialised = true
            hourOffsetMillis = targetOffsetMillis
            minuteOffsetMillis = targetOffsetMillis
            daylightStart = targetDaylightStart
            daylightSweep = targetDaylightSweep
            offsetTarget = targetOffsetMillis
            startTo = targetDaylightStart
            sweepTo = targetDaylightSweep
            return
        }

        val changed = targetOffsetMillis != offsetTarget ||
            targetDaylightStart != startTo ||
            targetDaylightSweep != sweepTo
        if (changed) {
            offsetTarget = targetOffsetMillis

            // The hour scale goes the short way round a day. Crossing the date line can move the
            // offset by a full 24 hours while leaving the dial reading the same hour — Apia to Pago
            // Pago is exactly that — and easing the raw difference would spin the hand a whole
            // gratuitous turn. The landing value differs from the real offset by a multiple of a
            // day, which every angle here is taken modulo anyway.
            hourFrom = hourOffsetMillis
            hourTo = hourFrom + shortestDelta(hourFrom, targetOffsetMillis, DAY_MILLIS)

            // The minute hand goes the short way round an hour: nothing at all for a whole-hour
            // zone, half a turn for a half-hour one.
            minuteFrom = minuteOffsetMillis
            minuteTo = minuteFrom + shortestDelta(minuteFrom, targetOffsetMillis, HOUR_MILLIS)

            sweepFrom = daylightSweep
            sweepTo = targetDaylightSweep
            // A band appearing or disappearing grows and shrinks in place; only a band that exists
            // at both ends is worth sliding round the dial.
            startFrom = if (sweepFrom <= 0f || sweepTo <= 0f) targetDaylightStart else daylightStart
            startTo = targetDaylightStart

            startedAt = nowMillis
            progress = 0f
        }

        if (progress >= 1f) return

        val elapsed = if (settle) DURATION_MILLIS else nowMillis - startedAt
        progress = if (elapsed >= DURATION_MILLIS) 1f else elapsed / DURATION_MILLIS.toFloat()
        val hands = stage(elapsed, 0L).toDouble()
        val band = stage(elapsed, BAND_DELAY_MILLIS)

        hourOffsetMillis = hourFrom + Math.round((hourTo - hourFrom) * hands)
        minuteOffsetMillis = minuteFrom + Math.round((minuteTo - minuteFrom) * hands)
        daylightSweep = sweepFrom + (sweepTo - sweepFrom) * band
        daylightStart = normalise(startFrom + shortestAngle(startFrom, startTo) * band)
    }

    /** Signed distance from [from] to [to] taken modulo [period], in `(-period/2, +period/2]`. */
    private fun shortestDelta(from: Long, to: Long, period: Long): Long {
        var delta = (to - from) % period
        if (delta > period / 2) delta -= period
        if (delta <= -period / 2) delta += period
        return delta
    }

    /** Signed angular distance in `(-180, 180]`, so the band never takes the long way round. */
    private fun shortestAngle(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta <= -180f) delta += 360f
        return delta
    }

    private fun normalise(degrees: Float): Float {
        var d = degrees % 360f
        if (d < 0f) d += 360f
        return d
    }

    companion object {
        /**
         * A cubic in and out, so the hands move like something with mass: a slow start that is
         * plainly a start, the pace building to three times the average at the middle, and a
         * braking run onto the new setting. Smoothstep came first and peaks at only one and a half
         * times the average, which on the wrist read as an even slide — the weight was not visible.
         */
        fun ease(t: Float): Float {
            if (t <= 0f) return 0f
            if (t >= 1f) return 1f
            if (t < 0.5f) return 4f * t * t * t
            val u = 2f - 2f * t
            return 1f - u * u * u / 2f
        }

        /**
         * Slow enough to watch. This is a dial re-setting itself, not a chronograph showing off;
         * three seconds reads as deliberate where two read as a twitch. Four came first and, with
         * the stages staggered, dragged; so did pauses of 0.3 s between them, where 0.2 s reads as
         * play in the gears and no more.
         */
        const val DURATION_MILLIS = 3000L

        /** One stage of the train: the hands, the band, the sky, each this long. */
        const val STAGE_MILLIS = 2600L

        /** How far the daylight band runs behind the hands. */
        const val BAND_DELAY_MILLIS = 200L

        /** How far the sky marks run behind the hands; the last stage ends at [DURATION_MILLIS]. */
        const val SKY_DELAY_MILLIS = 400L

        /** How far a stage starting [delayMillis] into the glide has gone at [elapsedMillis]. */
        fun stage(elapsedMillis: Long, delayMillis: Long): Float =
            ease((elapsedMillis - delayMillis) / STAGE_MILLIS.toFloat())

        private const val DAY_MILLIS = 86_400_000L
        private const val HOUR_MILLIS = 3_600_000L
    }
}
