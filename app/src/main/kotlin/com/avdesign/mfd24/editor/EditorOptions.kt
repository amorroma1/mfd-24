// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.editor

import androidx.compose.ui.graphics.Color
import com.avdesign.mfd24.R
import com.avdesign.mfd24.data.SensorSlots
import com.avdesign.mfd24.data.WatchShiftController
import com.avdesign.mfd24.style.Palette
import com.avdesign.mfd24.style.StyleSchema

/*
 * The option lists the editor offers, one per setting, and the duty-length presets.
 *
 * Data, not behaviour: each list pairs a style option id with its label resources, and the screen
 * walks them. Kept apart from the screen so a new option is a line here and nothing there.
 */

/**
 * One cell of a [SegmentedRow].
 *
 * [speechRes] exists because the visible labels are abbreviated to fit three or four to a line:
 * `CST` and `hPa` are legible on a dial and useless read aloud, and the long forms are still what
 * the platform's own style list shows. [tint] lets a cell carry its own hue, which is how the
 * palette row shows what it is offering now that there is no width left for a swatch.
 */
internal class Segment(
    val id: String,
    val labelRes: Int,
    val speechRes: Int = labelRes,
    val tint: Color? = null,
)

internal val DURATION_PRESETS = listOf(
    Segment(
        WatchShiftController.PRESET_4H, R.string.editor_duration_4h,
        R.string.editor_duration_4h_sr,
    ),
    Segment(
        WatchShiftController.PRESET_8H, R.string.editor_duration_8h,
        R.string.editor_duration_8h_sr,
    ),
    Segment(
        WatchShiftController.PRESET_12H, R.string.editor_duration_12h,
        R.string.editor_duration_12h_sr,
    ),
    Segment(
        WatchShiftController.PRESET_CUSTOM, R.string.editor_duration_custom,
        R.string.editor_duration_custom_sr,
    ),
)

/** Where the position comes from. Local to the editor: the repository holds a boolean. */
internal const val POSITION_AUTO = "auto"
internal const val POSITION_MANUAL = "manual"
internal val POSITION_OPTIONS = listOf(
    Segment(POSITION_AUTO, R.string.editor_position_auto),
    Segment(POSITION_MANUAL, R.string.editor_position_manual),
)

// "MIDNIGHT UP" is a character too wide for half a row and lost its "UP"; under a label that
// already says DIAL TOP, the direction was the redundant half anyway.
internal val DIAL_TOP_OPTIONS = listOf(
    Segment(StyleSchema.DIAL_TOP_NOON, R.string.editor_seg_dial_noon, R.string.dial_top_noon_sr),
    Segment(
        StyleSchema.DIAL_TOP_MIDNIGHT, R.string.editor_seg_dial_midnight,
        R.string.dial_top_midnight_sr,
    ),
)
internal val NADIR_OPTIONS = listOf(
    Segment(StyleSchema.NADIR_OFF, R.string.nadir_off, R.string.nadir_off_sr),
    Segment(StyleSchema.NADIR_ON, R.string.nadir_on, R.string.nadir_on_sr),
)
internal val SOLAR_OPTIONS = listOf(
    Segment(StyleSchema.SOLAR_OFF, R.string.solar_off, R.string.solar_off_sr),
    Segment(StyleSchema.SOLAR_ON, R.string.solar_on, R.string.solar_on_sr),
)
internal val LUNAR_OPTIONS = listOf(
    Segment(StyleSchema.LUNAR_OFF, R.string.lunar_off, R.string.lunar_off_sr),
    Segment(StyleSchema.LUNAR_ON, R.string.lunar_on, R.string.lunar_on_sr),
)
internal val VIGILANCE_OPTIONS = listOf(
    Segment(StyleSchema.VIGILANCE_OFF, R.string.vigilance_off, R.string.vigilance_off_sr),
    Segment(StyleSchema.VIGILANCE_ON, R.string.vigilance_on, R.string.vigilance_on_sr),
)
internal val VIGILANCE_INTERVAL_OPTIONS = listOf(
    Segment("5", R.string.vigilance_5, R.string.vigilance_5_sr),
    Segment("10", R.string.vigilance_10, R.string.vigilance_10_sr),
    Segment("15", R.string.vigilance_15, R.string.vigilance_15_sr),
)
internal val SENSOR_OPTIONS = listOf(
    Segment(SensorSlots.Kind.OFF.id, R.string.sensor_off, R.string.sensor_off_sr),
    Segment(SensorSlots.Kind.HEART_RATE.id, R.string.sensor_hr, R.string.sensor_hr_sr),
    Segment(SensorSlots.Kind.STEPS.id, R.string.sensor_steps, R.string.sensor_steps_sr),
    Segment(SensorSlots.Kind.PRESSURE.id, R.string.sensor_qfe, R.string.sensor_qfe_sr),
)

