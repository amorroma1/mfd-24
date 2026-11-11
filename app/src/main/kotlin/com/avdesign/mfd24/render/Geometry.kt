// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every coordinate the renderer needs, expressed in pixels for the current surface.
 *
 * [rebuild] is the only place in the drawing path that allocates, and it runs only when the surface
 * bounds actually change. Everything the per-frame code touches is a preallocated array or path.
 */
class Geometry {

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    var cx: Float = 0f
        private set
    var cy: Float = 0f
        private set

    /** Radius of the dial, i.e. half the shorter side. */
    var r: Float = 0f
        private set

    /** True when the 24-hour axis is flipped so midnight sits at the top. */
    var midnightUp: Boolean = false
        private set

    // --- 24-hour scale ---------------------------------------------------------------------

    /** 24 hour ticks as x0,y0,x1,y1 quads for a single `Canvas.drawLines` call. */
    val hourTicks: FloatArray = FloatArray(HOUR_TICKS * 4)

    /** The four cardinal ticks (00/06/12/18) are drawn separately, heavier. */
    val cardinalTicks: FloatArray = FloatArray(4 * 4)

    /**
     * The fine ring: 60 divisions, one per minute of the minute hand's revolution and one per
     * second of the seconds cursor's. Split in two so the ring can be graduated — every fifth
     * division is drawn heavier, which is what lets the eye land on a five without counting.
     */
    val minuteTicks: FloatArray = FloatArray(MINUTE_MINOR_TICKS * 4)
    val minuteFiveTicks: FloatArray = FloatArray(MINUTE_MAJOR_TICKS * 4)

    /** `"00".."23"`, preformatted so the render loop never converts a number to text. */
    val hourLabels: Array<CharArray> = Array(HOUR_TICKS) { CharArray(2) }
    val hourLabelX: FloatArray = FloatArray(HOUR_TICKS)
    val hourLabelY: FloatArray = FloatArray(HOUR_TICKS)

    // The dial carries no NOON / MORN / EVE / MIDNIGHT wording: the heavy cardinal ticks and the
    // numerals already say it, and four more words only crowded the face.

    // --- Seconds marker --------------------------------------------------------------------

    /** Radius of the marker's outer edge, just clear of the tick tips. */
    var secondsMarkerBaseRadius: Float = 0f
        private set

    /**
     * Half the marker's base, a little wider than the gap between two minute ticks so it reads as a
     * cursor sitting over the scale rather than as one more tick.
     */
    var secondsMarkerHalfBase: Float = 0f
        private set

    // --- Watch (shift) arc -----------------------------------------------------------------

    /** Oval the duty arc rides on; it doubles as the dial's inner decorative ring. */
    val dutyArcTrack: RectF = RectF()

    // --- Daylight band ---------------------------------------------------------------------

    /**
     * Oval the daylight band is stroked along, filling the whole hour-tick band so the lit hours
     * read as a shaded sector of the 24-hour scale rather than as one more ring.
     */
    val daylightTrack: RectF = RectF()
    var daylightWidth: Float = 0f
        private set

    // --- Hands -----------------------------------------------------------------------------

    /**
     * Hand silhouettes, built pointing straight up from the hub; the renderer rotates the canvas
     * around it. These are contours, not solids — the readout sits under the hands, and a solid
     * hand crossing it takes a line of type with it. Drawn through [hourHandRing], never stroked
     * directly; see there for why.
     */
    val hourHand: Path = Path()
    val minuteHand: Path = Path()

    /**
     * The parts of each hand that stay solid: the point, so it lands unambiguously on a mark, and
     * the counterweight, so the hand still reads as a hand rather than as a wire.
     */
    val hourHandSolid: Path = Path()
    val minuteHandSolid: Path = Path()
    val hubRadius: Float
        get() = r * HUB_RADIUS

    /** Centre-line of the duty arc, where its incident marks have to cross it. */
    val dutyArcRadius: Float
        get() = r * DUTY_ARC_RADIUS

