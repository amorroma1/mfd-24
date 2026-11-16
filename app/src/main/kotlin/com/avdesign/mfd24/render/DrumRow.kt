// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

/**
 * A row of type whose changed characters turn over on drums, the way a mechanical counter's do:
 * each changed column's old character leaves and its new one arrives, one after another from the
 * left, and everything that did not change stands still.
 *
 * It exists to say *this just changed*. The cached weather goes up the moment the screen wakes and
 * the fresh reading lands a second later; set in one frame, the swap is easy to miss, and a reader
 * who missed it does not know which of the two they are looking at. Hence three rules:
 *
 * - **Only what changed.** The whole row turning over, departure-board style, was tried: on
 *   `QFE 1007` to `QFE 1008` seven drums spun to say one digit moved, and the movement drowned
 *   the difference it was there to point at. A column whose character stayed does not move.
 * - **Only for a change the reader can still connect with.** One met on an always-on frame or on
 *   the first frame after one is set at once — always-on has already shown it — and so is one
 *   met after the face was away for longer than [MAX_AWAY_MILLIS]: a wrist raised on an hour-old
 *   change must not be greeted by a slot machine. A short absence is different: the commonest is
 *   the editor, where the wearer has just switched the position source and comes back to see
 *   what it did. That change turns on the first frame back.
 * - **Both ways, as a number does.** A figure that went up rolls up — the new one from below —
 *   and one that went down rolls down. Read off the whole number, not the digit: 1017 to 1009 is
 *   a fall, so its `1` and its `7` both turn downwards; and `-3` to `-5` is a fall too, though
 *   its digit grew. A clock is the exception, built with [forwardOnly]: 13:59 to 14:00 is time
 *   going on, not minutes falling, so every drum on it turns up.
 * - **No intermediate figures.** 1017 to 1009 does not count down through 1016; it reads well once
 *   and tediously after that.
 *
 * Old and new are lined up by what they share at the front and at the back, and what differs in
 * between is right-aligned, the way numbers are: `9°C` to `10°C` keeps `°C` still and turns the
 * units under the units; `KJFK 9.9KM` to `KJFK 10.0KM` keeps the code and the `KM` still.
 *
 * Pure: no Android, no allocation after construction.
 */
class DrumRow(capacity: Int, private val forwardOnly: Boolean = false) {

    private val shown = CharArray(capacity)
    private var shownLength = 0

    /** Per column: the character it is rolling away from; read while its [progress] is below 1. */
    val fromChars = CharArray(capacity)

    /** Per column: true when it rolls upwards, the new character arriving from below. */
    val upward = BooleanArray(capacity)

    /** Per column: when its roll began, or [STILL]. */
    private val startedAt = LongArray(capacity) { STILL }

    private var lastFrameMillis = 0L
    private var lastAmbient = true
    private var seen = false

    /** True while any drum is still turning, so the renderer knows to ask for fast frames. */
    var rolling: Boolean = false
        private set

    /** Call once per frame with the row as it should now read. */
    fun update(chars: CharArray, length: Int, nowMillis: Long, ambient: Boolean) {
        val n = if (length > shown.size) shown.size else length
        val gap = nowMillis - lastFrameMillis
        val watched = seen && !ambient && !lastAmbient && gap >= 0L && gap <= MAX_AWAY_MILLIS
        seen = true
        lastFrameMillis = nowMillis
        lastAmbient = ambient

        if (!sameAs(chars, n)) {
            align(chars, n)
            // Only the columns that changed turn, each the way its number went, one after
            // another: the first changed column starts at once, wherever it stands in the row.
            var turned = 0
            for (i in 0 until n) {
                val j = oldIndex(i)
                val old = if (j >= 0) shown[j] else ' '
                fromChars[i] = old
                if (old == chars[i] || !watched) {
                    startedAt[i] = STILL
                    continue
                }
                upward[i] = forwardOnly || rises(chars, n, i, old)
                startedAt[i] = nowMillis + turned * STAGGER_MILLIS
                turned++
            }
            for (i in 0 until n) shown[i] = chars[i]
            shownLength = n
        } else if (!watched) {
            // Unchanged, but the screen went away: whatever was mid-turn is finished now.
            for (i in 0 until n) startedAt[i] = STILL
        }

        var any = false
        for (i in 0 until n) {
            if (startedAt[i] != STILL && progress(i, nowMillis) < 1f) any = true
        }
        rolling = any
    }

