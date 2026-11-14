// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.avdesign.mfd24.astro.AstroTime
import com.avdesign.mfd24.astro.PlanetMode
import com.avdesign.mfd24.data.PositionAge
import com.avdesign.mfd24.data.TelemetryState
import com.avdesign.mfd24.data.VigilanceState
import com.avdesign.mfd24.data.WatchShiftState
import com.avdesign.mfd24.data.WeatherCondition
import com.avdesign.mfd24.style.Palette
import com.avdesign.mfd24.text.Coordinates
import com.avdesign.mfd24.text.TextBuf
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The readout printed on the dial.
 *
 * Bare type — no window, no fill, no frame — set in one size and one weight, on one grid, split
 * either side of the hub. One size means one: the sensor slots, the battery row and the vigilance
 * status are set at the readout's [Geometry.TEXT_SIZE] too, and nothing here changes `textSize`
 * after the first frame's setup. Colour and weight carry the hierarchy. It is drawn under the
 * hands, the way dial text always is, and each glyph is laid down twice, first as a
 * background-coloured outline and then as fill, so it separates cleanly from the ticks and the
 * duty arc.
 *
 * ```
 *                  EARTH                  MARS               MOON
 *   above  row 1   DUTY: 03:42 REM        DUTY: 03:42 REM    OFF-DUTY
 *          row 2   Z 18AUG 18:42 +3       Z 18AUG 18:42      Z 18AUG 18:42
 *   below  row 3   ✈ UUEE 4.2KM ⊛         ♂ SOL 54321        ☾ LUNAR DAY 20834
 *          row 4   -4°C OVC Q1013         MTC 18:42          LTC 18:42
 * ```
 * On Earth the location comes first: the site pictogram leads row 3 and the position symbol
 * (⊛, a satellite or a pin) closes it; the weather follows on row 4. The site pictogram is chosen
 * by the site type *and* its flags — see [SiteGlyph].
 * The reference frame is an astronomical symbol rather than a word: it carries the same meaning in
 * a fraction of the width, and it is drawn as a path because font coverage for ♁ ♂ ☾ on Wear OS is
 * no more reliable than it is for emoji. Earth-referenced ZULU is present in every mode, as the
 * brief requires.
 */
class TelemetryLayer {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    /** Background-coloured outline drawn under every glyph. */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    /** Every pictogram — site, position or frame symbol — goes through this pair. */
    private val symbolHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Refreshed once per frame; used to sit the pictograms on the type's optical middle. */
    private val metrics = Paint.FontMetrics()

    /**
     * Bold monospace, built once.
     *
     * `Typeface.create` allocates, so the low-battery weight cannot be produced in the drawing
     * path. Two faces are made here and the paint is pointed at one or the other, which costs a
     * field write.
     */
    private val monoBold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private val zulu = TextBuf(24)

    /** The dial's offset from Zulu, ` +2` or ` -3:30`, drawn after the Zulu group; see [buildZone]. */
    private val zone = TextBuf(8)
    private val battery = TextBuf(12)
    private val sensor = TextBuf(12)

    /** One cached pictogram per slot, keyed on the reading and the layout. */
    private val sensorGlyphPath = arrayOf(Path(), Path())
    private val sensorGlyphKey = intArrayOf(-1, -1)
    private val sensorGlyphLayout = intArrayOf(-1, -1)
    private val sensorGlyphX = floatArrayOf(Float.NaN, Float.NaN)
    private val sensorGlyphBox = RectF()
    private val duty = TextBuf(24)
    private val line1 = TextBuf(40)
    private val line2 = TextBuf(40)

    /** Built with its left edge at the dial centre; drawing translates it into place. */
    private val symbolPath = Path()
    private var symbolMode = -1
    private var symbolLayout = -1

    private val glyphPath = Path()


    /** The rocket's exhaust, built beside [glyphPath] for a spaceport and empty otherwise. */
    private val flamePath = Path()

    /** Type and flags packed together — both pick the pictogram, so both are the cache key. */
    private var glyphKey = -1
    private var glyphLayout = -1

    /** Local copy of the site code, refreshed only when the resolver publishes a new one. */
    private val siteCode = CharArray(8)
    private var siteCodeLength = 0
    private var siteVersionSeen = -1