    /**
     * The contour of each hand as a *filled* shape: [hourHand] stroked once, here, and kept as
     * the fill that stroke covers. [Paint.getFillPath] does the stroking, once per layout, so
     * the interactive sprite in [HandsLayer] and the ambient contour are fills of one definition
     * rather than a stroke whose weight is set again at every draw. Note that this alone bought
     * nothing on the watch: a fill of this many verbs is still a software mask to Skia, which is
     * why the interactive hands are sprites. The reasoning is kept with [HandsLayer].
     */
    val hourHandRing: Path = Path()
    val minuteHandRing: Path = Path()

    /** The same contours at ambient weight, where filled hands would burn the panel. */
    val hourHandAmbientRing: Path = Path()
    val minuteHandAmbientRing: Path = Path()

    /** Strokes a silhouette into its ring; declared above the init that first uses it. */
    private val contourPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // --- Telemetry readout -----------------------------------------------------------------

    /**
     * Baselines of the four readout lines — duty and ZULU above the hub, the two data rows below —
     * and the one size every row on the dial is set in, because with no window and no frame the
     * type itself has to carry the structure. See [TEXT_SIZE] and [LINE_PITCH].
     */
    val telemetryLineY: FloatArray = FloatArray(4)
    var telemetryTextSize: Float = 0f
        private set

    /** Baseline of the battery row, one pitch under the readout block. */
    var batteryLineY: Float = 0f
        private set

    /** Status line one pitch over the readout, drawn only when the vigilance monitor has something to say. */
    var statusLineY: Float = 0f

    /** The two optional sensor readouts either side of the hub, on the hub's own baseline. */
    var sensorLineY: Float = 0f
    var sensorOffsetX: Float = 0f
    var sensorGlyphSize: Float = 0f
    var sensorGlyphGap: Float = 0f
        private set

    /**
     * Reference-frame symbol box, sitting on the first data line the way the site pictogram sits on
     * the second. Anchored with its left edge at [cx]; the renderer translates it into place.
     */
    val symbolBox: RectF = RectF()

    /** Site glyph box, likewise anchored at [cx]. */
    val glyphBox: RectF = RectF()

    var glyphGap: Float = 0f
        private set

    /** Boxes the spoken accessibility labels point at: the readout block, and the status line. */
    val a11yReadoutBounds: Rect = Rect()
    val a11yStatusBounds: Rect = Rect()

    // --- Rings -----------------------------------------------------------------------------

    val outerRing: RectF = RectF()

    var hourTickWidth: Float = 0f
        private set
    var cardinalTickWidth: Float = 0f
        private set
    var minuteTickWidth: Float = 0f
        private set
    var minuteFiveTickWidth: Float = 0f
        private set
    var hourLabelSize: Float = 0f
        private set
    var ringWidth: Float = 0f
        private set

    fun matches(bounds: Rect, midnightUp: Boolean): Boolean =
        bounds.width() == width && bounds.height() == height && midnightUp == this.midnightUp

    fun rebuild(bounds: Rect, midnightUp: Boolean) {
        this.midnightUp = midnightUp
        width = bounds.width()
        height = bounds.height()
        cx = bounds.exactCenterX()
        cy = bounds.exactCenterY()
        r = (if (width < height) width else height) / 2f

        hourTickWidth = r * 0.020f
        cardinalTickWidth = r * 0.032f
        minuteTickWidth = r * 0.008f
        minuteFiveTickWidth = r * 0.021f
        hourLabelSize = r * HOUR_LABEL_SIZE
        ringWidth = r * 0.008f

        setRadius(outerRing, RING_RADIUS)
        setRadius(dutyArcTrack, DUTY_ARC_RADIUS)
        setRadius(daylightTrack, (TICK_HOUR_INNER + TICK_CARDINAL_OUTER) / 2f)
        daylightWidth = r * (TICK_CARDINAL_OUTER - TICK_HOUR_INNER)
        secondsMarkerBaseRadius = r * SECONDS_MARKER_BASE
        // Pitch between two minute ticks, measured along the arc the cursor's base sits on.
        val pitch = 2.0 * Math.PI * secondsMarkerBaseRadius / MINUTE_TICKS
        secondsMarkerHalfBase = (pitch * SECONDS_MARKER_PITCH_RATIO / 2.0).toFloat()

        buildScale()
        buildHands()
        buildTelemetry()
    }

