// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.avdesign.mfd24.data.SensorSlots

/*
 * Which runtime permission a setting needs, and whether it is held.
 *
 * A watch face cannot raise a permission dialog, so the editor is where every grant is asked
 * for; these are the rules for when. See the permission table in the README for what each one
 * buys and what its absence costs.
 */

internal fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** [hasPermission] with the [grants] nudge, for readers inside the composition. */
internal fun heldPermission(
    context: Context,
    permission: String,
    @Suppress("UNUSED_PARAMETER") grants: Int,
): Boolean = hasPermission(context, permission)

/**
 * The permission enabling vigilance still lacks on this platform, or null when it has them all.
 *
 * Ordered by how badly the miss hurts: on API 34 the `health` foreground service will not start
 * at all without ACTIVITY_RECOGNITION (or another sensor permission), which is a dead-man's
 * switch that never arms; on API 33 the service runs but its ongoing notification is invisible.
 * Neither exists on the API 30 watches this face was built on.
 */
internal fun vigilancePermissionToRequest(
    context: Context,
    @Suppress("UNUSED_PARAMETER") grants: Int,
): String? = when {
    android.os.Build.VERSION.SDK_INT >= 34 &&
        !hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ->
        Manifest.permission.ACTIVITY_RECOGNITION

    android.os.Build.VERSION.SDK_INT >= 33 &&
        !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS) ->
        Manifest.permission.POST_NOTIFICATIONS

    else -> null
}

/**
 * Whether either slot is set to a reading whose permission is not held.
 *
 * [grants] is unused except to make this recompose after the dialog closes: what is actually asked
 * is the package manager, which a Compose snapshot has no way of observing.
 */
internal fun missingSensorPermission(
    context: Context,
    left: String,
    right: String,
    @Suppress("UNUSED_PARAMETER") grants: Int,
): Boolean = listOf(left, right).any { id ->
    val permission = SensorSlots.Kind.ofId(id).permission
    permission != null && !hasPermission(context, permission)
}

/** Whether the pulse sensor may be read at all — the heart-rate log row is offered only then. */
internal fun bodySensorsGranted(context: Context, grants: Int): Boolean {
    @Suppress("UNUSED_EXPRESSION") grants // recomposition key: a grant arrives without a state change
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
        PackageManager.PERMISSION_GRANTED
}
