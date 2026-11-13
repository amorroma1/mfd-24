// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.render

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

/**
 * Vector pictograms for the site-lock readout.
 *
 * Five silhouettes for the site lock, plus a heart and a walking figure for the optional sensor
 * slots beside the hub, where a pictogram says what two or three letters had to spell.
 *
 * The site set, chosen by the site's type and flags: an airliner or a fast jet for a civil or
 * military airfield, a helicopter for a helipad, an anchor for a port and a rocket for a
 * spaceport. Only the airfield pair differs by shape; everywhere else the owner is carried by
 * colour alone — military sites are drawn in the palette's accent — because at 22 px on a wrist
 * a second ship or a second helicopter did not survive as a different thing, measured on the
 * watch at eight real sites. The rocket alone has a second colour of its own: the exhaust under
 * it, in a fixed orange, because a bare rocket and a delta-wing fighter are the same pointed
 * body at 22 px.
 *
 * These are drawn as paths rather than as the `✈ ⚓ 🚀` code points on purpose — emoji coverage in
 * the Wear OS system fonts is unreliable, and colour emoji would ignore the lume palette and wreck
 * the ambient pixel budget.
 *
 * All of them are authored in a 100 x 100 box and scaled into place once per layout, and all of
 * them are built from clockwise sub-paths only: under the non-zero winding rule that makes the
 * result the union of the parts, which is how the anchor gets a stock and the helicopter a rotor
 * without a boolean op. A hole is a counter-clockwise sub-path wholly inside a clockwise one (the
 * anchor's ring); only the moon, whose cutter reaches past the disc, needs a real subtraction.
 *
 * ### Sized for 22 px
 * The glyph box is `0.096 r`, which is about 22 device pixels on a 454 px dial — small enough that
 * a detail thinner than four units of the authoring box disappears. The pairs that have to be told
 * apart are therefore separated by where the *mass* sits rather than by fine detail: the fast jet
 * by its delta planform against the airliner's straight wing. Every stroke in the set is at least
 * eight units wide, two device pixels, and the parts that must read as separate — rotor and
 * cabin, cabin and skid — keep eight units of air between them.
 */
object Glyphs {

    private val scratch = Matrix()

    // The unit paths never change, so they are authored once. Rebuilding them on every call meant
    // allocating a Path — and, for the moon, running a boolean subtraction — each time the layout
    // or the planet mode changed.
    private val aircraft: Path by lazy(LazyThreadSafetyMode.NONE) { aircraft() }
    private val fighter: Path by lazy(LazyThreadSafetyMode.NONE) { fighter() }
    private val helicopter: Path by lazy(LazyThreadSafetyMode.NONE) { helicopter() }
    private val anchor: Path by lazy(LazyThreadSafetyMode.NONE) { anchor() }
    private val rocket: Path by lazy(LazyThreadSafetyMode.NONE) { rocket() }
    private val rocketFlame: Path by lazy(LazyThreadSafetyMode.NONE) { rocketFlame() }
    private val heart: Path by lazy(LazyThreadSafetyMode.NONE) { heart() }
    private val pedestrian: Path by lazy(LazyThreadSafetyMode.NONE) { pedestrian() }
    private val earthSymbol: Path by lazy(LazyThreadSafetyMode.NONE) { earthSymbol() }
    private val marsSymbol: Path by lazy(LazyThreadSafetyMode.NONE) { marsSymbol() }
    private val moonSymbol: Path by lazy(LazyThreadSafetyMode.NONE) { moonSymbol() }

    fun buildAircraft(box: RectF, out: Path) = build(aircraft, box, out)

    fun buildFighter(box: RectF, out: Path) = build(fighter, box, out)

    fun buildHelicopter(box: RectF, out: Path) = build(helicopter, box, out)

    fun buildAnchor(box: RectF, out: Path) = build(anchor, box, out)

    fun buildRocket(box: RectF, out: Path) = build(rocket, box, out)
    fun buildRocketFlame(box: RectF, out: Path) = build(rocketFlame, box, out)

    fun buildHeart(box: RectF, out: Path) = build(heart, box, out)

    fun buildPedestrian(box: RectF, out: Path) = build(pedestrian, box, out)

    fun buildEarthSymbol(box: RectF, out: Path) = build(earthSymbol, box, out)

    fun buildMarsSymbol(box: RectF, out: Path) = build(marsSymbol, box, out)