    private fun setRadius(oval: RectF, fraction: Float) {
        val radius = r * fraction
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    private fun buildScale() {
        var cardinalIndex = 0
        for (h in 0 until HOUR_TICKS) {
            val angle = hourAngleRad(h.toDouble(), midnightUp)
            val sinA = sin(angle).toFloat()
            val cosA = cos(angle).toFloat()

            // Hour ticks all spring from the inner circle and grow outwards; the cardinals simply
            // reach further. They stop well short of the minute ring, because 24 hours and 60
            // minutes do not divide into each other and interleaving the two scales in one band
            // just produces noise.
            val isCardinal = h % 6 == 0
            val outer = if (isCardinal) TICK_CARDINAL_OUTER else TICK_HOUR_OUTER
            val x0 = cx + sinA * r * outer
            val y0 = cy - cosA * r * outer
            val x1 = cx + sinA * r * TICK_HOUR_INNER
            val y1 = cy - cosA * r * TICK_HOUR_INNER

            val base = h * 4
            hourTicks[base] = x0
            hourTicks[base + 1] = y0
            hourTicks[base + 2] = x1
            hourTicks[base + 3] = y1

            if (isCardinal) {
                val cb = cardinalIndex * 4
                cardinalTicks[cb] = x0
                cardinalTicks[cb + 1] = y0
                cardinalTicks[cb + 2] = x1
                cardinalTicks[cb + 3] = y1
                cardinalIndex++
            }

            hourLabels[h][0] = ('0' + h / 10)
            hourLabels[h][1] = ('0' + h % 10)
            hourLabelX[h] = cx + sinA * r * LABEL_RADIUS
            // Baseline is nudged in buildScale's caller-independent way: the renderer adds the
            // font's vertical centring offset, which depends on the Paint, not on the geometry.
            hourLabelY[h] = cy - cosA * r * LABEL_RADIUS
        }

        // The fine ring is 60 divisions rather than the 96 quarter-hours the 24 h scale would
        // suggest, because the seconds marker has to land dead centre on a tick and 96/60 is not a
        // whole number. Sixty is also the honest scale for the minute hand, which turns once an
        // hour. The two rings coincide every two hours; elsewhere an hour tick simply falls between
        // two minute ticks.
        var minor = 0
        var major = 0
        for (i in 0 until MINUTE_TICKS) {
            val angle = i * 2.0 * Math.PI / MINUTE_TICKS
            val sinA = sin(angle).toFloat()
            val cosA = cos(angle).toFloat()
            val x0 = cx + sinA * r * TICK_MINUTE_OUTER
            val y0 = cy - cosA * r * TICK_MINUTE_OUTER
            val x1 = cx + sinA * r * TICK_MINUTE_INNER
            val y1 = cy - cosA * r * TICK_MINUTE_INNER

            if (i % MINUTE_GRADUATION == 0) {
                val base = major++ * 4
                minuteFiveTicks[base] = x0
                minuteFiveTicks[base + 1] = y0
                minuteFiveTicks[base + 2] = x1
                minuteFiveTicks[base + 3] = y1
            } else {
                val base = minor++ * 4
                minuteTicks[base] = x0
                minuteTicks[base + 1] = y0
                minuteTicks[base + 2] = x1
                minuteTicks[base + 3] = y1
            }
        }

    }

    private fun buildHands() {
        // Hour hand — a slim lance with convex flanks and a rounded tail. No crossguard: against a
        // 24 h scale it read as a stray tick mark rather than as part of the hand. It stops just
        // short of the hour numerals, whose inner edge is around 0.638 r, so it points at them
        // without overlapping them.
        val hLen = r * 0.620f
        val hWide = r * 0.032f
        val tail = r * 0.115f
        hourHand.reset()
        hourHand.moveTo(cx, cy - hLen)
        hourHand.cubicTo(
            cx + hWide, cy - hLen * 0.58f,
            cx + hWide, cy - hLen * 0.16f,
            cx + hWide * 0.62f, cy + tail,
        )
        hourHand.quadTo(cx, cy + tail + r * 0.032f, cx - hWide * 0.62f, cy + tail)
        hourHand.cubicTo(
            cx - hWide, cy - hLen * 0.16f,
            cx - hWide, cy - hLen * 0.58f,
            cx, cy - hLen,
        )
        hourHand.close()

        // Minute hand — precision needle, tapering from base to tip, reaching almost to the inner
        // edge of the minute ring at 0.930 r so it is read against its own scale.
        val mLen = r * 0.905f
        val mBase = r * 0.020f
        val mTip = r * 0.005f
        val mTail = r * 0.130f
        minuteHand.reset()
        minuteHand.moveTo(cx - mTip, cy - mLen)
        minuteHand.lineTo(cx + mTip, cy - mLen)
        minuteHand.lineTo(cx + mBase, cy - r * 0.060f)
        minuteHand.lineTo(cx + mBase * 0.8f, cy + mTail)
        minuteHand.lineTo(cx - mBase * 0.8f, cy + mTail)
        minuteHand.lineTo(cx - mBase, cy - r * 0.060f)
        minuteHand.close()

        // Solid caps, cut out of the silhouettes themselves rather than drawn as separate shapes.
        // A hand-authored cap would have to re-derive the same curves and would drift out of
        // register with the outline the moment either is tweaked.
        buildSolid(hourHand, hLen, SOLID_TIP_HOUR, hourHandSolid)
        buildSolid(minuteHand, mLen, SOLID_TIP_MINUTE, minuteHandSolid)

        // The contours as fills, at interactive and at ambient weight. Round joins on the
        // interactive hand so the lance's point stays a point under a visible weight; mitred in
        // ambient, where the hairline is too thin for the difference to show.
        strokeToFill(hourHand, HAND_OUTLINE_WIDTH, Paint.Join.ROUND, hourHandRing)
        strokeToFill(minuteHand, HAND_OUTLINE_WIDTH, Paint.Join.ROUND, minuteHandRing)
        strokeToFill(hourHand, AMBIENT_HAND_WIDTH, Paint.Join.MITER, hourHandAmbientRing)
        strokeToFill(minuteHand, AMBIENT_HAND_WIDTH, Paint.Join.MITER, minuteHandAmbientRing)
    }

    /** [src] stroked at [width] (a fraction of the radius), as the shape that stroke covers. */
    private fun strokeToFill(src: Path, width: Float, join: Paint.Join, out: Path) {
        contourPaint.strokeWidth = r * width
        contourPaint.strokeJoin = join
        out.reset()
        contourPaint.getFillPath(src, out)
    }

    /** Intersects [silhouette] with a band at the point and another below the hub. */
    private fun buildSolid(silhouette: Path, length: Float, tipDepth: Float, out: Path) {
        val tip = Path().apply {
            addRect(
                cx - r, cy - length - r * 0.02f,
                cx + r, cy - length + r * tipDepth,
                Path.Direction.CW,
            )
        }
        // The counterweight starts at the edge of the hub, not above it: taken from the centre it
        // swallows the widest part of the shaft and the two hands pool into a blob where they
        // cross. Tucked just under the hub it still reads as attached.
        val tail = Path().apply {
            addRect(cx - r, cy + hubRadius * 0.5f, cx + r, cy + r, Path.Direction.CW)
        }
        tip.op(tail, Path.Op.UNION)
        out.set(silhouette)
        out.op(tip, Path.Op.INTERSECT)
    }

    private fun buildTelemetry() {
        telemetryTextSize = r * TEXT_SIZE
        for (i in LINE_BASELINES.indices) {
            telemetryLineY[i] = cy + r * LINE_BASELINES[i]
        }

        batteryLineY = cy + r * BATTERY_BASELINE
        statusLineY = cy + r * STATUS_BASELINE

        sensorLineY = cy + r * SENSOR_BASELINE
        sensorOffsetX = r * SENSOR_OFFSET_X
        sensorGlyphSize = r * TEXT_SIZE * SENSOR_GLYPH_RATIO
        sensorGlyphGap = r * SENSOR_GLYPH_GAP

        val symbolSize = r * SYMBOL_SIZE
        symbolBox.set(
            cx, telemetryLineY[2] - symbolSize,
            cx + symbolSize, telemetryLineY[2],
        )

        val glyphSize = r * TEXT_SIZE
        glyphBox.set(
            cx, telemetryLineY[3] - glyphSize,
            cx + glyphSize, telemetryLineY[3],
        )
        glyphGap = r * GLYPH_GAP

        // Loose boxes for the two spoken labels, not drawing geometry: TalkBack highlights them
        // and reads the text, so "roughly where the words are" is the whole requirement.
        a11yReadoutBounds.set(
            (cx - r * 0.45f).toInt(), (cy - r * 0.45f).toInt(),
            (cx + r * 0.45f).toInt(), (cy + r * 0.45f).toInt(),
        )
        a11yStatusBounds.set(
            (cx - r * 0.45f).toInt(), (statusLineY - telemetryTextSize * 1.2f).toInt(),
            (cx + r * 0.45f).toInt(), (statusLineY + telemetryTextSize * 0.4f).toInt(),
        )
    }

    companion object {
        const val HOUR_TICKS = 24

        /** One division per minute of the minute hand and per second of the seconds cursor. */
        const val MINUTE_TICKS = 60
        const val SECONDS_SEGMENT_DEGREES = 360f / MINUTE_TICKS

        /** Every fifth division is drawn heavier. */
        const val MINUTE_GRADUATION = 5
        const val MINUTE_MAJOR_TICKS = MINUTE_TICKS / MINUTE_GRADUATION
        const val MINUTE_MINOR_TICKS = MINUTE_TICKS - MINUTE_MAJOR_TICKS

        /** Outer edge of the seconds marker, just clear of the tick tips. */
        private const val SECONDS_MARKER_BASE = 0.976f

        /** Marker base as a multiple of the minute-tick pitch — a little wider, as asked. */
        private const val SECONDS_MARKER_PITCH_RATIO = 1.15

        private const val RING_RADIUS = 0.985f

        /** Outer band: the 60-division minute and seconds scale. */
        private const val TICK_MINUTE_INNER = 0.930f
        private const val TICK_MINUTE_OUTER = 0.970f

        /**
         * Inner band: the 24-hour scale, on its own ring. The two scales do not share divisors, so
         * they get their own tracks rather than fighting for the same one; the hour ticks stop
         * short of the minute ring so the gap between them reads as deliberate.
         */
        private const val TICK_HOUR_INNER = 0.785f
        private const val TICK_HOUR_OUTER = 0.870f
        private const val TICK_CARDINAL_OUTER = 0.905f

        private const val LABEL_RADIUS = 0.680f

        /** The duty arc rides on the inner circle the hour ticks spring from. */
        const val DUTY_ARC_RADIUS = 0.760f

        /**
         * The readout is bare type on the dial — no window, no frame — split either side of the
         * hub: duty and ZULU above, weather and site below, the two sensor slots on the hub's own
         * line, the battery a step under the block and the vigilance status a step over it.
         * Offsets are signed, measured from the centre.
         *
         * ### One size, and it is the numerals'
         * Every piece of type on the dial is set at one em: the hour numerals at
         * [HOUR_LABEL_SIZE], and every readout row — duty, ZULU, the sensor slots, weather, site,
         * battery, the vigilance status — at [TEXT_SIZE], which *is* that figure. Until
         * 2026-11-06 the readout carried four sizes (0.096, 0.086 for the slots and the battery,
         * 0.078 for the status word), each sized to its own row's width budget, and together a
         * panel the eye re-focused for at every line. One size was the fix; the first attempt at
         * it, 0.112 for everything, was wrong in the other direction and lasted an afternoon: the
         * readout outgrew the dial's own figures by a sixth and ran to within five pixels of
         * them, and beside the Mars face, whose rows were still at 0.096, it looked crowded. The
         * dial is the instrument and the readout is printed on it; the type inside must not be
         * larger than the figures round the edge. Colour and weight carry the hierarchy, as they
         * already did.
         *
         * ### The width budget
         * Monospace advance is 0.60 em, so n characters are 0.6 × n × [TEXT_SIZE] wide; a
         * horizontal line at vertical offset y with half-width w reaches radius sqrt(w² + y²) at
         * its corners, and that has to stay inside the hour numerals, whose inner edge sits at
         * [NUMERAL_INNER_EDGE]. The longest rows: the Earth weather row `-40°C TSGR Q1013` at 16,
         * `DUTY: 03:42 REM` at 15, `Z 18AUG 18:42` at 13, the site row at a pictogram plus
         * `ABANDO 5.0KM` — six characters is the longest code in the bundled data, and the lock
         * radius keeps the distance under ten — `ACKNOWLEDGE` at 11, and `BAT 100%` and
         * `QFE 1013` at 8. The four readout rows clear the numerals by thirteen pixels and more
         * on a 454 px dial; the battery, a pitch further out, by five, and a pressure slot by six.
         * `GeometryBudgetTest` re-derives every corner from these constants.
         *
         * 0.095 was the numerals' size as the dial shipped; 0.100 is one hundredth up at the
         * user's word, having seen 0.095 on the wrist — a pixel of em on a 454 px dial, the
         * numerals and the readout moving together so the one size stays one.
         */
        internal const val HOUR_LABEL_SIZE = 0.100f
        internal const val TEXT_SIZE = HOUR_LABEL_SIZE

        /**
         * Monospace advance, the figure every width above is written in. Owned here rather than in
         * [TelemetryLayer][com.avdesign.mfd24.render.TelemetryLayer] because this file is where the
         * budget arithmetic lives; the layer draws with it, `GeometryBudgetTest` audits with it.
         */
        internal const val MONO_ADVANCE = 0.60f

        /** Space between a pictogram and the line it leads, as a fraction of the radius. */
        internal const val GLYPH_GAP = 0.028f

        /**
         * Inner edge of the hour numerals, the boundary every readout corner must stay inside.
         * Named so `GeometryBudgetTest` can check the shipped rows against it instead of trusting
         * the prose above.
         */
        internal const val NUMERAL_INNER_EDGE = 0.632f

        /**
         * One pitch between every baseline on the dial, counted from the hub's own line.
         *
         * Seven baselines — status, duty, ZULU, the sensor slots, weather, site, battery — at one
         * spacing, so the block reads as ruled rather than placed. The type's cap height is about
         * 0.72 em, 0.072 r, which leaves 0.103 r of air between lines, a cap and a half: the
         * spacing the Mars face's upper pair had, and what makes the block read as set rather
         * than stacked. The hub line is where a row of capitals is centred on the hub itself, half
         * a cap above the baseline; the rows either side of it are then symmetric about the dial's
         * centre, as the two pairs used to be by hand.
         *
         * The pitch is the largest the battery row allows: a pitch further out than the site row,
         * at 0.175 its corner sits five pixels inside the numerals, and 0.180 puts it within two.
         * Pulling the block in instead would spend the height the dial has on nothing.
         */
        internal const val LINE_PITCH = 0.175f
        internal const val HUB_BASELINE = 0.036f

        /** Duty, ZULU, weather, site — two pitches above the hub line to two below. */
        internal val LINE_BASELINES = floatArrayOf(
            HUB_BASELINE - 2f * LINE_PITCH,
            HUB_BASELINE - LINE_PITCH,
            HUB_BASELINE + LINE_PITCH,
            HUB_BASELINE + 2f * LINE_PITCH,
        )

        /**
         * The battery row, a pitch under the site row. Eight characters at most — `BAT 100%` —
         * which is what lets it sit this far out: its corner reaches 0.608 r, and a sixteen-character
         * row here would be into the numerals. Lengthen it and that stops being true.
         */
        internal const val BATTERY_BASELINE = HUB_BASELINE + 3f * LINE_PITCH

        /** The reference-frame symbol leads the first data row, a little taller than the type. */
        internal const val SYMBOL_SIZE = 0.104f

        /**
         * The two optional readouts sit on the hub's own line, outboard of every other row.
         *
         * ### Why here
         * This is the only clear ground left: on the hub line there is nothing at all between the
         * hub and the hour numerals.
         *
         * ### The arithmetic
         * The offset is set by the widest line, not by the shortest. `QFE 1013` is eight characters
         * at 0.60 em of [TEXT_SIZE], 0.480 r wide and 0.240 r either side of its centre; at 0.364 r
         * that puts the outer edge at 0.604 r, six pixels short of the numerals — and on a noon-up
         * dial `06` and `18` stand exactly there — and the inner edge at 0.124 r, well outside the
         * hub. A pictogram slot takes about half the width and simply sits further in, which keeps
         * the two symmetrical.
         *
         * Lengthen a slot past eight characters and the arithmetic stops holding, which is why
         * steps go to `12K` rather than running to five figures.
         */
        internal const val SENSOR_OFFSET_X = 0.364f
        internal const val SENSOR_BASELINE = HUB_BASELINE

        /** Glyph height against the type it sits with, and the space between the two. */
        private const val SENSOR_GLYPH_RATIO = 0.90f
        private const val SENSOR_GLYPH_GAP = 0.022f

        /**
         * The vigilance status, a pitch over the duty row, appears only when the monitor is doing
         * something worth saying. `ACKNOWLEDGE` is the longest word, and clears the numerals by
         * nine pixels this far out.
         */
        internal const val STATUS_BASELINE = HUB_BASELINE - 3f * LINE_PITCH

        /**
         * Centre hub, as a fraction of the dial radius — about 9 dp across on a 454 px watch.
         *
         * It was 0.030, which is 6.8 dp and too small to read a fraction inside. Enlarging it is
         * what makes the vigilance core legible; going further, to the 30-odd dp a separate
         * indicator would want, would put the hub into the weather row 38 px below the centre.
         */
        private const val HUB_RADIUS = 0.040f

        /** How far down from each point the hand stays filled. */
        private const val SOLID_TIP_HOUR = 0.155f
        private const val SOLID_TIP_MINUTE = 0.215f

        /** Contour weight of the skeletonised body: heavy enough to read, light enough to see past. */
        const val HAND_OUTLINE_WIDTH = 0.009f

        /** The ambient contour, a shade heavier: it has to survive the dimmed palette. */
        const val AMBIENT_HAND_WIDTH = 0.011f

        /**
         * Screen angle for an hour on the 24-hour dial, in radians clockwise from 12 o'clock.
         * Noon is up and midnight down, or the other way round when [midnightUp] flips the axis.
         */
        fun hourAngleRad(hours: Double, midnightUp: Boolean): Double =
            (hours / 24.0 + if (midnightUp) 0.0 else 0.5) * 2.0 * Math.PI
    }
}