    fun draw(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        state: TelemetryState,
        planetMode: Int,
        epochMillis: Long,
        fahrenheit: Boolean,
        mmHg: Boolean,
        dutyState: Int,
        dutyMillis: Long,
        vigilanceStatus: Int,
        showClearHint: Boolean,
        incidentMillis: Long,
        incidentElapsedMillis: Long,
        sensorLeft: Int,
        sensorRight: Int,
        layoutGeneration: Int,
        frameSymbol: Boolean,
        dialOffsetMillis: Long,
        positionAge: Int,
    ) {
        buildLines(state, planetMode, epochMillis, fahrenheit, mmHg)
        buildZone(planetMode, dialOffsetMillis)
        if (vigilanceStatus == VigilanceState.INCIDENT) {
            buildIncident(incidentMillis, incidentElapsedMillis)
        } else {
            buildDuty(dutyState, dutyMillis)
        }

        textPaint.textSize = g.telemetryTextSize
        textPaint.getFontMetrics(metrics)
        haloPaint.textSize = g.telemetryTextSize
        haloPaint.strokeWidth = g.r * HALO_WIDTH
        haloPaint.color = palette.background
        symbolHaloPaint.strokeWidth = g.r * HALO_WIDTH
        symbolHaloPaint.color = palette.background

        textPaint.textAlign = Paint.Align.CENTER
        haloPaint.textAlign = Paint.Align.CENTER

        drawVigilanceStatus(canvas, g, palette, vigilanceStatus, showClearHint)
        // Before the readout rather than after, because the rows below have early returns and the
        // battery does not depend on any of them.
        drawBattery(canvas, g, palette, state.batteryPercent)
        drawSensorSlots(canvas, g, palette, state, sensorLeft, sensorRight, layoutGeneration)

        // Duty leads: it is the row that changes meaning, and ZULU sits below it where the dial is
        // wider — which is what lets the date group share the line. An incident takes the row
        // outright: how long a shift still had to run stopped being the useful thing on this dial
        // the moment nobody answered.
        if (vigilanceStatus == VigilanceState.INCIDENT) {
            textPaint.color = palette.second
            drawHaloed(canvas, duty, g.cx, g.telemetryLineY[0])
        } else {
            textPaint.color = when (dutyState) {
                WatchShiftState.DUTY_ACTIVE, WatchShiftState.DUTY_PENDING -> palette.dutyArc
                WatchShiftState.DUTY_SERVED -> palette.dutyArcSpent
                else -> palette.lumeDim
            }
            drawHaloed(canvas, duty, g.cx, g.telemetryLineY[0])
        }

        textPaint.color = palette.lume
        if (zone.length == 0) {
            drawHaloed(canvas, zulu, g.cx, g.telemetryLineY[1])
        } else {
            // Zulu and the dial's offset centred as one group, the offset a step dimmer: it is a
            // note on the hour scale, as the date is a note on the time, not a second reading.
            val zuluWidth = textPaint.measureText(zulu.chars, 0, zulu.length)
            val left = g.cx - (zuluWidth + textPaint.measureText(zone.chars, 0, zone.length)) / 2f
            textPaint.textAlign = Paint.Align.LEFT
            haloPaint.textAlign = Paint.Align.LEFT
            drawHaloed(canvas, zulu, left, g.telemetryLineY[1])
            textPaint.color = palette.lumeDim
            drawHaloed(canvas, zone, left + zuluWidth, g.telemetryLineY[1])
            textPaint.textAlign = Paint.Align.CENTER
            haloPaint.textAlign = Paint.Align.CENTER
        }

        textPaint.color = palette.lumeSoft
        if (planetMode == PlanetMode.EARTH) {
            drawEarthRows(canvas, g, palette, state, layoutGeneration, positionAge)
            return
        }

        // Off Earth the reference-frame symbol leads the first data row, so the mode costs no
        // line of its own, and the second carries the body's own clock. Neither has a site.
        if (frameSymbol) {
            if (symbolMode != planetMode || symbolLayout != layoutGeneration) {
                buildSymbol(g, planetMode)
                symbolMode = planetMode
                symbolLayout = layoutGeneration
            }
            drawGlyphedLine(canvas, g, palette.lume, symbolPath, line1, g.telemetryLineY[2])
        } else if (line1.length > 0) {
            drawHaloed(canvas, line1, g.cx, g.telemetryLineY[2])
        }
        drawHaloed(canvas, line2, g.cx, g.telemetryLineY[3])
    }