    /**
     * The crescent is authored as a half-rim sliver, so unlike the other glyphs it does not fill the
     * unit box: mapped through it, the moon would come out a quarter of the width of the others. It
     * is fitted from its own bounds instead, stretched to the target box. The horizontal stretch is
     * deliberate — a sickle drawn from true circles is either fat or hair-thin, and stretching a
     * thin one is what makes it bold and unmistakable at 24 px.
     */
    fun buildMoonSymbol(box: RectF, out: Path) {
        scratch.reset()
        scratch.setRectToRect(MOON_BOUNDS, box, Matrix.ScaleToFit.FILL)
        out.reset()
        moonSymbol.transform(scratch, out)
    }

    private fun build(unit: Path, box: RectF, out: Path) {
        scratch.reset()
        scratch.setRectToRect(UNIT_BOX, box, Matrix.ScaleToFit.CENTER)
        out.reset()
        unit.transform(scratch, out)
    }

    /** Airliner, plan view, nose up: straight wing, long fuselage, swept tailplane. */
    private fun aircraft(): Path = Path().apply {
        moveTo(50f, 2f)
        cubicTo(56f, 10f, 58f, 22f, 58f, 36f)
        lineTo(96f, 60f)
        lineTo(96f, 70f)
        lineTo(58f, 58f)
        lineTo(58f, 78f)
        lineTo(72f, 90f)
        lineTo(72f, 97f)
        lineTo(50f, 90f)
        lineTo(28f, 97f)
        lineTo(28f, 90f)
        lineTo(42f, 78f)
        lineTo(42f, 58f)
        lineTo(4f, 70f)
        lineTo(4f, 60f)
        lineTo(42f, 36f)
        cubicTo(42f, 22f, 44f, 10f, 50f, 2f)
        close()
    }

    /** Fast jet, plan view, nose up: slim fuselage, delta wing, canted tails. */
    private fun fighter(): Path = Path().apply {
        moveTo(50f, 2f)
        lineTo(57f, 22f)
        lineTo(58f, 52f)
        lineTo(58f, 84f)
        lineTo(42f, 84f)
        lineTo(42f, 52f)
        lineTo(43f, 22f)
        close()
        moveTo(50f, 26f)
        lineTo(95f, 86f)
        lineTo(95f, 94f)
        lineTo(50f, 80f)
        lineTo(5f, 94f)
        lineTo(5f, 86f)
        close()
        moveTo(50f, 88f)
        lineTo(38f, 99f)
        lineTo(30f, 96f)
        lineTo(42f, 80f)
        close()
        moveTo(58f, 80f)
        lineTo(70f, 96f)
        lineTo(62f, 99f)
        lineTo(50f, 88f)
        close()
    }

    /**
     * Helipad, civil or military: a helicopter in profile, nose right — rotor seen edge-on as a
     * bar, a bubble cabin, the tail boom running left to a fin, skids under it. Drawn once for
     * both owners; the colour says whose pad it is. The silhouette is twice as wide as it is
     * tall and sits centred in the square box, which is the price of keeping the proportions a
     * reader knows: stretched to fill the box it became a blob with a bar over it.
     */
    private fun helicopter(): Path = Path().apply {
        moveTo(6f, 22f)
        lineTo(96f, 22f)
        lineTo(96f, 31f)
        lineTo(6f, 31f)
        close()
        moveTo(60f, 31f)
        lineTo(68f, 31f)
        lineTo(68f, 40f)
        lineTo(60f, 40f)
        close()
        addOval(44f, 38f, 86f, 70f, Path.Direction.CW)
        moveTo(64f, 42f)
        lineTo(98f, 58f)
        lineTo(94f, 68f)
        lineTo(64f, 70f)
        close()
        moveTo(50f, 58f)
        lineTo(4f, 52f)
        lineTo(4f, 42f)
        lineTo(50f, 46f)
        close()
        moveTo(0f, 34f)
        lineTo(9f, 34f)
        lineTo(9f, 60f)
        lineTo(0f, 60f)
        close()
        moveTo(36f, 76f)
        lineTo(92f, 76f)
        lineTo(92f, 83f)
        lineTo(36f, 83f)
        close()
        moveTo(54f, 68f)
        lineTo(60f, 68f)
        lineTo(60f, 76f)
        lineTo(54f, 76f)
        close()
        moveTo(74f, 68f)
        lineTo(80f, 68f)
        lineTo(80f, 76f)
        lineTo(74f, 76f)
        close()
    }