/** ABOUT's release-check switch. Ids are local: this one is not in the style schema. */
internal const val UPDATE_CHECK_ON = "on"
internal const val UPDATE_CHECK_OFF = "off"

internal const val HINTS_ON = "on"
internal const val HINTS_OFF = "off"

internal val HINT_OPTIONS = listOf(
    Segment(HINTS_ON, R.string.hints_on, R.string.hints_on_sr),
    Segment(HINTS_OFF, R.string.hints_off, R.string.hints_off_sr),
)

internal val UPDATE_CHECK_OPTIONS = listOf(
    Segment(UPDATE_CHECK_ON, R.string.update_check_on, R.string.update_check_on_sr),
    Segment(UPDATE_CHECK_OFF, R.string.update_check_off, R.string.update_check_off_sr),
)

internal val SOS_SOUND_OPTIONS = listOf(
    Segment(StyleSchema.SOS_SOUND_OFF, R.string.sos_off, R.string.sos_off_sr),
    Segment(StyleSchema.SOS_SOUND_LOW, R.string.sos_low, R.string.sos_low_sr),
    Segment(StyleSchema.SOS_SOUND_MED, R.string.sos_med, R.string.sos_med_sr),
    Segment(StyleSchema.SOS_SOUND_HIGH, R.string.sos_high, R.string.sos_high_sr),
)

internal val LOG_HR_OPTIONS = listOf(
    Segment(StyleSchema.LOG_HR_OFF, R.string.log_hr_off, R.string.log_hr_off_sr),
    Segment(StyleSchema.LOG_HR_ON, R.string.log_hr_on, R.string.log_hr_on_sr),
)

internal val VIBE_OPTIONS = listOf(
    Segment(StyleSchema.VIBE_LOW, R.string.vibe_low, R.string.vibe_low_sr),
    Segment(StyleSchema.VIBE_MED, R.string.vibe_med, R.string.vibe_med_sr),
    Segment(StyleSchema.VIBE_HIGH, R.string.vibe_high, R.string.vibe_high_sr),
)
internal val WEATHER_OPTIONS = listOf(
    Segment(StyleSchema.WEATHER_ON, R.string.weather_on, R.string.weather_on_sr),
    Segment(StyleSchema.WEATHER_OFF, R.string.weather_off, R.string.weather_off_sr),
)
internal val AMBIENT_DENSITY_OPTIONS = listOf(
    Segment(StyleSchema.AMBIENT_FULL, R.string.ambient_full, R.string.ambient_full_sr),
    Segment(StyleSchema.AMBIENT_HALF, R.string.ambient_half, R.string.ambient_half_sr),
    Segment(StyleSchema.AMBIENT_AUTO, R.string.ambient_auto, R.string.ambient_auto_sr),
)
internal val MIDNIGHT_OPTIONS = listOf(
    Segment(StyleSchema.MIDNIGHT_AS_00, R.string.midnight_00, R.string.midnight_00_sr),
    Segment(StyleSchema.MIDNIGHT_AS_24, R.string.midnight_24, R.string.midnight_24_sr),
)
// Each cell wears its own hue, which says more than a name does and survives the loss of the
// swatch that a full-width chip had room for.
internal val PALETTE_OPTIONS = listOf(
    Segment(
        Palette.ID_AMBER, R.string.editor_seg_palette_amber, R.string.palette_amber_sr,
        Color(Palette.ALERT_AMBER),
    ),
    Segment(
        Palette.ID_GREEN, R.string.editor_seg_palette_green, R.string.palette_green_sr,
        Color(Palette.PHOSPHOR_GREEN),
    ),
    Segment(
        Palette.ID_RED, R.string.editor_seg_palette_red, R.string.palette_red_sr,
        Color(Palette.NVG_RED),
    ),
)
internal val TEMP_OPTIONS = listOf(
    Segment(
        StyleSchema.TEMP_CELSIUS, R.string.editor_seg_temp_celsius, R.string.temp_celsius_sr
    ),
    Segment(
        StyleSchema.TEMP_FAHRENHEIT, R.string.editor_seg_temp_fahrenheit,
        R.string.temp_fahrenheit_sr,
    ),
)
internal val PRESSURE_OPTIONS = listOf(
    Segment(StyleSchema.PRESSURE_HPA, R.string.editor_seg_pressure_hpa, R.string.pressure_hpa_sr),
    Segment(
        StyleSchema.PRESSURE_MMHG, R.string.editor_seg_pressure_mmhg, R.string.pressure_mmhg_sr
    ),
)