    /**
     * The Earth face's two rows under the hub: where the dial is, then the weather there.
     *
     * Location leads because it is what the position symbol describes, and the symbol belongs
     * beside the place it measured rather than beside a temperature: a satellite for a position
     * the watch measured, the cross in a map pin for one typed in by hand — which is also the
     * dial's clock, see [DialZone]. It closes the row, after the site pictogram when a site is
     * locked and the site, the coordinates or `NO SITE`. The upper row is the wider one, which is what
     * pays for two pictograms; the weather row below it lost its symbol and fits the lower row
     * as bare text. No position, no location row — and the weather row stays where it is, so the
     * block does not jump when a fix arrives.
     */
    private fun drawEarthRows(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        state: TelemetryState,
        layoutGeneration: Int,
        positionAge: Int,
    ) {
        drawWeatherRow(canvas, g)
        if (!state.hasPosition) return

        val symbol = when (state.positionSource) {
            TelemetryState.POSITION_MANUAL -> SYMBOL_TYPED
            TelemetryState.POSITION_DEVICE -> SYMBOL_MEASURED
            else -> NO_SYMBOL
        }
        if (symbol != NO_SYMBOL && (symbolMode != symbol || symbolLayout != layoutGeneration)) {
            buildSymbol(g, symbol)
            symbolMode = symbol
            symbolLayout = layoutGeneration
        }
        val site = state.siteValid
        if (site) prepareSiteGlyph(g, state, layoutGeneration)

        // The position symbol closes the row, a character's space after the text: in front it
        // stacked against the site pictogram, two signs before anything was said.
        val siteWidth = if (site) g.glyphBox.width() + g.siteTextGap else 0f
        val textWidth = textPaint.measureText(line2.chars, 0, line2.length)
        val symbolWidth = if (symbol == NO_SYMBOL) 0f else g.siteTextGap + g.symbolBox.width()
        var x = g.cx - (siteWidth + textWidth + symbolWidth) / 2f

        if (site) {
            drawPictogram(
                canvas, g, glyphPath, x, g.glyphBox.height(), siteColor(palette, state),
                rightColor = siteRightColor(palette, state),
                glyphWidth = g.glyphBox.width(),
                overlay = flamePath, overlayColor = palette.flame,
            )
            x += siteWidth
        }
        textPaint.textAlign = Paint.Align.LEFT
        haloPaint.textAlign = Paint.Align.LEFT
        drawHaloed(canvas, line2, x, g.telemetryLineY[2])
        textPaint.textAlign = Paint.Align.CENTER
        haloPaint.textAlign = Paint.Align.CENTER
        x += textWidth + g.siteTextGap

        if (symbol != NO_SYMBOL) {
            // A measured position says how much it is still worth (PositionAge): full while it
            // can be trusted, dimmed once it may have drifted past the site lock, struck out after
            // half an hour on the move without a fix. The position stays on the dial throughout.
            val measured = symbol == SYMBOL_MEASURED
            val aged = measured && positionAge != PositionAge.FRESH
            drawPictogram(
                canvas, g, symbolPath, x, g.symbolBox.height(),
                if (aged) palette.lumeDim else palette.lume,
                strike = measured && positionAge == PositionAge.LOST,
                strikeColor = palette.lume,
            )
        }
    }

    /**
     * The weather, bare. The site pictogram on the row above already says whether this is an
     * aerodrome's own METAR: a METAR is fetched only for a locked aerodrome, so a second aircraft
     * here said nothing the first had not.
     */
    private fun drawWeatherRow(canvas: Canvas, g: Geometry) {
        if (line1.length == 0) return
        drawHaloed(canvas, line1, g.cx, g.telemetryLineY[3])
    }

    private fun prepareSiteGlyph(g: Geometry, state: TelemetryState, layoutGeneration: Int) {
        val key = state.siteType shl 8 or (state.siteFlags and 0xFF)
        if (glyphKey != key || glyphLayout != layoutGeneration) {
            buildGlyph(g, state.siteType, state.siteFlags)
            glyphKey = key
            glyphLayout = layoutGeneration
        }
    }

    /**
     * The accent colour marks a military site. It is the same accent the seconds cursor and the
     * hub ring use, and it is never the lume hue, so it separates at any size -- which is more
     * than the silhouette alone can promise in a 22 px box.
     */
    private fun siteColor(palette: Palette, state: TelemetryState): Int =
        if (SiteGlyph.isMilitary(state.siteFlags)) palette.second else palette.lume

    /** A civil field with a military one in the same radius keeps its airliner and takes the
     * accent on its right half. */
    private fun siteRightColor(palette: Palette, state: TelemetryState): Int =
        if (SiteGlyph.isShared(state.siteFlags)) palette.second else siteColor(palette, state)