    /**
     * Port, civil or naval: an anchor — ring, shank, stock and arms with flukes. Drawn once for
     * both; colour says who owns the harbour. A ship in profile was tried first, twice, and at
     * 22 px on the wrist neither a tanker nor a patrol ship said more than "bar with a lump";
     * the anchor is the one harbour symbol every chart already uses. The ring's hole is a
     * counter-clockwise oval wholly inside the clockwise disc, so non-zero winding cuts it
     * cleanly — unlike the moon, nothing here reaches past its outer edge.
     */
    private fun anchor(): Path = Path().apply {
        addOval(36f, 0f, 64f, 26f, Path.Direction.CW)
        addOval(44f, 8f, 56f, 18f, Path.Direction.CCW)
        moveTo(43f, 24f)
        lineTo(57f, 24f)
        lineTo(57f, 90f)
        lineTo(43f, 90f)
        close()
        moveTo(22f, 32f)
        lineTo(78f, 32f)
        lineTo(78f, 43f)
        lineTo(22f, 43f)
        close()
        moveTo(91.7f, 67.4f)
        lineTo(89.7f, 71.3f)
        lineTo(87.3f, 74.9f)
        lineTo(84.6f, 78.3f)
        lineTo(81.6f, 81.5f)
        lineTo(78.3f, 84.3f)
        lineTo(74.7f, 86.8f)
        lineTo(70.9f, 89f)
        lineTo(67f, 90.7f)
        lineTo(62.9f, 92.2f)
        lineTo(58.6f, 93.2f)
        lineTo(54.3f, 93.8f)
        lineTo(50f, 94f)
        lineTo(45.7f, 93.8f)
        lineTo(41.4f, 93.2f)
        lineTo(37.1f, 92.2f)
        lineTo(33f, 90.7f)
        lineTo(29.1f, 89f)
        lineTo(25.3f, 86.8f)
        lineTo(21.7f, 84.3f)
        lineTo(18.4f, 81.5f)
        lineTo(15.4f, 78.3f)
        lineTo(12.7f, 74.9f)
        lineTo(10.3f, 71.3f)
        lineTo(8.3f, 67.4f)
        lineTo(21f, 61.5f)
        lineTo(22.4f, 64.2f)
        lineTo(24.1f, 66.7f)
        lineTo(25.9f, 69.1f)
        lineTo(28f, 71.3f)
        lineTo(30.3f, 73.2f)
        lineTo(32.8f, 75f)
        lineTo(35.4f, 76.5f)
        lineTo(38.2f, 77.7f)
        lineTo(41f, 78.7f)
        lineTo(44f, 79.4f)
        lineTo(47f, 79.9f)
        lineTo(50f, 80f)
        lineTo(53f, 79.9f)
        lineTo(56f, 79.4f)
        lineTo(59f, 78.7f)
        lineTo(61.8f, 77.7f)
        lineTo(64.6f, 76.5f)
        lineTo(67.2f, 75f)
        lineTo(69.7f, 73.2f)
        lineTo(72f, 71.3f)
        lineTo(74.1f, 69.1f)
        lineTo(75.9f, 66.7f)
        lineTo(77.6f, 64.2f)
        lineTo(79f, 61.5f)
        close()
        moveTo(4f, 58f)
        lineTo(18f, 64f)
        lineTo(10f, 80f)
        close()
        moveTo(90f, 80f)
        lineTo(82f, 64f)
        lineTo(96f, 58f)
        close()
    }

    /**
     * Heart, for the pulse slot.
     *
     * One closed outline rather than the usual union of blocks, because a heart is the one shape
     * here everybody already knows: at slot size the reader is matching a silhouette from memory,
     * not resolving detail, and anything that is not immediately heart-shaped is worse than the
     * two letters it replaced. The lobes are deliberately wide and the point short — a tall narrow
     * heart turns into a spade below about fifteen pixels.
     */
    private fun heart(): Path = Path().apply {
        moveTo(50f, 92f)
        cubicTo(20f, 68f, 3f, 53f, 3f, 34f)
        cubicTo(3f, 17f, 18f, 8f, 31f, 8f)
        cubicTo(41f, 8f, 47f, 15f, 50f, 22f)
        cubicTo(53f, 15f, 59f, 8f, 69f, 8f)
        cubicTo(82f, 8f, 97f, 17f, 97f, 34f)
        cubicTo(97f, 53f, 80f, 68f, 50f, 92f)
        close()
    }

    /**
     * Walking figure, for the step slot.
     *
     * Clockwise blocks again, so the union gives the figure without a boolean op. The stride is
     * exaggerated well past a real one for the same reason the fast jet is: at this size the
     * silhouette has to be read from where the mass sits, and a figure standing straight is a
     * vertical bar.
     */
    private fun pedestrian(): Path = Path().apply {
        addCircle(52f, 14f, 13f, Path.Direction.CW)
        // Torso.
        moveTo(42f, 28f)
        lineTo(62f, 28f)
        lineTo(60f, 58f)
        lineTo(40f, 58f)
        close()
        // Leading leg, thrown forward.
        moveTo(49f, 52f)
        lineTo(64f, 52f)
        lineTo(82f, 90f)
        lineTo(67f, 97f)
        close()
        // Trailing leg.
        moveTo(37f, 52f)
        lineTo(53f, 52f)
        lineTo(38f, 93f)
        lineTo(23f, 88f)
        close()
        // Leading arm, swung back to balance the leg.
        moveTo(24f, 32f)
        lineTo(40f, 27f)
        lineTo(48f, 52f)
        lineTo(33f, 57f)
        close()
    }