    /** How far column [i] has turned, `0` to `1`, eased; `1` means at rest. */
    fun progress(i: Int, nowMillis: Long): Float {
        val start = startedAt[i]
        if (start == STILL) return 1f
        return ease(nowMillis - start)
    }

    private fun sameAs(chars: CharArray, n: Int): Boolean {
        if (n != shownLength) return false
        for (i in 0 until n) if (shown[i] != chars[i]) return false
        return true
    }

    /**
     * Whether column [i] went up. Inside a run of figures that the old row also had in the same
     * place, the two numbers decide, each signed by any minus in front of it;
     * anywhere else the two characters themselves do.
     */
    private fun rises(chars: CharArray, n: Int, i: Int, old: Char): Boolean {
        if (chars[i] in '0'..'9') {
            var a = i
            while (a > 0 && isFigure(chars[a - 1])) a--
            var b = i + 1
            while (b < n && isFigure(chars[b])) b++
            // The old number at the same place: its figures ending where the new run ends.
            val last = oldIndex(b - 1)
            val ob = last + 1
            var oa = last
            if (last >= 0 && isFigure(shown[oa])) {
                while (oa > 0 && isFigure(shown[oa - 1])) oa--
                val newValue = valueOf(chars, a, b)
                val oldValue = valueOf(shown, oa, ob)
                if (newValue >= 0L && oldValue >= 0L) {
                    val signedNew = if (a > 0 && chars[a - 1] == '-') -newValue else newValue
                    val signedOld = if (oa > 0 && shown[oa - 1] == '-') -oldValue else oldValue
                    if (signedNew != signedOld) return signedNew > signedOld
                }
            }
        }
        return chars[i] > old
    }

    /**
     * The digits of `[from, until)` read as one number, the decimal point skipped — both rows put
     * it in the same place, so `9.9` against `10.0` compares as 99 against 100. -1 if too long.
     */
    private fun valueOf(src: CharArray, from: Int, until: Int): Long {
        var value = 0L
        var digits = 0
        for (k in from until until) {
            if (src[k] == '.') continue
            if (++digits > DIGITS_MAX) return -1L
            value = value * 10 + (src[k] - '0')
        }
        return value
    }

    private fun isFigure(c: Char): Boolean = c in '0'..'9' || c == '.'

    // The alignment of the change in hand: the shared front, and how far the rest moved.
    private var prefix = 0
    private var shift = 0

    /** The front both rows share stays put; everything after it lines up from the right. */
    private fun align(chars: CharArray, n: Int) {
        val m = shownLength
        var p = 0
        while (p < n && p < m && shown[p] == chars[p]) p++
        // Never stop inside a number whose length changed: `10.0` to `1.2` shares a `1`, but it
        // is tens in one and units in the other, and the number has to line up from the right.
        if (n != m) {
            while (p > 0 && isFigure(chars[p - 1])) p--
        }
        prefix = p
        shift = n - m
    }

    /** Where column [i] of the new row stood in the old one, or -1 if it is new. */
    private fun oldIndex(i: Int): Int {
        if (i < prefix) return i
        val j = i - shift
        return if (j >= prefix && j < shownLength) j else -1
    }

    companion object {
        /**
         * One drum's turn: a mechanical counter's pace. 360 ms was tried first, on the wake
         * sweep's tempo, and on the wrist the change was over before the eye had found the row.
         */
        const val DURATION_MILLIS = 800L

        /** Each changed drum starts this much after the one to its left, like a Solari board. */
        const val STAGGER_MILLIS = 60L

        /**
         * Away from the screen for longer than this and a change is old news, set without a turn.
         * Long enough for a trip into the editor and back; first tried at two and a half seconds,
         * which set every change made in the editor and so hid exactly the ones the wearer asked
         * for.
         */
        const val MAX_AWAY_MILLIS = 90_000L

        private const val STILL = Long.MIN_VALUE

        /** Further ahead than any stagger can put a start: the clock went backwards. */
        private const val STAGGER_LIMIT_MILLIS = 10_000L

        /** Longer digit runs than this are not read as numbers; nothing on these rows comes close. */
        private const val DIGITS_MAX = 9

        /**
         * Before its start a drum has not moved; then it eases in and out, so it neither jerks
         * off the old figure nor slams into the new one. A clock that jumped backwards settles.
         */
        fun ease(elapsed: Long): Float {
            if (elapsed <= 0L) return if (elapsed < -STAGGER_LIMIT_MILLIS) 1f else 0f
            if (elapsed >= DURATION_MILLIS) return 1f
            val t = elapsed / DURATION_MILLIS.toFloat()
            return t * t * (3f - 2f * t)
        }
    }
}
