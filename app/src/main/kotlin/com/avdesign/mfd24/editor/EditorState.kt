// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.wear.watchface.editor.EditorSession
import androidx.wear.watchface.style.UserStyleSetting
import com.avdesign.mfd24.style.StyleSchema

/*
 * The two pieces of EditorSession plumbing the screen relies on.
 *
 * The on-watch session exposes `userStyle` through a proxy that does not implement `collect`,
 * so the screen mirrors each selection in Compose state and pushes changes in; that is all of
 * [rememberOption] and [select].
 */

@Composable
internal fun rememberOption(
    session: EditorSession,
    settingId: UserStyleSetting.Id,
    fallback: String,
): MutableState<String> = remember(settingId) {
    mutableStateOf(StyleSchema.optionId(session.userStyle.value, settingId, fallback))
}

/**
 * Applies a list option to the live style. The editor session persists it and the running watch
 * face redraws immediately.
 */
internal fun EditorSession.select(settingId: UserStyleSetting.Id, optionId: String) {
    val setting = userStyleSchema[settingId] ?: return
    val option = setting.options.firstOrNull { it.id.toString() == optionId } ?: return
    val mutable = userStyle.value.toMutableUserStyle()
    mutable[setting] = option
    userStyle.value = mutable.toUserStyle()
}
