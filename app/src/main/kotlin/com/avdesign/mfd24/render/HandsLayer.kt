// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.avdesign.mfd24.style.Palette
import kotlin.math.ceil

/**
 * Draws the two hands and the centre hub. Seconds live on the rim ([SecondsMarker]), not on a
 * needle.
 *
 * The hands are skeletons: a thin contour along the body, solid only at the point and the
 * counterweight. The readout is printed on the dial underneath them, and a filled hand sweeping
 * across it simply deletes a line of type for part of every hour. Hollowed out, the type reads
 * straight through the shaft while the solid tip still lands unambiguously on a mark.
 *
 * **In interactive mode each hand is a sprite, not a path.** It is rendered once — ring and
 * solid, by the paths in [Geometry] — into a small bitmap at [SUPERSAMPLE] times the screen's
 * density, and drawn rotated with bilinear filtering. Drawn as paths they were four software
 * masks a frame: Skia as shipped in Android 11 sends a coverage-antialiased fill of more than
 * ten verbs to its software path renderer, which never caches a mask under a rotated matrix, so
 * both hands were rasterised on the CPU and uploaded to the GPU again every second — about six
 * milliseconds of CPU a frame, measured on the watch with `debug.hwui.skia_atrace_enabled`.
 * Stroking them instead of filling changed nothing, and nor would quantising the angles: the
 * cache is skipped, not missed. A sprite is uploaded once and rotated on the GPU for the price
 * of two textured quads. The sprite's size follows the geometry rather than a fixed pixel count,
 * so a denser panel gets a denser sprite for free; it is rebuilt only when the layout or the
 * lume changes, the same exception [DialLayer] already makes to "render allocates nothing".
 * Ambient keeps the paths: it draws once a minute, and a mask a minute is not worth a bitmap.
 *
 * Every `Paint` is created here once; [drawHourMinute] only mutates primitive fields and calls into
 * the canvas.
 */
class HandsLayer {

    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var hourSprite: Bitmap? = null
    private var minuteSprite: Bitmap? = null

    /** Where each sprite lands in hand space — pointing up from the hub — in screen pixels. */
    private val hourBox = RectF()
    private val minuteBox = RectF()
    private val scratch = RectF()
    private var spriteLume = 0
    private var spriteLayout = -1

    /**
     * @param hourAngle degrees clockwise from 12 o'clock; one turn per 24 h of the selected world
     * @param minuteAngle degrees clockwise from 12 o'clock; one turn per hour
     * @param layoutGeneration bumped by the renderer whenever [Geometry] is rebuilt
     */
    fun drawHourMinute(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        hourAngle: Float,
        minuteAngle: Float,
        layoutGeneration: Int,
    ) {
        if (hourSprite == null || minuteSprite == null ||
            spriteLayout != layoutGeneration || spriteLume != palette.lume
        ) {
            rebuildSprites(g, palette.lume, layoutGeneration)
        }
        drawSprite(canvas, g, hourAngle, hourSprite!!, hourBox)
        drawSprite(canvas, g, minuteAngle, minuteSprite!!, minuteBox)
    }

    private fun drawSprite(canvas: Canvas, g: Geometry, angle: Float, sprite: Bitmap, box: RectF) {
        canvas.save()
        canvas.rotate(angle, g.cx, g.cy)
        canvas.drawBitmap(sprite, null, box, spritePaint)
        canvas.restore()
    }

    private fun rebuildSprites(g: Geometry, lume: Int, layoutGeneration: Int) {
        hourSprite?.recycle()
        minuteSprite?.recycle()
        handFill.color = lume
        hourSprite = render(g.hourHandRing, g.hourHandSolid, hourBox)
        minuteSprite = render(g.minuteHandRing, g.minuteHandSolid, minuteBox)
        spriteLume = lume
        spriteLayout = layoutGeneration
    }

