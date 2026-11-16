// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

/**
 * Carries a sky mark — the sun's or the moon's — from one place's sky to another's when the
 * position jumps, instead of letting it teleport while the hands glide.
 *
 * The mark sits at its body's hour angle, and an hour angle is known whether the body is up or
 * not; moving changes it by the difference in longitude and nothing else. So the glide is done in
 * hour angle, the short way, which is the way the wearer moved: east and the mark goes forwards,
 * west and it goes back. The horizon is eased beside it — half the body's day arc, which changes
 * with latitude — and the mark is drawn while it stands inside it. That is what makes a sun that
 * had not yet risen roll up out of the night on the sunrise side when the wearer jumps east, and
 * one that had set come back over the sunset side when they jump west.
 *
 * What is not a jump: time passing. Between two frames an hour angle advances at its body's own
 * rate, however long the screen was off, and that is subtracted before deciding; a zone change
 * moves no hour angle at all, which is why a mark stays put while the hands re-set past it.
 *
 * Where the body culminates on the other side of the wearer the mark runs against the hands, and
 * a jump across that line would carry it half-way round the dial in the wrong direction. That one
 * case is not glided: the mark is set in its new place and brought up by brightness instead.
 *
 * Pure: no Android, no allocation.
 */
class SkyGlide(private val degreesPerHour: Double) {

    /** The hour angle to draw, degrees in `(-180, 180]`. */
    var hourAngle = 0.0
        private set

    /** How bright to draw the mark, `0` (not drawn) to `1`. */
    var level = 0f
        private set

    /** True while gliding or fading, so the renderer asks for fast frames. */
    val animating: Boolean
        get() = progress < 1f || level != levelTarget

    private var horizon = 0.0
    private var seen = false
    private var lastFrame = 0L
    private var lastTargetAngle = 0.0
    private var lastTargetHorizon = 0.0
    private var lastMirror = false
    private var angleOffset = 0.0
    private var horizonOffset = 0.0
    private var startedAt = 0L
    private var progress = 1f
    private var levelTarget = 0f

    /**
     * Once per frame, with where the body really is: [targetAngle] its hour angle, [targetHorizon]
     * half its day arc (0 never rises, 180 never sets), [mirror] whether it culminates on the far
     * side, and [up] whether it is above the horizon — the rule at rest.
     */
    fun update(
        nowMillis: Long,
        targetAngle: Double,
        targetHorizon: Double,
        mirror: Boolean,
        up: Boolean,
        settle: Boolean = false,
    ) {
        val elapsed = nowMillis - lastFrame
        if (!seen || elapsed < 0L) {
            settle(nowMillis, targetAngle, targetHorizon, mirror, up)
            return
        }
        val expected = degreesPerHour * elapsed / 3_600_000.0
        val jump = wrap(targetAngle - lastTargetAngle - expected)
        val widened = targetHorizon - lastTargetHorizon
        lastFrame = nowMillis
        lastTargetAngle = targetAngle
        lastTargetHorizon = targetHorizon

        if (mirror != lastMirror) {
            // Across the line it would sweep the wrong way: set it there and bring it up.
            lastMirror = mirror
            angleOffset = 0.0
            horizonOffset = 0.0
            level = 0f
            startedAt = nowMillis
            progress = 0f
        } else if (Math.abs(jump) > JUMP_DEGREES || Math.abs(widened) > JUMP_DEGREES) {
            // From wherever the mark is drawn now, mid-glide or not, so a second jump does not snap.
            angleOffset = wrap(hourAngle + expected - targetAngle)
            horizonOffset = horizon - targetHorizon
            startedAt = nowMillis
            progress = 0f
        }

        // The last wheel of the train: it starts after the hands and the band, and ends with the
        // glide. Always-on gets the end at once, never a mark stood mid-sky for a minute.
        val t = if (settle || progress >= 1f) DialTransition.DURATION_MILLIS else nowMillis - startedAt
        progress = if (t >= DialTransition.DURATION_MILLIS) {
            1f
        } else {
            t / DialTransition.DURATION_MILLIS.toFloat()
        }
        val remaining = 1.0 - DialTransition.stage(t, DialTransition.SKY_DELAY_MILLIS)
        hourAngle = wrap(targetAngle + angleOffset * remaining)
        horizon = targetHorizon + horizonOffset * remaining
        levelTarget = if (progress < 1f) {
            if (Math.abs(hourAngle) < horizon) 1f else 0f
        } else {
            if (up) 1f else 0f
        }
        val step = if (settle) 1f else elapsed / FADE_MILLIS.toFloat()
        level = if (level < levelTarget) {
            minOf(levelTarget, level + step)
        } else {
            maxOf(levelTarget, level - step)
        }
    }

    /** Nothing to show: the next mark appears where it is, with no glide from a stale place. */
    fun reset() {
        seen = false
        level = 0f
        levelTarget = 0f
        progress = 1f
    }

    private fun settle(now: Long, angle: Double, horizon: Double, mirror: Boolean, up: Boolean) {
        seen = true
        lastFrame = now
        lastTargetAngle = angle
        lastTargetHorizon = horizon
        lastMirror = mirror
        angleOffset = 0.0
        horizonOffset = 0.0
        progress = 1f
        hourAngle = wrap(angle)
        this.horizon = horizon
        level = if (up) 1f else 0f
        levelTarget = level
    }

    companion object {
        /**
         * A jump of more than this between frames, beyond the body's own motion, is a move: four
         * minutes of hour angle, or seventy to a hundred kilometres of longitude. Anything less is
         * a drive, and a mark nudged that little does not need carrying.
         */
        const val JUMP_DEGREES = 1.0

        /** A mark crossing the horizon fades rather than blinking in or out. */
        const val FADE_MILLIS = 400L

        /** The sun's hour angle advances 15 degrees an hour; the moon's a little less. */
        const val SUN_DEGREES_PER_HOUR = 15.0
        const val MOON_DEGREES_PER_HOUR = 14.49

        /** Half the day arc of a body at [declinationDeg] seen from [latitudeDeg], in degrees. */
        fun horizonOf(latitudeDeg: Double, declinationDeg: Double): Double {
            val c = -Math.tan(Math.toRadians(latitudeDeg)) * Math.tan(Math.toRadians(declinationDeg))
            if (c <= -1.0) return 180.0
            if (c >= 1.0) return 0.0
            return Math.toDegrees(Math.acos(c))
        }

        fun wrap(degrees: Double): Double {
            var d = degrees % 360.0
            if (d > 180.0) d -= 360.0
            if (d <= -180.0) d += 360.0
            return d
        }
    }
}