    /** The frame symbol and its text as one group, centred on the dial. */
    private fun drawGlyphedLine(
        canvas: Canvas,
        g: Geometry,
        glyphColor: Int,
        path: Path,
        buf: TextBuf,
        baselineY: Float,
    ) {
        val glyphWidth = g.symbolBox.width()
        val textWidth = textPaint.measureText(buf.chars, 0, buf.length)
        // An empty buffer means the pictogram is the whole row, so it centres on its own rather
        // than being nudged aside by a gap that leads to nothing.
        val groupWidth =
            if (buf.length == 0) glyphWidth else glyphWidth + g.glyphGap + textWidth
        val left = g.cx - groupWidth / 2f
        drawPictogram(canvas, g, path, left, g.symbolBox.height(), glyphColor)
        if (buf.length == 0) return
        textPaint.textAlign = Paint.Align.LEFT
        haloPaint.textAlign = Paint.Align.LEFT
        drawHaloed(canvas, buf, left + glyphWidth + g.glyphGap, baselineY)
        textPaint.textAlign = Paint.Align.CENTER
        haloPaint.textAlign = Paint.Align.CENTER
    }

    /**
     * One pictogram with its left edge at [left]. Paths are built with their left edge at the
     * dial centre and their foot on the row's baseline, so positioning is a translate rather
     * than a rebuild.
     *
     * The vertical shift matters: a pictogram sitting on the baseline hangs low against digits,
     * which have no descender. Both are centred on the type's optical middle instead, which the
     * font metrics give as `baseline + (ascent + descent) / 2`.
     */
    private fun drawPictogram(
        canvas: Canvas,
        g: Geometry,
        path: Path,
        left: Float,
        glyphHeight: Float,
        glyphColor: Int,
        rightColor: Int = glyphColor,
        glyphWidth: Float = 0f,
        overlay: Path? = null,
        overlayColor: Int = 0,
        strike: Boolean = false,
        strikeColor: Int = 0,
    ) {
        val dy = (metrics.ascent + metrics.descent) / 2f + glyphHeight / 2f
        canvas.save()
        canvas.translate(left - g.cx, dy)
        canvas.drawPath(path, symbolHaloPaint)
        glyphPaint.color = glyphColor
        if (rightColor == glyphColor) {
            canvas.drawPath(path, glyphPaint)
        } else {
            // Split down the middle of the glyph box: the path is built with its left edge on the
            // dial centre, so the seam is half a glyph to the right of it. Two clips, no allocation.
            val seam = g.cx + glyphWidth / 2f
            canvas.save()
            canvas.clipRect(g.cx - g.r, g.cy - g.r, seam, g.cy + g.r)
            canvas.drawPath(path, glyphPaint)
            canvas.restore()
            canvas.save()
            canvas.clipRect(seam, g.cy - g.r, g.cx + g.r, g.cy + g.r)
            glyphPaint.color = rightColor
            canvas.drawPath(path, glyphPaint)
            canvas.restore()
        }
        if (overlay != null) {
            glyphPaint.color = overlayColor
            canvas.drawPath(overlay, glyphPaint)
        }
        if (strike) {
            // Corner to corner across the satellite's axis, haloed so it reads as a line laid on
            // the glyph rather than one more part of it.
            val box = g.symbolBox
            strikePaint.strokeWidth = g.r * STRIKE_HALO_WIDTH
            strikePaint.color = symbolHaloPaint.color
            canvas.drawLine(box.left, box.top, box.right, box.bottom, strikePaint)
            strikePaint.strokeWidth = g.r * STRIKE_WIDTH
            strikePaint.color = strikeColor
            canvas.drawLine(box.left, box.top, box.right, box.bottom, strikePaint)
        }
        canvas.restore()
    }

    /**
     * The two optional readouts either side of the hub.
     *
     * Drawn from the same [TextBuf] twice over rather than from two, because nothing is kept
     * between frames here and one buffer costs one allocation at construction instead of two.
     *
     * The label is dim and the value is not, so a glance lands on the number. Both are haloed like
     * every other piece of type on this dial: the hands sweep straight through this ground, and a
     * halo is what keeps a digit legible with a hand crossing it.
     */
    private fun drawSensorSlots(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        state: TelemetryState,
        leftKind: Int,
        rightKind: Int,
        layoutGeneration: Int,
    ) {
        if (leftKind == SLOT_OFF && rightKind == SLOT_OFF) return
        if (leftKind != SLOT_OFF) {
            drawSensorSlot(
                canvas, g, palette, state, leftKind, g.cx - g.sensorOffsetX, 0, layoutGeneration
            )
        }
        if (rightKind != SLOT_OFF) {
            drawSensorSlot(
                canvas, g, palette, state, rightKind, g.cx + g.sensorOffsetX, 1, layoutGeneration
            )
        }
    }

