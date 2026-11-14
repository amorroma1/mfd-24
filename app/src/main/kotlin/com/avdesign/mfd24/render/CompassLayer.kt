// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.avdesign.mfd24.style.Palette

/**
 * The meridian's two cardinal ticks redrawn as a compass needle, over the cached scale: red at the
 * north end, white at the south, and pointed while [CompassTicks.pointed] says the dial can be
 * aimed. Interactive only — the white end carries blue, and nothing on this face that carries
 * blue is ever held on the screen overnight.
 *
 * Both shapes are built once per layout pointing straight up and drawn by rotating the canvas, so
 * a frame allocates nothing.
 */
class CompassLayer {

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val needle = Path()
    private var builtForRadius = Float.NaN

    fun draw(
        canvas: Canvas,
        g: Geometry,
        midnightAngleDeg: Float,
        northAtMidnight: Boolean,
        pointed: Boolean,
    ) {
        if (builtForRadius != g.r) {
            buildNeedle(g)
            builtForRadius = g.r
        }
        val northAngle = if (northAtMidnight) midnightAngleDeg else midnightAngleDeg + 180f
        drawEnd(canvas, g, northAngle, Palette.COMPASS_NORTH, pointed)
        drawEnd(canvas, g, northAngle + 180f, Palette.COMPASS_SOUTH, pointed)
    }

    private fun drawEnd(canvas: Canvas, g: Geometry, angleDeg: Float, color: Int, pointed: Boolean) {
        canvas.save()
        canvas.rotate(angleDeg, g.cx, g.cy)
        if (pointed) {
            needlePaint.color = color
            canvas.drawPath(needle, needlePaint)
        } else {
            tickPaint.color = color
            tickPaint.strokeWidth = g.cardinalTickWidth
            canvas.drawLine(
                g.cx, g.cy - g.r * Geometry.TICK_HOUR_INNER,
                g.cx, g.cy - g.r * Geometry.TICK_CARDINAL_OUTER,
                tickPaint,
            )
        }
        canvas.restore()
    }

    /**
     * A blade over exactly the tick's own length, its base [NEEDLE_BASE] tick widths across and
     * its point at the rim end: wide enough to read as a point at 454 px, short enough to stay off
     * the numerals.
     */
    private fun buildNeedle(g: Geometry) {
        val half = g.cardinalTickWidth * NEEDLE_BASE / 2f
        val inner = g.cy - g.r * Geometry.TICK_HOUR_INNER
        val outer = g.cy - g.r * Geometry.TICK_CARDINAL_OUTER
        needle.reset()
        needle.moveTo(g.cx - half, inner)
        needle.lineTo(g.cx, outer)
        needle.lineTo(g.cx + half, inner)
        needle.close()
    }

    private companion object {
        const val NEEDLE_BASE = 3.2f
    }
}