    /**
     * The hand's two shapes, drawn into a bitmap that covers their joint bounds plus a margin for
     * the antialiased edge. [box] comes back as the rectangle the bitmap must be drawn into, pinned
     * to the bitmap's own size so the blit is an exact 1/[SUPERSAMPLE] scale and nothing drifts.
     */
    private fun render(ring: Path, solid: Path, box: RectF): Bitmap {
        ring.computeBounds(box, true)
        solid.computeBounds(scratch, true)
        box.union(scratch)
        box.inset(-SPRITE_MARGIN_PX, -SPRITE_MARGIN_PX)
        val width = ceil(box.width() * SUPERSAMPLE).toInt().coerceAtLeast(1)
        val height = ceil(box.height() * SUPERSAMPLE).toInt().coerceAtLeast(1)
        box.right = box.left + width / SUPERSAMPLE
        box.bottom = box.top + height / SUPERSAMPLE

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(SUPERSAMPLE, SUPERSAMPLE)
        canvas.translate(-box.left, -box.top)
        canvas.drawPath(ring, handFill)
        canvas.drawPath(solid, handFill)
        return bitmap
    }

    fun recycle() {
        hourSprite?.recycle()
        minuteSprite?.recycle()
        hourSprite = null
        minuteSprite = null
        spriteLayout = -1
    }

    /**
     * Centre hub, drawn last so the hand pivots stay clean, with the vigilance core inside it.
     *
     * ### The core
     * [coreFraction] fills the hub from nothing to full as the vigilance interval runs down, so the
     * one thing the dial could never say before — that a dead-man's check is armed, and how much of
     * it is left — is readable at a glance without a row of type or a countdown.
     *
     * It is the accent hue, like the ring around it and the seconds cursor on the rim, rather than
     * a red of its own: the palette has three hues and always-on is the same hues dimmed, so a
     * hardcoded red would neither dim with the face nor be visible at all under the red palette.
     *
     * **The core never leaves the hub.** A first attempt marked the alarm with a ring outside it,
     * which read as the indicator carrying on growing after the interval had already run out —
     * there is no such thing as more than out of time, and an indicator that keeps moving past its
     * own limit is telling you something untrue. [alarming] thickens the hub's own ring instead, at
     * the same radius.
     *
     * Nothing here animates. A strobe would be invisible in always-on, which redraws once a minute,
     * and in interactive it would pin the frame rate at 16 ms for the length of an alarm — burning
     * battery in the name of a feature whose whole point is to save it.
     *
     * The core fills only while an answer is owed. That is the whole of what it says, and the
     * reason an incident leaves it empty even though an incident is the worst of the states: the
     * words above the hub carry that one, and a full core here would say "answer now" for hours.
     *
     * @param suspended vigilance is switched on but not watching — on charge, or off the wrist.
     *   The ring goes dim to say so, because an empty core cannot: an empty core is also what a
     *   monitor that has just been answered looks like. Only ever true when vigilance was asked
     *   for, so a face that does not use it keeps the accent hub it always had.
     */
    fun drawHub(
        canvas: Canvas,
        g: Geometry,
        palette: Palette,
        coreFraction: Float,
        alarming: Boolean,
        suspended: Boolean,
    ) {
        hubPaint.color = palette.background
        canvas.drawCircle(g.cx, g.cy, g.hubRadius, hubPaint)

        if (coreFraction > 0f) {
            hubPaint.color = palette.second
            canvas.drawCircle(g.cx, g.cy, g.hubRadius * coreFraction.coerceAtMost(1f), hubPaint)
        }

        // Same accent as the seconds rim, tying the centre of the dial to its edge. Last, so a full
        // core reads as a filled hub rather than as a hub that has burst its outline.
        hubRingPaint.color = if (suspended) palette.lumeDim else palette.second
        hubRingPaint.strokeWidth = g.r * if (alarming) ALARM_RING_WIDTH else HUB_RING_WIDTH
        canvas.drawCircle(g.cx, g.cy, g.hubRadius, hubRingPaint)
    }

    private companion object {
        const val HUB_RING_WIDTH = 0.010f

        /** The alarm reads as a heavier outline, not as a bigger circle. */
        const val ALARM_RING_WIDTH = 0.022f

        /**
         * Sprite pixels per screen pixel. Two, so the blit averages four rendered pixels into each
         * one on screen and a rotated edge stays as clean as the vector it came from; the bitmaps
         * are a few tens of kilobytes at 454 px and scale with the dial, not with this constant.
         */
        const val SUPERSAMPLE = 2f

        /** Room around the shapes for their antialiased edge, in screen pixels. */
        const val SPRITE_MARGIN_PX = 2f
    }
}
