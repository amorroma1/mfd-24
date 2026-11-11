// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.watchface.editor.EditorSession
import com.avdesign.mfd24.R
import com.avdesign.mfd24.data.SensorSlots
import com.avdesign.mfd24.render.WakeTransition
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The editor's own widgets: the rows, the steppers, the section headers and the type they share.
 *
 * Split out of [WatchConfigActivity] so the screen reads as a list of settings rather than as a
 * list of settings interleaved with the machinery for drawing one. Nothing here knows which
 * setting it is showing; that is the screen's business.
 */

/** Ending a duty on purpose is a decision, not an emergency, so it is not the alarm red. */
internal val Terracotta = Color(0xFFB85C38)

/**
 * One of the two slots beside the hub.
 *
 * Selecting a reading that needs a permission asks for it there and then, rather than at some later
 * moment the user cannot connect to what they did. If it is refused the setting still takes — the
 * face shows dashes — because refusing a permission is not the same as not wanting the row, and a
 * setting that silently reverted would be the more confusing of the two.
 */
@Composable
internal fun SensorSlotSetting(
    label: String,
    selectedId: String,
    otherId: String,
    // Unused except as the recomposition nudge every permission reader in this file leans on.
    @Suppress("UNUSED_PARAMETER") grants: Int,
    onRequestPermission: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Label(label)
        SegmentedRow(
            options = SENSOR_OPTIONS,
            selectedId = selectedId,
            // A reading taken by the other slot is greyed out here. Both slots showing the same
            // number is not harmful, but it is never what was meant, and the pair of them is the
            // whole point of having two.
            isEnabled = { id -> id == SensorSlots.Kind.OFF.id || id != otherId },
            onSelect = { id ->
                onSelect(id)
                val permission = SensorSlots.Kind.ofId(id).permission
                if (permission != null && !hasPermission(context, permission)) {
                    onRequestPermission(permission)
                }
            },
        )
    }
}

/**
 * `[ – ]  8 h  [ + ]` — a free numeric choice rather than a menu of preset watch lengths.
 *
 * Both buttons repeat while held. Latitude is stepped in hundredths of a degree over a range of
 * 18000 of them, so tapping from where you are to anywhere else would be thousands of presses;
 * holding gets there in seconds.
 *
 * @param onDown called with the number of units to subtract this tick
 * @param onUp called with the number of units to add this tick
 * @param maxStep how far one tick may jump once the repeat is at full speed. One for anything
 *   counted in days or hours, where a fast repeat is already plenty; higher only for the
 *   coordinate rows, whose range is far too wide to cross a unit at a time.
 * @param onCommit run once when the press ends, for callers whose write is too expensive to do on
 *   every tick — pushing a value into the [EditorSession] twenty times a second, for instance.
 */
@Composable
internal fun StepperRow(
    value: String,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
    maxStep: Int = 1,
    onCommit: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton(stringResource(R.string.editor_step_down), maxStep, onDown, onCommit)
        Text(
            text = value,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1,
        )
        StepButton(stringResource(R.string.editor_step_up), maxStep, onUp, onCommit)
    }
}

/**
 * One round stepper button, with press-and-hold auto-repeat.
 *
 * A plain tap still applies exactly one unit on *release*, which is what `clickable` did before:
 * these buttons sit inside a `ScalingLazyColumn`, and acting on the press instead would nudge the
 * value every time a scroll happened to start on top of one. A hold past [HOLD_DELAY_MS] starts
 * repeating and accelerating until the finger comes off.
 */
@Composable
internal fun StepButton(
    label: String,
    maxStep: Int,
    onStep: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    // The gesture coroutine outlives individual recompositions, so it must not capture a stale
    // lambda: these keep it pointed at the current one.
    val step by rememberUpdatedState(onStep)
    val commit by rememberUpdatedState(onCommit)
    val haptics = LocalHapticFeedback.current

    var pressed by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }

    // Held is worth showing: without it there is no way to tell auto-repeat has engaged except by
    // watching the number, which is the thing you are trying to read.
    val background = when {
        repeating -> MaterialTheme.colors.primary
        pressed -> MaterialTheme.colors.primaryVariant
        else -> MaterialTheme.colors.surface
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(background)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pointerInput(maxStep) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            // null = the delay expired and the finger is still down; true = a
                            // normal tap; false = a parent took the gesture, i.e. a scroll, and
                            // nothing should happen at all.
                            when (withTimeoutOrNull(HOLD_DELAY_MS) { tryAwaitRelease() }) {
                                true -> {
                                    step(1)
                                    commit()
                                }

                                false -> Unit

                                null -> {
                                    repeating = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    var tick = 0
                                    while (true) {
                                        step(repeatStep(tick, maxStep))
                                        tick++
                                        val done = withTimeoutOrNull(REPEAT_TICK_MS) {
                                            tryAwaitRelease()
                                        }
                                        if (done != null) break
                                    }
                                    commit()
                                }
                            }
                        } finally {
                            pressed = false
                            repeating = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.title2,
            color = if (repeating) MaterialTheme.colors.onPrimary else MaterialTheme.colors.primary,
        )
    }
}

