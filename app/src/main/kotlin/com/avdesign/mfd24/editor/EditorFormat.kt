// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.avdesign.mfd24.R
import com.avdesign.mfd24.data.IncidentRecord
import com.avdesign.mfd24.data.WatchShiftController
import com.avdesign.mfd24.data.WatchShiftState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Formatting for the editor's rows: durations, coordinates, booked starts, incident lines.
 *
 * Pure functions over plain values, which is what makes them readable without the screen around
 * them — and what would make them testable, were any of them more than a format string.
 */

/** `3:42` — hours and minutes left, rounded up so it never reads 0:00 while time remains. */
internal fun formatRemaining(remainingMillis: Long): String {
    val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).toInt()
    return String.format(Locale.US, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
}

/** Secondary line on the timer chip: what the shift is doing right now, if anything. */
@Composable
internal fun dutySecondaryText(
    dutyState: Int,
    watchShift: WatchShiftController,
    nowMillis: Long,
): String? = when (dutyState) {
    WatchShiftState.DUTY_ACTIVE -> stringResource(
        R.string.editor_watch_running,
        formatRemaining(watchShift.state.remainingMillis(nowMillis)),
    )

    WatchShiftState.DUTY_PENDING -> stringResource(
        R.string.editor_watch_pending,
        formatRemaining(watchShift.state.untilStartMillis(nowMillis)),
    )

    WatchShiftState.DUTY_SERVED -> stringResource(R.string.editor_watch_served)

    else -> null
}

/**
 * Degrees to hundredths, the unit the manual steppers work in, falling back to [orElse] and then
 * to zero. NaN means "never set" for both.
 */
internal fun toHundredths(degrees: Double, orElse: Double): Int {
    val value = if (degrees.isNaN()) orElse else degrees
    return if (value.isNaN()) 0 else Math.round(value * 100.0).toInt()
}

/** `55.75` / `-33.92` — two decimals, which is what a hundredth-of-a-degree stepper expresses. */
internal fun formatDegrees(hundredths: Int): String =
    String.format(Locale.US, "%.2f", hundredths / 100.0)

internal const val DAY_MILLIS = 24 * 60 * 60 * 1000L
internal const val HOUR_MILLIS = 60 * 60 * 1000L

/** An hour from now, rounded up to the next multiple of the minute step. */
internal fun defaultBookedStart(nowMillis: Long): Long =
    WatchShiftController.earliestBookableStart(nowMillis + HOUR_MILLIS)

/** The schedule steppers' floor, read off a live clock — see the comment at their rows. */
internal fun earliestBookableNow(): Long =
    WatchShiftController.earliestBookableStart(System.currentTimeMillis())

internal fun localDateTime(millis: Long): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

internal fun hourOf(millis: Long): Int = localDateTime(millis).hour

internal fun minuteOf(millis: Long): Int = localDateTime(millis).minute

internal fun formatBookedDate(millis: Long): String =
    localDateTime(millis).format(BOOKED_DATE_FORMAT).uppercase(Locale.US)

/**
 * `19 AUG 21:14Z` — an incident, in the same Zulu the dial prints it in.
 *
 * Local time would be the wrong answer twice over: the record is kept in absolute millis precisely
 * so it survives a change of zone, and the face states it in Zulu, so a log that disagreed with the
 * dial would be a second version of events.
 */
internal fun formatIncident(record: IncidentRecord): String =
    INCIDENT_FORMAT.format(Instant.ofEpochMilli(record.atMillis).atZone(ZoneOffset.UTC))

/**
 * `HR 41 · REF 58 AT 15:12Z` — the incident pulse and the reference it has to be read against.
 *
 * Null when there is nothing measured, because a row that prints dashes for an absent reading
 * invites them to be read as a low one. The reference carries its own instant: a baseline from
 * four hours ago is a different claim from one taken four minutes before the operator stopped
 * answering, and the editor is where somebody decides which they are looking at.
 */
internal fun formatIncidentPulse(record: IncidentRecord): String? {
    if (!record.hasBpm && !record.hasBaseline) return null
    val out = StringBuilder()
    if (record.hasBpm) out.append("HR ").append(record.bpm)
    if (record.hasBaseline) {
        if (out.isNotEmpty()) out.append(" · ")
        out.append("REF ").append(record.baselineBpm).append(' ')
        out.append(
            PULSE_TIME_FORMAT.format(
                Instant.ofEpochMilli(record.baselineAtMillis).atZone(ZoneOffset.UTC)
            )
        )
    }
    return out.toString()
}

/**
 * `WATCH 21 Aug 20:00–04:00Z · 8h` — the shift the log on file belongs to.
 *
 * Null when nothing is on file. The end carries the Zulu marker and the start does not, because
 * they are the same clock and marking it twice is noise on a row this narrow.
 */
internal fun formatLogShift(startMillis: Long, endMillis: Long): String? {
    if (startMillis <= 0L || endMillis <= startMillis) return null
    val start = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC)
    val end = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC)
    val minutes = (endMillis - startMillis) / 60_000L
    val length = if (minutes % 60L == 0L) {
        (minutes / 60L).toString() + "h"
    } else {
        "%dh%02d".format(minutes / 60L, minutes % 60L)
    }
    return "WATCH " + INCIDENT_FORMAT.format(start).removeSuffix("Z") +
        "–" + PULSE_TIME_FORMAT.format(end) + " · " + length
}

internal val INCIDENT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM HH:mm'Z'", Locale.UK)

/** Just the clock: the reference pulse's date is the incident's, a line above. */
internal val PULSE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm'Z'", Locale.UK)

internal val BOOKED_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