    /**
     * One slot: the pictogram and the value on one line, at one size.
     *
     * The pair is centred on the slot as a unit, which means placing each half against the other's
     * width rather than aligning either to the slot's own centre. Widths come from the monospace
     * advance the rest of the dial is laid out on, not from measuring: the figure is deterministic,
     * and the layout arithmetic is already written down in [Geometry] in those terms.
     */
    private fun drawSensorSlot(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        state: TelemetryState,
        kind: Int,
        x: Float,
        slot: Int,
        layoutGeneration: Int,
    ) {
        buildSensorValue(state, kind)
        textPaint.color = palette.lumeSoft

        // Station pressure has no pictogram, so it leads with its own name and the line is centred
        // on the slot as it stands.
        if (kind == SLOT_PRESSURE) {
            drawHaloed(canvas, sensor, x, g.sensorLineY)
            return
        }

        val textWidth = sensor.length * g.telemetryTextSize * Geometry.MONO_ADVANCE
        val shift = (g.sensorGlyphSize + g.sensorGlyphGap) * 0.5f
        drawSensorGlyph(
            canvas, g, palette, kind, x - (textWidth + g.sensorGlyphGap) * 0.5f,
            slot, layoutGeneration,
        )
        drawHaloed(canvas, sensor, x + shift, g.sensorLineY)
    }

    /**
     * The heart or the walking figure, cached per slot.
     *
     * Rebuilt when the reading, the layout, or the *position* changes, and the position moves with
     * the value: a pulse going from 99 to 100 widens the line and shifts the glyph. Rebuilding
     * scales a path into place, which is worth not repeating for a shape that has not moved.
     */
    private fun drawSensorGlyph(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        kind: Int,
        centreX: Float,
        slot: Int,
        layoutGeneration: Int,
    ) {
        val path = sensorGlyphPath[slot]
        if (sensorGlyphKey[slot] != kind ||
            sensorGlyphLayout[slot] != layoutGeneration ||
            sensorGlyphX[slot] != centreX
        ) {
            val half = g.sensorGlyphSize * 0.5f
            // Centred on the type's own optical middle rather than sitting on the baseline, so the
            // glyph and the digits read as one line and not as a picture with a caption.
            val middle = g.sensorLineY - g.telemetryTextSize * CAP_MIDDLE
            sensorGlyphBox.set(centreX - half, middle - half, centreX + half, middle + half)
            if (kind == SLOT_HEART_RATE) {
                Glyphs.buildHeart(sensorGlyphBox, path)
            } else {
                Glyphs.buildPedestrian(sensorGlyphBox, path)
            }
            sensorGlyphKey[slot] = kind
            sensorGlyphLayout[slot] = layoutGeneration
            sensorGlyphX[slot] = centreX
        }
        glyphPaint.color = palette.lumeSoft
        canvas.drawPath(path, glyphPaint)
    }

    /**
     * Four characters at the outside, which is the width budget the position pays for.
     *
     * Steps go to thousands past 9999 rather than being truncated or allowed to run to five
     * figures: `12K` is the honest thing to say when the exact count no longer fits and no longer
     * matters. Station pressure drops its fraction for the same reason `1013` is what an altimeter
     * setting looks like.
     */
    private fun buildSensorValue(state: TelemetryState, kind: Int) {
        sensor.clear()
        val value = when (kind) {
            SLOT_HEART_RATE -> state.heartRate
            SLOT_STEPS -> state.stepsToday
            else -> state.localPressureTenths
        }
        if (value < 0) {
            if (kind == SLOT_PRESSURE) sensor.lit(TextBuf.LIT_SENSOR_QFE)
            sensor.lit(TextBuf.LIT_SENSOR_NONE)
            return
        }
        when (kind) {
            SLOT_STEPS ->
                if (value >= STEPS_IN_THOUSANDS) {
                    sensor.uint(value / 1000).lit(TextBuf.LIT_THOUSANDS)
                } else {
                    sensor.uint(value)
                }

            SLOT_PRESSURE -> sensor.lit(TextBuf.LIT_SENSOR_QFE).uint((value + 5) / 10)

            else -> sensor.uint(value)
        }
    }

