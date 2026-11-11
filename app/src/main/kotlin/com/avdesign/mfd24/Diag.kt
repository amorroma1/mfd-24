// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24

import android.util.Log

/**
 * Development logging, and in a release build nothing at all.
 *
 * The README says no analytics, and the system log is where that claim was quietly untrue: a
 * release build was writing the wearer's pulse, step total, position fixes, the weather it
 * fetched, which network answered and when the wrist came off — readable by anyone who holds the
 * watch and a cable, and kept by the platform for as long as its ring buffer lasts. None of that
 * is what the face is for. So every line of diagnostic logging goes through here, and here is a
 * no-op unless [BuildConfig.DEBUG]: the release APK on the watch, which is the only build that
 * counts, writes nothing.
 *
 * The one exception is the incident record. A watch that escalated to SOS, held an incident,
 * retired or cleared one, or could not write one, says so through `android.util.Log` directly,
 * because that is a fact about the wearer's safety and the log is one more place it survives.
 * `LogDisciplineTest` reads the sources and pins which lines those are; a new `Log.` call
 * anywhere else fails the build. Use this instead, and if a line is worth a release, it is worth
 * the test's list.
 *
 * Plain functions rather than lambdas, on purpose: the strings are built at the call site and
 * thrown away in a release, which costs a few bytes of dex and nothing the wearer can see, and
 * the call sites read like the `Log` calls they replaced.
 */
object Diag {
    private val ENABLED = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (ENABLED) Log.d(tag, message)
    }

    fun d(tag: String, message: String, cause: Throwable?) {
        if (ENABLED) Log.d(tag, message, cause)
    }

    fun i(tag: String, message: String) {
        if (ENABLED) Log.i(tag, message)
    }

    fun i(tag: String, message: String, cause: Throwable?) {
        if (ENABLED) Log.i(tag, message, cause)
    }

    fun w(tag: String, message: String) {
        if (ENABLED) Log.w(tag, message)
    }

    fun w(tag: String, message: String, cause: Throwable?) {
        if (ENABLED) Log.w(tag, message, cause)
    }

    fun e(tag: String, message: String) {
        if (ENABLED) Log.e(tag, message)
    }

    fun e(tag: String, message: String, cause: Throwable?) {
        if (ENABLED) Log.e(tag, message, cause)
    }
}