/** Small enough to read as punctuation on the header rather than as a second label. */
internal val CHEVRON_SIZE = 10.dp

/** The open section's outline: thin, and half a second of light. */
internal val TRACE_STROKE = 2.dp

/**
 * How long the outline takes to come up or go down.
 *
 * Deliberately the wake sweep's own figure rather than a number picked for this list: the same
 * ramp, the same duration, the same meaning — something has just become live. Read from the
 * renderer's constant so the two cannot drift apart.
 */
internal val TRACE_MILLIS = WakeTransition.DURATION_MILLIS.toInt()

/** Wait before a hold becomes a repeat: long enough that a deliberate single tap never trips it. */
internal const val HOLD_DELAY_MS = 350L

/** About twenty ticks a second once repeating. */
internal const val REPEAT_TICK_MS = 50L

/**
 * How many units one repeat tick moves, ramping up the longer the button is held and then clamped
 * to the row's own ceiling.
 *
 * The ramp is what makes a wide range crossable without making a narrow one uncontrollable: the
 * first three quarters of a second still move one unit at a time, so a short hold is precise, and
 * only a sustained one reaches full speed — about 20 degrees a second on a coordinate row.
 */
internal fun repeatStep(tick: Int, maxStep: Int): Int {
    val ramp = when {
        tick < 15 -> 1
        tick < 30 -> 2
        tick < 45 -> 5
        tick < 60 -> 10
        tick < 80 -> 25
        tick < 100 -> 50
        else -> 100
    }
    return if (ramp > maxStep) maxStep else ramp
}

/**
 * A row of equal-width chips acting as one choice, for options short enough to sit side by side.
 *
 * Four presets on one line where four full-width chips would be four screens of scrolling. The cells
 * share the width evenly so the row reads as a single control rather than as a cluster of buttons,
 * and the selected one is filled while the rest are outlined — an outline is legible on a black
 * dial without spending the accent colour four times over.
 *
 * [isEnabled] is per cell rather than per row because that is how the dependencies actually fall:
 * NADIR has nothing to shade without a position, but WEATHER can always be turned *off*, so
 * greying the whole row would trap a setting the user is allowed to change.
 */
