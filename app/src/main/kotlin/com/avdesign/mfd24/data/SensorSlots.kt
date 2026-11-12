// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.avdesign.mfd24.Diag
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The two optional readouts either side of the hub, and the sensors behind them.
 *
 * ### Why they run only while the screen is on
 * The heart-rate sensor is an optical one: reading it means lighting an LED against the wrist and
 * keeping it lit. Left registered it would run for the whole watch, which is the sort of standing
 * cost the rest of this face is built to avoid — and it would buy nothing, because a number nobody
 * is looking at is not information. So the registration follows the watch face's own visibility:
 * the sensors come up when the screen does and go down with it. What you read is therefore a
 * reading taken while you were reading it.
 *
 * The consequence to know about is that a value takes a moment to arrive. Optical heart rate needs
 * several seconds to lock from cold, so the readout shows `--` first and fills in. That is honest,
 * and better than showing a number from ten minutes ago as though it were now.
 *
 * ### Why always-on is a window a minute, and not nothing
 * Always-on used to keep the sensor off and the slot's last number on, which was exactly the
 * number from ten minutes ago presented as now: the figure read when the wrist was last raised
 * stood on the dial for as long as the screen stayed dim, and the user saw it standing. The
 * ambient face redraws once a minute, and that minute tick is the one clock a watch face has in
 * Doze — an inexact alarm there is served every nine minutes — so [ambientMinute] is driven from
 * the renderer's ambient frame and opens a *window*: a partial wake lock, because the pulse
 * sensor on this watch is non-wakeUp and its events do not reach a suspended processor; the LED
 * until the first plausible reading or [WINDOW_MILLIS], whichever is first; then a redraw with the
 * figure. Two rules round it: a reading older than [STALE_MILLIS] is dashes, never a stale
 * number, and no window opens off the wrist, where the LED would light against a table every
 * minute all night for nothing. The wrist detector can be wrong about a table — it was, for a
 * whole night — so three empty windows in a row slow the cadence to [BACKOFF_MILLIS] until a
 * lock, a screen-on or the detector's own *worn* edge says a wrist is back.
 *
 * The window length was measured, not reasoned. Two and a half days of wear at thirty seconds:
 * 747 windows, one a minute of always-on as intended, and 92 % of them ran to the timeout with
 * nothing — a wrist that is walking or typing gives this sensor no lock in thirty seconds, and
 * the LED burned the full thirty each time, 5 h 50 m in 62. On a lowered, still wrist every
 * window locked in seven to eleven seconds, and of the windows that locked at all, 41 of 48 did
 * so inside fifteen. So fifteen: nearly every lock that is going to come has come, and the
 * minutes that were never going to lock cost half what they did. Dashes on the move are the
 * honest answer — a wrist in motion gives this sensor nothing, whoever is asking.
 *
 * ### Where the step count comes from
 * From the platform, when the platform will give it: [DailySteps] subscribes to Health Services'
 * own `DAILY_STEPS`, which is the figure the rest of the watch shows and is accumulated whether or
 * not this face was installed. That matters for the first day of every new wearer — walk four
 * thousand steps, install the face, and a self-counted row would say `0`.
 *
 * That subscription is the one thing here that does *not* follow the screen: it stands for as
 * long as the slot is on. See [followPlatformSteps] for what renewing it at every glance cost.
 *
 * The counter path below is the fallback for builds with no Health Services.
 * [Sensor.TYPE_STEP_COUNTER] counts from the last reboot, so steps for the day need the counter's
 * value at the day's start subtracted, and that has to be kept somewhere a reboot cannot lose — so
 * it lives beside the shift and the incident log in device-protected storage. A reboot resets the
 * hardware counter without resetting the day, so the baseline is dropped whenever the counter comes
 * back *below* it: see [stepsToday].
 */