    /**
     * Spaceport: the vehicle alone — conical nose, body, two fins, a nozzle — and nothing of the
     * pad. The exhaust is a separate path ([rocketFlame]) because it is drawn in its own colour:
     * at 22 px a bare rocket and a delta-wing fighter are both a pointed body with fins, and the
     * orange under the nozzle is what tells them apart.
     */
    private fun rocket(): Path = Path().apply {
        moveTo(50f, 2f)
        lineTo(62f, 22f)
        lineTo(62f, 74f)
        lineTo(38f, 74f)
        lineTo(38f, 22f)
        close()
        moveTo(38f, 50f)
        lineTo(38f, 84f)
        lineTo(24f, 92f)
        lineTo(24f, 80f)
        close()
        moveTo(62f, 50f)
        lineTo(76f, 80f)
        lineTo(76f, 92f)
        lineTo(62f, 84f)
        close()
        moveTo(42f, 74f)
        lineTo(58f, 74f)
        lineTo(58f, 80f)
        lineTo(42f, 80f)
        close()
    }

    /** The rocket's exhaust, drawn under [rocket] in a fixed orange. Same box, same scale. */
    private fun rocketFlame(): Path = Path().apply {
        moveTo(40f, 82f)
        lineTo(60f, 82f)
        lineTo(50f, 98f)
        close()
    }

    /**
     * Earth ♁ — a ringed circle quartered by a cross.
     *
     * The rings rely on winding: an outer clockwise circle plus an inner counter-clockwise one
     * cancel to a hollow band, and the bars drawn over the hole fill it back in.
     */
    private fun earthSymbol(): Path = Path().apply {
        addCircle(50f, 50f, 40f, Path.Direction.CW)
        addCircle(50f, 50f, 31f, Path.Direction.CCW)
        addRect(45f, 6f, 55f, 94f, Path.Direction.CW)
        addRect(6f, 45f, 94f, 55f, Path.Direction.CW)
    }

    /** Mars ♂ — a ringed circle with an arrow off the upper right. */
    private fun marsSymbol(): Path = Path().apply {
        addCircle(38f, 62f, 32f, Path.Direction.CW)
        addCircle(38f, 62f, 23f, Path.Direction.CCW)
        // Shaft, a rectangle rotated onto the 45 degree diagonal.
        moveTo(52.8f, 44.8f)
        lineTo(59.2f, 51.2f)
        lineTo(91.2f, 19.2f)
        lineTo(84.8f, 12.8f)
        close()
        // Arrowhead.
        moveTo(96f, 4f)
        lineTo(64f, 9f)
        lineTo(91f, 36f)
        close()
    }

    /**
     * Moon ☾ — a thin sickle with its horns to the right.
     *
     * A disc cut by a larger circle offset to the right leaves the left limb. Two numbers set the
     * shape: the cut circle's radius decides where the horns land, and the leftover thickness works
     * out as `rOuter - rCut + distance`. Here `rCut = sqrt(44² + 12²)` puts the horns exactly on
     * the vertical diameter — a true half-rim crescent rather than the near-closed ring a smaller
     * cutter produces — and the sliver comes out about 10 units of an 88-unit disc.
     */
    private fun moonSymbol(): Path {
        val disc = Path().apply { addCircle(52f, 50f, 44f, Path.Direction.CW) }
        val cutter = Path().apply { addCircle(64f, 50f, 45.6f, Path.Direction.CW) }
        // A real boolean subtraction, not a counter-clockwise sub-path. The cutter is larger than
        // the disc and reaches past it, and under the non-zero winding rule that overhang has
        // winding -1, which still counts as filled — it came out as a second crescent facing the
        // other way, so the pair read as a cat's eye rather than as a moon.
        disc.op(cutter, Path.Op.DIFFERENCE)
        return disc
    }

    /** Bounds of the sliver [moonSymbol] actually occupies: the left half of the disc. */
    private val MOON_BOUNDS = RectF(8f, 6f, 52f, 94f)

    private val UNIT_BOX = RectF(0f, 0f, 100f, 100f)
}