@Composable
internal fun SegmentedRow(
    options: List<Segment>,
    selectedId: String,
    onSelect: (String) -> Unit,
    isEnabled: (String) -> Boolean = { true },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (segment in options) {
            val chosen = segment.id == selectedId
            val enabled = isEnabled(segment.id)
            val accent = segment.tint ?: MaterialTheme.colors.primary
            val description = stringResource(segment.speechRes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .then(
                        if (chosen) {
                            Modifier.background(if (enabled) accent else accent.copy(alpha = 0.35f))
                        } else {
                            Modifier.border(
                                1.dp,
                                when {
                                    !enabled -> MaterialTheme.colors.surface
                                    // Tinted cells keep their hue unselected as well, or the
                                    // palette row offers three identically amber outlines.
                                    segment.tint != null -> segment.tint
                                    else -> MaterialTheme.colors.primaryVariant
                                },
                                CircleShape,
                            )
                        }
                    )
                    .clickable(enabled = enabled) { onSelect(segment.id) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(segment.labelRes),
                    style = MaterialTheme.typography.button,
                    color = when {
                        chosen -> MaterialTheme.colors.onPrimary
                        enabled -> MaterialTheme.colors.onBackground
                        else -> MaterialTheme.colors.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A labelled [SegmentedRow], as one list item rather than two.
 *
 * One item because a `ScalingLazyColumn` scales and spaces its items independently: a label in an
 * item of its own drifts away from the control it names as the list scrolls past, and pays the
 * inter-item gap for the privilege. Folding the pair together is what let seven appearance settings
 * fit under a single DISPLAY header instead of carrying seven headers of their own.
 */
@Composable
internal fun SegmentedSetting(
    label: String,
    options: List<Segment>,
    selectedId: String,
    onSelect: (String) -> Unit,
    isEnabled: (String) -> Boolean = { true },
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Label(label)
        SegmentedRow(options, selectedId, onSelect, isEnabled)
    }
}

/**
 * A sub-heading inside a section: the section's amber, one size down.
 *
 * Amber rather than grey because these name the control below them; in grey, DURATION read as a
 * remark about the row above it. The size is what keeps the hierarchy legible — a full-size amber
 * line under DISPLAY would look like another section beginning.
 */
@Composable
internal fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 1.dp),
    )
}

/** A section heading: the largest amber line on the screen, and the only thing that starts one. */
@Composable
internal fun SectionHeader(text: String, expanded: Boolean, onClick: () -> Unit) {
    // The open section is outlined, and the outline arrives the way the face itself does: as
    // brightness.
    //
    // One section is open at a time, so which one it is is the most useful thing this list can say
    // — and the chevron alone says it in eight pixels of glyph. An earlier version drew the line
    // on, clockwise, over a second. It read as an animation rather than as a state, which is
    // exactly the wrong emphasis for a row you are trying to look past: the eye followed the
    // moving end instead of the section it was marking.
    //
    // So it ramps in brightness over `WakeTransition.DURATION_MILLIS` — the same half-second and
    // the same idea as waking from always-on, where colour is already there and only the light
    // arrives. One gesture learned once, met twice. Brightness by scaling the hue's channels
    // rather than by alpha, for the reason the renderer's palette gives: alpha composites badly
    // and shifts the hue, and this hue is the lume.
    //
    // Snapped rather than ramped on the first composition, so opening the editor does not replay
    // half a second of light for a choice nobody just made.
    val trace = remember { Animatable(if (expanded) 1f else 0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!settled) {
            settled = true
            trace.snapTo(if (expanded) 1f else 0f)
            return@LaunchedEffect
        }
        trace.animateTo(if (expanded) 1f else 0f, tween(TRACE_MILLIS, easing = LinearEasing))
    }
    val outline = MaterialTheme.colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.surface)
            .drawBehind {
                val progress = trace.value
                if (progress <= 0f) return@drawBehind
                val stroke = TRACE_STROKE.toPx()
                val inset = stroke / 2f
                val radius = (size.height - stroke) / 2f
                // The whole contour, every frame, at the brightness the ramp is currently at. A
                // stadium rather than an arc because that is the shape of the row.
                drawRoundRect(
                    color = Color(
                        red = outline.red * progress,
                        green = outline.green * progress,
                        blue = outline.blue * progress,
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = stroke),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chevron(expanded = expanded, color = MaterialTheme.colors.primary)
        Text(
            text = text,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * The open/closed marker on a [SectionHeader]: a small triangle, pointing right when the section is
 * shut and down when it is open.
 *
 * Drawn rather than set in type or taken from an icon pack. The glyphs that would do this are not
 * reliably in the system font on Wear, and `material-icons` is a dependency to carry for one
 * triangle — this project drops dependencies rather than collecting them.
 */
@Composable
internal fun Chevron(expanded: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(CHEVRON_SIZE)) {
        val w = size.width
        val h = size.height
        val path = Path()
        if (expanded) {
            path.moveTo(w * 0.15f, h * 0.32f)
            path.lineTo(w * 0.85f, h * 0.32f)
            path.lineTo(w * 0.50f, h * 0.72f)
        } else {
            path.moveTo(w * 0.32f, h * 0.15f)
            path.lineTo(w * 0.72f, h * 0.50f)
            path.lineTo(w * 0.32f, h * 0.85f)
        }
        path.close()
        drawPath(path, color)
    }
}

@Composable
internal fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/**
 * A full-width chip standing for one choice or one action.
 *
 * What is left of this after the settings moved to segmented rows: the two commands that need a
 * whole line of their own, and DONE. The colour swatch it used to carry for the palette rows went
 * with them — each segment wears its own hue now, which says the same thing in less space.
 */
@Composable
internal fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        },
        label = { Text(text = label, maxLines = 1) },
    )
}

/**
 * An explanatory line under a row — the same type as [Caption], but hideable.
 *
 * Two composables rather than a flag on one, because the distinction is the whole point: a hint
 * explains, a caption reports. `HINTS OFF` silences every hint in the list and touches no reading.
 */
@Composable
internal fun Hint(text: String, shown: Boolean) {
    if (!shown) return
    Caption(text)
}