class SensorSlots(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(SensorManager::class.java)
    private val state = TelemetryRepository.get(appContext).state

    private val prefs: SharedPreferences = appContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Where an incident's reference pulse goes; see [noteReference]. */
    private val vigilanceStore by lazy { VigilanceStore(appContext) }

    /** `elapsedRealtime` of the last reference written, so the interactive stream is not written at 15 Hz. */
    private var referenceWrittenAt = NEVER

    /** The kinds currently asked for, as [Kind] values. Both [Kind.OFF] means nothing is running. */
    private var left = Kind.OFF
    private var right = Kind.OFF
    private var listening = false

    private val dailySteps = DailySteps(appContext)

    /** True while the platform is supplying the step total, so the raw counter is left alone. */
    private var stepsFromPlatform = false

    /**
     * True once the platform has actually delivered a figure — not merely accepted the
     * subscription.
     *
     * The two are different, and the difference decides which number the dial shows. A provider
     * that binds and then says nothing needs the raw counter behind it; a provider that answers
     * owns the row, and the counter must not be allowed to overwrite it — they measure different
     * things, and whichever wrote last would win.
     */
    private var platformDelivered = false

    private val handler = Handler(Looper.getMainLooper())

    /** Set by [stepsToday] when it had to create today's zero point. See [onSensorChanged]. */
    private var baselineIsNew = false

    // --- The always-on pulse window: see the class KDoc and [ambientMinute]. ---

    private val heartSensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    /** On-change, wakeUp, no permission, and already powered for four other clients on this watch. */
    private val offBodySensor: Sensor? =
        manager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

    private val wakeLock: PowerManager.WakeLock? = appContext
        .getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

    /**
     * Called on the main thread when a window changes what the slot shows — a reading landed, or
     * the old one expired — so the face can redraw without waiting for the next minute.
     */
    var onAmbientReading: (() -> Unit)? = null

    /** Defaults to worn, so hardware without the detector keeps its windows. */
    private var onBody = true
    private var followingBody = false
    private var windowOpen = false

    /** `elapsedRealtime` of the last pulse put on the dial, by either path; [NEVER] until one is. */
    private var pulseReadAt = NEVER

    /**
     * Windows that have run to their timeout since the last lock, screen-on or return to the
     * wrist — the back-off counter, see [windowDue]. Measured 2026-11-12: a watch left face
     * down on a table reported *worn* all night, and 600 windows lit the LED against the table.
     */
    private var missedWindows = 0

    /** `elapsedRealtime` the last window opened at; [NEVER] until one has. */
    private var windowOpenedAt = NEVER

    private val windowTimeout = Runnable {
        missedWindows++
        closeWindow("no lock inside the window")
    }
    private val ambientTick = Runnable { ambientMinuteNow() }

    /** The window's own listener, so closing it cannot touch the interactive registration. */
    private val windowListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor?.type != Sensor.TYPE_HEART_RATE) return
            // The first plausible reading ends the window: the recorder keeps the last of a fixed
            // window because it files a record, and this is a glance. The bounds are the log's,
            // wide enough to be a filter against the sensor rather than a judgement of the wearer.
            val bpm = event.values[0].toInt()
            if (!IncidentLog.plausibleBpm(bpm)) return
            state.heartRate = bpm
            pulseReadAt = SystemClock.elapsedRealtime()
            missedWindows = 0
            noteReference(bpm)
            closeWindow("locked")
            onAmbientReading?.invoke()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Separate for the same reason: `stop()` unregisters the main listener wholesale. */
    private val bodyListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor?.type != Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) return
            val worn = event.values[0] > 0.5f
            if (worn == onBody) return
            onBody = worn
            Diag.d(TAG, if (worn) "back on the wrist" else "off the wrist")
            // A wrist that has just come back is a new chance; the back-off was about a table.
            if (worn) missedWindows = 0 else closeWindow("off the wrist")
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * Falls back to the raw counter when the platform accepted the subscription and then said
     * nothing.
     *
     * A passive listener reports on the platform's own schedule, so dashes for a few seconds are
     * normal and honest. Dashes for ever are not: a Health Services build that takes the
     * subscription and never delivers would leave this row permanently empty, which reads as the
     * feature being broken. So the counter is brought up alongside after
     * [PLATFORM_GRACE_MILLIS] if nothing has arrived. Whichever reports next wins, and the
     * platform's figure is the one that keeps winning — it arrives repeatedly.
     */
    private val stepsFallback = Runnable {
        if (state.stepsToday != NO_READING) return@Runnable
        val sensor = manager?.getDefaultSensor(Kind.STEPS.type) ?: return@Runnable
        Diag.d(TAG, "no daily total yet; bringing up the step counter as well")
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    /**
     * Which kinds to feed, and whether to be running at all.
     *
     * Called on a style change and on every visibility change, so it has to be cheap and idempotent
     * when nothing has moved. The platform's step subscription follows the style alone, through
     * [followPlatformSteps]; only the raw sensors follow the screen.
     */
    fun configure(leftKind: Int, rightKind: Int, visible: Boolean) {
        val wantLeft = Kind.of(leftKind)
        val wantRight = Kind.of(rightKind)
        val changed = wantLeft != left || wantRight != right
        left = wantLeft
        right = wantRight
        followPlatformSteps((left == Kind.STEPS || right == Kind.STEPS) && permitted(Kind.STEPS))
        followBody((left == Kind.HEART_RATE || right == Kind.HEART_RATE) && permitted(Kind.HEART_RATE))
        val shouldListen = visible && (left != Kind.OFF || right != Kind.OFF)
        if (shouldListen && (!listening || changed)) {
            // The screen is on: the continuous registration takes over from any window.
            closeWindow("screen on")
            stop()
            start()
        } else if (!shouldListen && listening) {
            stop()
        }
    }

    /**
     * One always-on minute has been drawn. Posted rather than acted on, because the caller is
     * `render()`, which allocates nothing and should not be making binder calls to the sensor
     * service and the power manager in the middle of a frame.
     */
    fun ambientMinute() {
        handler.post(ambientTick)
    }

    private fun ambientMinuteNow() {
        if (left != Kind.HEART_RATE && right != Kind.HEART_RATE) return
        val now = SystemClock.elapsedRealtime()
        // The expiry first, and whether or not a window follows: dashes beat a number that has
        // stopped being true, and the face is asked to redraw so they appear now rather than at
        // the next minute.
        if (state.heartRate != NO_READING && stale(now, pulseReadAt)) {
            state.heartRate = NO_READING
            onAmbientReading?.invoke()
        }
        if (!permitted(Kind.HEART_RATE)) return
        val sensor = heartSensor ?: return
        if (!windowDue(now, pulseReadAt, onBody, listening, windowOpen, missedWindows, windowOpenedAt)) {
            return
        }
        runCatching { wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
        val registered = runCatching {
            manager?.registerListener(windowListener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        }.getOrDefault(false)
        if (!registered) {
            releaseWakeLock()
            return
        }
        windowOpen = true
        windowOpenedAt = now
        handler.postDelayed(windowTimeout, WINDOW_MILLIS)
    }

    private fun closeWindow(reason: String) {
        if (!windowOpen) return
        windowOpen = false
        handler.removeCallbacks(windowTimeout)
        heartSensor?.let { runCatching { manager?.unregisterListener(windowListener, it) } }
        releaseWakeLock()
        Diag.d(TAG, "pulse window closed: $reason")
    }

    /**
     * Hands the dial's reading to the incident record as its reference pulse — at most once a
     * minute, and only while `LOG PULSE` is on, because a physiological reading is written nowhere
     * the wearer did not ask for it.
     *
     * The monitor used to sample a reference of its own after every sign of life. On a wrist that
     * keeps moving that was the LED on for the whole watch: measured 2026-11-11 on duty at a desk,
     * 815 activations of the sensor and the LED on 79 % of eight minutes, against ten activations
     * for ten windows in the same ten minutes without a duty — for a number the next movement
     * overwrote and nothing wrote down unless an incident followed. The dial already has the
     * number; the record borrows it.
     */
    private fun noteReference(bpm: Int) {
        if (!IncidentLog.plausibleBpm(bpm)) return
        val now = SystemClock.elapsedRealtime()
        if (referenceWrittenAt != NEVER && now - referenceWrittenAt < REFERENCE_WRITE_MILLIS) return
        if (!vigilanceStore.logHeartRate(false)) return
        referenceWrittenAt = now
        vigilanceStore.noteReferencePulse(bpm, System.currentTimeMillis())
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) runCatching { lock.release() }
    }

    /**
     * The wrist detector stands for as long as the pulse slot is set, like the platform's step
     * subscription: on-change, so a wrist that stays put costs nothing, and the state has to be
     * known the moment a minute comes round rather than acquired then.
     */
    private fun followBody(wanted: Boolean) {
        val sensor = offBodySensor ?: return
        if (wanted && !followingBody) {
            followingBody = runCatching {
                manager?.registerListener(bodyListener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            }.getOrDefault(false)
        } else if (!wanted && followingBody) {
            runCatching { manager?.unregisterListener(bodyListener, sensor) }
            followingBody = false
            onBody = true
        }
    }

    /**
     * The platform's daily total, subscribed to while the STEPS slot is on — and only then.
     *
     * Once per style, not once per screen-on. A passive registration is meant to stand: it is
     * the platform's own accumulation, costs no hardware, and the figure it holds is the one the
     * next callback brings. It used to be registered and cleared with the screen like the
     * LED-backed sensors, and that is the likeliest reading of the 44-against-8604 measurement
     * of 30 August: the README's own footnote records that this watch reports its "daily" total
     * *since the subscription began*, and a subscription renewed at every glance cannot hold
     * more than the minutes since the last one. Whether that is the whole story is what a day
     * on the duty face against TicHealth decides — the subscription now has a day to fill.
     *
     * Permission is part of "wanted": the editor is where it is asked for, and a slot without
     * it shows dashes rather than a provider refusing in the log once a minute.
     */
    private fun followPlatformSteps(wanted: Boolean) {
        if (wanted && !stepsFromPlatform) {
            // The platform's own total first. Only if it will not give one does the raw
            // counter — and this app's own baseline arithmetic — come into it at all.
            stepsFromPlatform = dailySteps.start { steps ->
                // The platform answered: it owns this row from here on. The fallback is
                // cancelled and the counter, if it had already been brought up, is dropped.
                if (!platformDelivered) {
                    platformDelivered = true
                    handler.removeCallbacks(stepsFallback)
                    manager?.getDefaultSensor(Kind.STEPS.type)?.let { sensor ->
                        runCatching { manager?.unregisterListener(this, sensor) }
                    }
                }
                state.stepsToday = steps
            }
        } else if (!wanted && stepsFromPlatform) {
            dailySteps.stop()
            stepsFromPlatform = false
            platformDelivered = false
        }
    }

    private fun start() {
        val sensorManager = manager ?: return
        // The screen is on, so a wrist was raised: whatever the windows had given up on is over.
        missedWindows = 0
        var any = false
        for (kind in Kind.entries) {
            if (kind == Kind.OFF || (kind != left && kind != right)) continue
            if (!permitted(kind)) {
                // Nothing to do about it here — the editor is where a permission can be asked for,
                // and the readout shows dashes until it is granted.
                Diag.d(TAG, "${kind.name} not permitted")
                continue
            }
            if (kind == Kind.STEPS && stepsFromPlatform) {
                // The platform owns the row (see followPlatformSteps). Until it has actually
                // delivered, the counter is armed behind it as before; once it has, nothing
                // here needs a sensor.
                if (!platformDelivered) {
                    handler.postDelayed(stepsFallback, PLATFORM_GRACE_MILLIS)
                    any = true
                }
                continue
            }
            val sensor = sensorManager.getDefaultSensor(kind.type) ?: continue
            // SENSOR_DELAY_UI rather than anything faster: none of these change quickly, and the
            // two on-change sensors ignore the rate entirely.
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            any = true
        }
        listening = any
    }

    /** The raw sensors down with the screen; the platform subscription stands. */
    fun stop() {
        if (!listening) return
        manager?.unregisterListener(this)
        handler.removeCallbacks(stepsFallback)
        listening = false
    }

    /** Everything down, the standing subscriptions included: the face itself is going away. */
    fun release() {
        closeWindow("released")
        stop()
        followPlatformSteps(false)
        followBody(false)
    }

    private fun permitted(kind: Kind): Boolean {
        val permission = kind.permission ?: return true
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                // A zero or a negative is the sensor saying it has not locked on, not a pulse of
                // zero. Left as no reading, so the dial keeps showing dashes rather than a corpse.
                val bpm = event.values[0].toInt()
                state.heartRate = if (bpm > 0) bpm else NO_READING
                if (bpm > 0) {
                    pulseReadAt = SystemClock.elapsedRealtime()
                    noteReference(bpm)
                }
            }

            // Ignored outright once the platform is answering: see platformDelivered.
            Sensor.TYPE_STEP_COUNTER -> if (!platformDelivered) {
                // A zero this app has just invented is not a reading. The counter measures from
                // the last reboot, so the first sample of a day is subtracted from itself and
                // yields exactly 0 — which is what a freshly installed face used to show somebody
                // who had already walked four thousand steps. Dashes are honest; 0 is not, so it
                // waits for a step or for the platform's own daily total to arrive.
                val steps = stepsToday(event.values[0].toLong(), System.currentTimeMillis())
                if (steps > 0 || !baselineIsNew) state.stepsToday = steps
            }

            Sensor.TYPE_PRESSURE -> {
                // Gated on plausibility for the same reason a heart rate of zero is: the first
                // event after registering can carry something that is not a reading at all. This
                // watch produced 2048 hPa, which is not a pressure that exists anywhere on Earth --
                // Everest is about 330 and the highest ever recorded at sea level about 1084. A
                // number the dial cannot mean is worse than dashes.
                val hpa = event.values[0]
                state.localPressureTenths = if (hpa in MIN_PRESSURE_HPA..MAX_PRESSURE_HPA) {
                    Math.round(hpa * 10f)
                } else {
                    NO_READING
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Steps since the most recent local midnight, from a counter that counts since boot.
     *
     * Local rather than UTC, which it used to be: every other figure of the day on this dial is
     * either labelled Z or runs on local time, and a step count that silently reset at 21:00 or
     * 03:00 local read as the sensor glitching. The offset is a parameter so the two day-boundary
     * cases stay testable without depending on the JVM's default zone.
     *
     * Internal rather than private so a test can hold it to the two cases that are easy to get
     * wrong: a new day, and a reboot part-way through one.
     */
    internal fun stepsToday(
        counter: Long,
        nowMillis: Long,
        zoneOffsetMillis: Int = TimeZone.getDefault().getOffset(nowMillis),
    ): Int {
        val day = (nowMillis + zoneOffsetMillis) / DAY_MILLIS
        val storedDay = prefs.getLong(KEY_BASELINE_DAY, -1L)
        val stored = prefs.getLong(KEY_BASELINE_COUNT, 0L)
        val baseline = baselineFor(counter, stored, storedDay, day)
        // Whether this call is the one that *invented* today's zero point. A baseline made here
        // makes the subtraction below return 0 by construction, and 0 is what the caller must not
        // publish: see onSensorChanged.
        baselineIsNew = day != storedDay
        if (baseline != stored || day != storedDay) {
            runCatching {
                prefs.edit()
                    .putLong(KEY_BASELINE_DAY, day)
                    .putLong(KEY_BASELINE_COUNT, baseline)
                    .apply()
            }
        }
        val steps = counter - baseline
        return if (steps > Int.MAX_VALUE) Int.MAX_VALUE else steps.toInt()
    }

    /**
     * What a slot can show.
     *
     * The set is what this hardware actually offers through a public API, which is narrower than
     * the sensor list suggests. Blood oxygen is present as `android.sensor.ppg_spo2`, a vendor type
     * with no `Sensor.TYPE_*` constant, and it is on-change — it produces a value when the
     * manufacturer's own app runs a measurement and not otherwise, so a watch face reading it would
     * show either nothing or something from yesterday. Ambient temperature is present too, and it
     * is the barometer chip's own die temperature: it reads the warmth of the watch, not the air.
     */
    enum class Kind(val id: String, val type: Int, val permission: String?) {
        OFF("off", -1, null),
        HEART_RATE("hr", Sensor.TYPE_HEART_RATE, Manifest.permission.BODY_SENSORS),
        STEPS("steps", Sensor.TYPE_STEP_COUNTER, Manifest.permission.ACTIVITY_RECOGNITION),

        /**
         * Station pressure from the watch's own barometer, which is a different reading from the
         * `Q` on the weather row: that is sea-level pressure for the nearest station, this is the
         * air where you are standing. Costs no permission and no radio.
         */
        PRESSURE("qfe", Sensor.TYPE_PRESSURE, null),
        ;

        companion object {
            fun of(ordinal: Int): Kind = entries.getOrElse(ordinal) { OFF }

            fun ofId(id: String): Kind = entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    companion object {
        /**
         * How long the platform's own step total is waited for before the raw counter is brought
         * up beside it. Long enough for a passive listener's first delivery, short enough that a
         * silent provider does not cost the row a whole day.
         */
        private const val PLATFORM_GRACE_MILLIS = 30_000L

        /**
         * Where the day's step count is measured from.
         *
         * Pure, and separate from the write, because it is the arithmetic and it has two traps. A
         * new day restarts it, obviously. Less obviously, a counter that comes back *below* its own
         * baseline means the hardware counter restarted without the day doing so — a reboot — and
         * without that case the readout would go negative and stay there until midnight.
         */
        fun baselineFor(counter: Long, storedBaseline: Long, storedDay: Long, day: Long): Long =
            if (day != storedDay || counter < storedBaseline) counter else storedBaseline

        /**
         * Whether an always-on minute opens a pulse window. Pure, so the four conditions can be
         * pinned without a sensor: worn, the screen not already holding the sensor, no window
         * open, and the reading on the dial old enough to be worth replacing — a wrist lowered
         * seconds after the screen filled the slot has nothing to gain from lighting the LED.
         */
        fun windowDue(
            nowElapsed: Long,
            readingElapsed: Long,
            onBody: Boolean,
            interactive: Boolean,
            windowOpen: Boolean,
            missedWindows: Int = 0,
            lastWindowElapsed: Long = NEVER,
        ): Boolean =
            onBody && !interactive && !windowOpen &&
                (readingElapsed == NEVER || nowElapsed - readingElapsed >= FRESH_MILLIS) &&
                (
                    missedWindows < BACKOFF_AFTER_MISSES || lastWindowElapsed == NEVER ||
                        nowElapsed - lastWindowElapsed >= BACKOFF_MILLIS
                    )

        /**
         * Empty windows in a row before the minute cadence gives way to [BACKOFF_MILLIS].
         *
         * The wrist detector is the gate that should stop a window opening on a table, and on
         * 2026-11-12 it did not: the watch lay on a table from 23:21 to 09:14, the detector said
         * *worn* throughout, and 600 windows lit the LED for 2 h 47 m against the wood — 1.6 %/h
         * of battery against 0.94 without. A table never gives a lock, so three misses in a row
         * is the one signal that does not depend on the detector; three because that is also when
         * the reading comes off the dial ([STALE_MILLIS]), so the slot is showing dashes anyway
         * and a slower cadence changes nothing on it. The count resets on a lock, on the screen
         * coming on (a wrist was raised) and on the detector's own *worn* edge.
         */
        const val BACKOFF_AFTER_MISSES: Int = 3

        /**
         * The cadence after [BACKOFF_AFTER_MISSES]: a quarter of an hour. A night on the table
         * costs forty windows instead of six hundred, and a wrist that merely kept moving for
         * three minutes waits at most this long for its next chance in always-on — or not at
         * all, because raising it to look is what resets the count.
         */
        const val BACKOFF_MILLIS: Long = 15 * 60_000L

        /** Whether the reading on the dial has stopped being true. Never, for a dial with none. */
        fun stale(nowElapsed: Long, readingElapsed: Long): Boolean =
            readingElapsed != NEVER && nowElapsed - readingElapsed > STALE_MILLIS

        /** No reading has been put on the dial since the process started. */
        const val NEVER: Long = -1L

        /**
         * How long an always-on window holds the LED waiting for a lock. Fifteen seconds, from
         * the measurement in the class KDoc: a still wrist locks in seven to eleven, and a moving
         * one does not lock in thirty either.
         */
        const val WINDOW_MILLIS: Long = 15_000L

        /**
         * A reading younger than this is not replaced. Half a minute: the next minute's window
         * is then always due, and a wrist lowered just after the screen read it is not.
         */
        const val FRESH_MILLIS: Long = 30_000L

        /**
         * Past this the number comes off the dial. Three windows' worth: one missed lock keeps
         * the last figure, three in a row means the wrist is not giving one, and a number from
         * before that presented as now is the fault this whole arrangement exists to remove.
         */
        const val STALE_MILLIS: Long = 180_000L

        /** Past any window that could need it; a leak costs seconds, not a night. */
        private const val WAKE_LOCK_TIMEOUT_MILLIS: Long = 30_000L
        private const val WAKE_LOCK_TAG = "mfd24:pulse"

        /** How often the reference pulse is written, which is the window's own cadence. */
        private const val REFERENCE_WRITE_MILLIS: Long = 60_000L

        private const val TAG = "SensorSlots"
        private const val PREFS_NAME = "mfd24_sensors"
        private const val KEY_BASELINE_DAY = "step_day"
        private const val KEY_BASELINE_COUNT = "step_baseline"

        private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

        /** No value yet, or none obtainable. The readout shows dashes. */
        const val NO_READING: Int = -1

        /**
         * The range a station-pressure reading has to fall in to be shown.
         *
         * Wide enough for anywhere a person can stand -- the summit of Everest is about 330 hPa and
         * the highest ever recorded at sea level about 1084 -- and narrow enough to reject whatever
         * it is this watch reports before the barometer has settled.
         */
        private const val MIN_PRESSURE_HPA = 250f
        private const val MAX_PRESSURE_HPA = 1150f
    }
}