    /**
     * `BAT 84%`, below the readout block.
     *
     * One colour in every state — [Palette.lumeDim], the same step back the minor ticks take. A
     * charge figure that turned red would compete with the accent the seconds cursor, the duty arc
     * and the vigilance core already share, and none of those can afford a rival at the bottom of
     * the dial. Below [LOW_BATTERY_PERCENT] the weight goes up instead: it reads at a glance and it
     * borrows nothing.
     */
    private fun drawBattery(canvas: Canvas, g: Geometry, palette: Palette, percent: Int) {
        if (percent < 0) return
        battery.clear().lit(TextBuf.LIT_BATTERY).uint(percent).ch('%')

        val low = percent < LOW_BATTERY_PERCENT
        textPaint.typeface = if (low) monoBold else Typeface.MONOSPACE
        haloPaint.typeface = textPaint.typeface
        textPaint.color = palette.lumeDim
        drawHaloed(canvas, battery, g.cx, g.batteryLineY)

        // Put the shared paints back: every other row assumes the regular face.
        textPaint.typeface = Typeface.MONOSPACE
        haloPaint.typeface = Typeface.MONOSPACE
    }

    /**
     * The vigilance line: only what the operator has to *act* on.
     *
     * Armed and counting says nothing here — the hub core carries it, and a status permanently on
     * the dial stops being read. Suspended-on-charge says nothing either, since the hub is empty
     * and the watch is visibly on a charger; a line of type to repeat both was clutter that took a
     * row from the readout and told nobody anything they could not see.
     *
     * Off the wrist is the exception, and the reason is that it is *not* self-evident. The detector
     * can report off-body while the watch is worn — a loose strap, a sleeve — and that is the one
     * way this monitor can stop watching a wrist that is still there. It says so rather than
     * looking armed.
     */
    private fun drawVigilanceStatus(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        status: Int,
        showClearHint: Boolean,
    ) {
        val label = when (status) {
            VigilanceState.PROMPT -> TextBuf.LIT_VIGIL_PROMPT
            VigilanceState.ALARM -> TextBuf.LIT_VIGIL_ALARM
            // The first tap of the clearing pair answers with the second half of the gesture,
            // right where the eyes already are. MAN DOWN itself is not lost: the duty row still
            // carries the incident's time, and the word comes back the moment the hint expires.
            VigilanceState.INCIDENT ->
                if (showClearHint) TextBuf.LIT_TAP_AGAIN else TextBuf.LIT_MAN_DOWN
            VigilanceState.OFF_BODY -> TextBuf.LIT_OFF_WRIST
            else -> return
        }
        // Off the wrist is information, not an alert; the accent is reserved for the three states
        // that are asking something of somebody.
        textPaint.color =
            if (status == VigilanceState.OFF_BODY) palette.lumeDim else palette.second
        canvas.drawText(label, 0, label.size, g.cx, g.statusLineY, haloPaint)
        canvas.drawText(label, 0, label.size, g.cx, g.statusLineY, textPaint)
    }

    /** Outline first, then fill — [textPaint]'s colour must already be set. */
    private fun drawHaloed(canvas: Canvas, buf: TextBuf, x: Float, y: Float) {
        canvas.drawText(buf.chars, 0, buf.length, x, y, haloPaint)
        canvas.drawText(buf.chars, 0, buf.length, x, y, textPaint)
    }

    /**
     * The offset of the hour scale from Zulu, after the Zulu group: ` +2`, ` -5`, ` +5:30`,
     * ` +12:45`, and ` +0` rather than nothing in London in winter, because an empty slot would
     * read as "not known".
     *
     * It is what makes the dial's clock a stated fact rather than an inference. Since the scale
     * can run on the place's zone or the phone's, and the two part company in the air, at a border
     * and on a typed position, the reader needs to see which one is in force without opening the
     * settings. An offset and not an abbreviation: plenty of zones have none (the platform prints
     * `+0545`), and `CST` is Chicago, Shanghai and Havana at once. Twenty characters at worst,
     * `Z 13NOV 13:58 +12:45`, which `GeometryBudgetTest` holds inside the numerals.
     *
     * Earth only: on the other worlds the scale is not a time zone.
     */
    private fun buildZone(planetMode: Int, offsetMillis: Long) {
        zone.clear()
        if (planetMode != PlanetMode.EARTH) return
        val minutes = (offsetMillis / 60_000L).toInt()
        val magnitude = if (minutes < 0) -minutes else minutes
        zone.space().ch(if (minutes < 0) '-' else '+').uint(magnitude / 60)
        if (magnitude % 60 != 0) zone.ch(':').pad2(magnitude % 60)
    }

