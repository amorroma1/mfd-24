// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A release build writes nothing to the system log but the incident record.
 *
 * The README promises no analytics, and the system log is readable by anyone with the watch and
 * a cable, so every diagnostic line goes through [Diag], which is silent outside debug builds.
 * The lines allowed to reach `android.util.Log` directly are listed here, one per fact about an
 * incident; a `Log.` call anywhere else fails this test rather than shipping. Read off the
 * sources rather than the bytecode so the list is reviewed where the line is written.
 */
class LogDisciplineTest {

    private val allowed = listOf(
        "no response; sounding SOS",
        "no answer after \${SosSchedule.BURSTS} bursts",
        "incident on file; holding",
        "incident cleared",
        "incident predates this watch; retiring it",
        "new watch; incident log cleared",
        "Could not record the incident",
        "Could not extend the incident log",
    )

    private val call = Regex("""\bLog\.[dviwe]\(""")

    @Test
    fun `only the incident record reaches the system log`() {
        val root = File("src/main/kotlin")
        assertTrue("sources not found at ${root.absolutePath}", root.isDirectory)
        val offenders = mutableListOf<String>()
        var kept = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Diag.kt" }
            .forEach { file ->
                val text = file.readText()
                for (match in call.findAll(text)) {
                    val window = text.substring(match.range.first, minOf(text.length, match.range.first + 250))
                    if (allowed.any { it in window }) {
                        kept++
                    } else {
                        val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                        offenders += "${file.path}:$line"
                    }
                }
            }
        assertTrue(
            "android.util.Log used outside the incident record; use Diag:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
        assertTrue("every allowed line should still exist: found $kept of ${allowed.size}", kept == allowed.size)
    }
}