    /**
     * `21:14Z +02:13` — when the operator stopped answering, and how long ago that was.
     *
     * Zulu because it is the one clock that means the same thing to whoever reads it next, and it
     * is what the row below already shows. The elapsed figure rounds *down*: an incident four
     * minutes old must not read as five.
     */
    private fun buildIncident(incidentMillis: Long, elapsedMillis: Long) {
        duty.clear()
        val minuteOfDay = (incidentMillis / 60_000L).mod(1440L).toInt()
        duty.pad2(minuteOfDay / 60).ch(':').pad2(minuteOfDay % 60).lit(TextBuf.LIT_ZULU_SUFFIX)
        var minutes = (elapsedMillis / 60_000L).toInt()
        if (minutes < 0) minutes = 0
        if (minutes > MAX_DISPLAY_MINUTES) minutes = MAX_DISPLAY_MINUTES
        duty.ch(' ').ch('+').pad2(minutes / 60).ch(':').pad2(minutes % 60)
    }

    /**
     * `DUTY: 3:42 REM` while a shift runs, `DUTY IN 4:30` before a booked one begins, and
     * `OFF-DUTY` when none is set or the last one has been served.
     */
    private fun buildDuty(dutyState: Int, dutyMillis: Long) {
        duty.clear()
        when (dutyState) {
            WatchShiftState.DUTY_ACTIVE -> {
                appendDuration(duty.lit(TextBuf.LIT_DUTY), dutyMillis).lit(TextBuf.LIT_REM)
            }

            WatchShiftState.DUTY_PENDING -> {
                appendDuration(duty.lit(TextBuf.LIT_DUTY_IN), dutyMillis)
            }

            else -> duty.lit(TextBuf.LIT_OFF_DUTY)
        }
    }

    /**
     * Hours and minutes, rounded up so a live countdown never shows 0:00 while time remains, and
     * capped at 99:59 so no amount of nonsense upstream can push the line past its width budget.
     */
    private fun appendDuration(buf: TextBuf, millis: Long): TextBuf {
        var totalMinutes = ((millis + 59_999L) / 60_000L).toInt()
        if (totalMinutes > MAX_DISPLAY_MINUTES) totalMinutes = MAX_DISPLAY_MINUTES
        // Always two digits either side, like ZULU: a countdown that changes width as it passes
        // ten hours makes the whole row twitch.
        return buf.pad2(totalMinutes / 60).ch(':').pad2(totalMinutes % 60)
    }

    private fun buildLines(
        state: TelemetryState,
        planetMode: Int,
        epochMillis: Long,
        fahrenheit: Boolean,
        mmHg: Boolean,
    ) {
        val daySeconds = AstroTime.utcSecondOfDay(epochMillis)

        // ICAO date-time group order: the day and month lead, then the time, all in UTC.
        val monthDay = AstroTime.utcMonthDay(epochMillis)
        zulu.clear()
            .lit(TextBuf.LIT_ZULU)
            .pad2(monthDay % 100)
            .lit(TextBuf.MONTHS[monthDay / 100 - 1])
            .space()
            .pad2(daySeconds / 3600)
            .ch(':')
            .pad2((daySeconds / 60) % 60)
        // No seconds, on any world. Interactive, the rim cursor already carries them; in always-on
        // the row is redrawn once a minute, so a seconds field there stood still and lied.
        // Thirteen characters instead of sixteen is what pays for the row's larger size.

        line1.clear()
        line2.clear()

        when (planetMode) {
            PlanetMode.MARS -> {
                line1.lit(TextBuf.LIT_SOL).uint(AstroTime.marsSol(epochMillis))
                appendClock(line2.lit(TextBuf.LIT_MTC), AstroTime.marsTimeHours(epochMillis))
            }

            PlanetMode.MOON -> {
                line1.lit(TextBuf.LIT_LUNAR_DAY).uint(AstroTime.lunarDay(epochMillis))
                appendClock(line2.lit(TextBuf.LIT_LTC), AstroTime.lunarTimeHours(epochMillis))
            }

            else -> {
                appendWeather(state, fahrenheit, mmHg)
                appendSite(state)
            }
        }
    }

    private fun appendClock(buf: TextBuf, hours: Double) {
        val h = floor(hours).toInt()
        val m = ((hours - h) * 60.0).toInt()
        buf.pad2(h).ch(':').pad2(m)
    }

    /** Leaves the row empty when there is nothing to report; the weather row is then not drawn. */
    private fun appendWeather(state: TelemetryState, fahrenheit: Boolean, mmHg: Boolean) {
        if (!state.weatherValid) return

        val deciC = state.temperatureDeciC
        if (fahrenheit) {
            val deciF = deciC * 9 / 5 + 320
            line1.int(roundDeci(deciF)).lit(TextBuf.LIT_DEG_F)
        } else {
            line1.int(roundDeci(deciC)).lit(TextBuf.LIT_DEG_C)
        }

        line1.space().lit(WeatherCondition.token(state.conditionIndex)).space()

        val deciHpa = state.pressureDeciHpa
        if (mmHg) {
            line1.uint((deciHpa * 0.0750062f).roundToInt()).lit(TextBuf.LIT_MMHG)
        } else {
            line1.lit(TextBuf.LIT_QNH).uint(roundDeci(deciHpa))
        }
    }

    private fun appendSite(state: TelemetryState) {
        if (!state.siteValid) {
            // A fix but nothing within the radius is worth saying; no fix at all is not, because
            // the row is not drawn in that case.
            // COORDINATES: where there is no site to name, the position itself is the most useful
            // thing the row can say — at sea and in the air, which is most of the time out there.
            if (state.showCoordinates && !state.positionLatDeg.isNaN() &&
                !state.positionLonDeg.isNaN()
            ) {
                Coordinates.append(line2, state.positionLatDeg, state.positionLonDeg)
            } else {
                line2.lit(TextBuf.LIT_NO_SITE)
            }
            return
        }
        val version = state.siteVersion
        if (version != siteVersionSeen) {
            siteCodeLength = state.copySiteCode(siteCode)
            siteVersionSeen = version
        }
        line2.lit(siteCode, 0, siteCodeLength).space()
            .tenths(state.siteDistanceHm).lit(TextBuf.LIT_KM)
    }

    /** Rounds a tenths-scaled value to whole units, away from zero. */
    private fun roundDeci(deci: Int): Int =
        if (deci >= 0) (deci + 5) / 10 else -((-deci + 5) / 10)

    private fun buildSymbol(g: Geometry, symbol: Int) {
        when (symbol) {
            PlanetMode.MARS -> Glyphs.buildMarsSymbol(g.symbolBox, symbolPath)
            PlanetMode.MOON -> Glyphs.buildMoonSymbol(g.symbolBox, symbolPath)
            SYMBOL_TYPED -> Glyphs.buildPositionPin(g.symbolBox, symbolPath)
            SYMBOL_MEASURED -> Glyphs.buildSatellite(g.symbolBox, symbolPath)
            else -> Glyphs.buildEarthSymbol(g.symbolBox, symbolPath)
        }
    }

    /** Rasterises whichever silhouette [SiteGlyph] chose for this site. */
    private fun buildGlyph(g: Geometry, type: Int, flags: Int) {
        flamePath.reset()
        when (SiteGlyph.forSite(type, flags)) {
            SiteGlyph.ROCKET -> {
                Glyphs.buildRocket(g.glyphBox, glyphPath)
                Glyphs.buildRocketFlame(g.glyphBox, flamePath)
            }
            SiteGlyph.ANCHOR -> Glyphs.buildAnchor(g.glyphBox, glyphPath)
            SiteGlyph.HELICOPTER -> Glyphs.buildHelicopter(g.glyphBox, glyphPath)
            SiteGlyph.FIGHTER -> Glyphs.buildFighter(g.glyphBox, glyphPath)
            else -> Glyphs.buildAircraft(g.glyphBox, glyphPath)
        }
    }

    private companion object {
        /** Halo stroke, as a fraction of the dial radius. */
        const val HALO_WIDTH = 0.014f

        /** The location row carries no position symbol: there is no position to describe. */
        const val NO_SYMBOL = -1

        /** The map-pin cross: the position, and so the dial's clock, was typed in by hand. */
        const val SYMBOL_TYPED = 100

        /** A satellite: the position was measured — and how long ago is in its brightness. */
        const val SYMBOL_MEASURED = 101

        /** The strike across a lost position's satellite, and its halo. */
        const val STRIKE_WIDTH = 0.010f
        const val STRIKE_HALO_WIDTH = 0.026f

        /** 99:59 — a shift is at most a day, so anything longer is a bug, not a reading. */
        const val MAX_DISPLAY_MINUTES = 99 * 60 + 59

        /**
         * Sensor slot kinds, matching the ordinals of
         * [SensorSlots.Kind][com.avdesign.mfd24.data.SensorSlots.Kind]. Held as plain ints because
         * `render()` reads them once a frame and an enum lookup there is a needless indirection.
         */
        const val SLOT_OFF = 0
        const val SLOT_HEART_RATE = 1
        const val SLOT_STEPS = 2
        const val SLOT_PRESSURE = 3

        /** Past this the exact step count stops fitting, and stops mattering. */
        const val STEPS_IN_THOUSANDS = 10_000

        /** Half the cap height, as a fraction of the type size: where a line's optical middle is. */
        const val CAP_MIDDLE = 0.35f

        /** Below this the battery row goes bold. A quarter is where a watch stops being day's kit. */
        const val LOW_BATTERY_PERCENT = 25
    }
}
