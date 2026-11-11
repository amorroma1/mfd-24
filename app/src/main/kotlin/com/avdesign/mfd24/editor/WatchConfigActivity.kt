// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.watchface.editor.EditorSession
import com.avdesign.mfd24.BuildConfig
import com.avdesign.mfd24.Diag
import com.avdesign.mfd24.R
import com.avdesign.mfd24.data.SensorSlots
import com.avdesign.mfd24.data.TelemetryRepository
import com.avdesign.mfd24.data.TelemetryState
import com.avdesign.mfd24.data.VigilanceMonitor
import com.avdesign.mfd24.data.VigilanceStore
import com.avdesign.mfd24.data.WatchShiftController
import com.avdesign.mfd24.data.WatchShiftState
import com.avdesign.mfd24.export.LogExportActivity
import com.avdesign.mfd24.export.RepoLinkActivity
import com.avdesign.mfd24.style.Palette
import com.avdesign.mfd24.style.StyleSchema
import com.avdesign.mfd24.update.ReleaseLinkActivity
import com.avdesign.mfd24.update.UpdateStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * On-watch configuration, reached through the system's long-press "Customise" affordance.
 *
 * Everything it edits lives in the watch face's [androidx.wear.watchface.style.UserStyleSchema], so
 * the session persists the changes and the running watch face picks them up live — this screen owns
 * no settings of its own.
 *
 * It also collects the location permission, which the watch face itself cannot: a
 * `WallpaperService` has no UI to put a runtime-permission dialog on top of.
 */
class WatchConfigActivity : ComponentActivity() {

    private lateinit var requestLocation: ActivityResultLauncher<String>
    private val locationGranted: MutableState<Boolean> = mutableStateOf(false)

    /**
     * Every other runtime permission goes through one launcher, for the same reason the location
     * one is here at all: a `WallpaperService` has no UI to put a permission dialog on, so the
     * watch face can never ask for anything itself. Sensor permissions are requested when a slot
     * is set to the reading that needs them; background location when the user asks for it from
     * the position section; notifications and activity recognition when vigilance goes on, because
     * API 33 gates the foreground notice and API 34 gates the health service type itself.
     */
    private lateinit var requestPermission: ActivityResultLauncher<String>
    private val grantCount: MutableIntState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            locationGranted.value = it
        }
        locationGranted.value = hasLocationPermission()

        // The count is a nudge for recomposition, not a value: which permissions are held is read
        // straight from the package manager, and this only says that the answer may have changed.
        requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            grantCount.intValue = grantCount.intValue + 1
        }

        lifecycleScope.launch {
            val session = try {
                EditorSession.createOnWatchEditorSession(this@WatchConfigActivity)
            } catch (e: Exception) {
                Diag.e(TAG, "Not launched as a watch face editor", e)
                finish()
                return@launch
            }

            val watchShift = WatchShiftController.get(this@WatchConfigActivity)
            val repository = TelemetryRepository.get(this@WatchConfigActivity)

            setContent {
                MaterialTheme(colors = CockpitColors) {
                    ConfigScreen(
                        session = session,
                        watchShift = watchShift,
                        repository = repository,
                        locationGranted = locationGranted.value,
                        onRequestLocation = {
                            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        grants = grantCount.intValue,
                        onRequestPermission = { requestPermission.launch(it) },
                        onDone = { finish() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        locationGranted.value = hasLocationPermission()
        // Background location is granted on a settings screen, not in a dialog, so the result
        // arrives as a resume rather than through the launcher: nudge the readers to re-check.
        grantCount.intValue = grantCount.intValue + 1
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "WatchConfigActivity"
    }
}

@Composable
private fun ConfigScreen(
    session: EditorSession,
    watchShift: WatchShiftController,
    repository: TelemetryRepository,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    grants: Int,
    onRequestPermission: (String) -> Unit,
    onDone: () -> Unit,
) {
    // The on-watch EditorSession exposes `userStyle` through a proxy that does not implement
    // `collect` (the library logs "userStyle proxy encountered unexpected method name 'collect'"),
    // so the flow cannot drive recomposition. This screen is the only writer, so it mirrors the
    // selections locally and pushes each change into the session.
    var palette by rememberOption(session, StyleSchema.PALETTE, Palette.ID_AMBER)
    var tempUnit by rememberOption(session, StyleSchema.TEMP_UNIT, StyleSchema.TEMP_CELSIUS)
    var pressureUnit by rememberOption(session, StyleSchema.PRESSURE_UNIT, StyleSchema.PRESSURE_HPA)
    var midnightLabel by rememberOption(
        session, StyleSchema.MIDNIGHT_LABEL, StyleSchema.MIDNIGHT_AS_00
    )
    var dialTop by rememberOption(session, StyleSchema.DIAL_TOP, StyleSchema.DIAL_TOP_NOON)
    var nadir by rememberOption(session, StyleSchema.NADIR, StyleSchema.NADIR_OFF)
    var solarMark by rememberOption(session, StyleSchema.SOLAR_MARK, StyleSchema.SOLAR_OFF)
    var lunarMark by rememberOption(session, StyleSchema.LUNAR_MARK, StyleSchema.LUNAR_OFF)
    var vigilance by rememberOption(session, StyleSchema.VIGILANCE, StyleSchema.VIGILANCE_OFF)
    var vigilanceInterval by rememberOption(
        session, StyleSchema.VIGILANCE_INTERVAL, StyleSchema.VIGILANCE_INTERVAL_DEFAULT
    )
    var vibeStrength by rememberOption(session, StyleSchema.VIBE_STRENGTH, StyleSchema.VIBE_MED)
    var sosSound by rememberOption(session, StyleSchema.SOS_SOUND, StyleSchema.SOS_SOUND_HIGH)
    var logHeartRate by rememberOption(
        session, StyleSchema.LOG_HEART_RATE, StyleSchema.LOG_HR_OFF
    )
    var weather by rememberOption(session, StyleSchema.WEATHER, StyleSchema.WEATHER_ON)
    var ambientDensity by rememberOption(
        session, StyleSchema.AMBIENT_DENSITY, StyleSchema.AMBIENT_FULL
    )

    // Position state, held in hundredths of a degree: one step is about 1.1 km of latitude and
    // less of longitude away from the equator, which fits inside the 5 km site lock. It used to be
    // tenths, and 11 km of quantisation is what forced a manual position to forgo that row.
    var manualActive by remember { mutableStateOf(repository.manualPositionSelected) }
    // Seeded from whatever position is in force when there is no manual one on file yet: even
    // with the hold-to-repeat, starting from 0.00 is the Gulf of Guinea and seconds of travel.
    var manualLat by remember {
        mutableIntStateOf(toHundredths(repository.manualLatitude(), repository.currentLatitude()))
    }
    var manualLon by remember {
        mutableIntStateOf(toHundredths(repository.manualLongitude(), repository.currentLongitude()))
    }
    var hasPosition by remember { mutableStateOf(repository.state.hasPosition) }

    // What is actually in force. Mirrored into Compose state for the same reason the style
    // selections are: TelemetryState is a plain volatile holder, and a lazy list item that merely
    // read it would never be told to recompose.
    var positionSource by remember { mutableIntStateOf(repository.state.positionSource) }

    // Set when a tap on AUTO had to detour through the permission dialog, so the grant can finish
    // what the tap began. Without it the switch stayed on MANUAL after the user said yes, and the
    // second tap it silently waited for looked like the first one not working.
    var autoRequested by remember { mutableStateOf(false) }

    // Only a *transition* into granted is interesting. Keyed on the flag alone this would also
    // fire on every open of the editor with permission already held, and waking the GPS because
    // somebody looked at the settings is exactly the background work the brief rules out.
    var grantSeen by remember { mutableStateOf(locationGranted) }
    LaunchedEffect(locationGranted) {
        if (locationGranted && !grantSeen) {
            if (autoRequested) {
                autoRequested = false
                repository.manualPositionSelected = false
            }
            // The user has just said yes and is watching this screen: go and get a real position
            // now rather than leaving NO SITE up until the half-hourly worker next runs.
            runCatching { repository.onLocationGranted() }
            hasPosition = repository.state.hasPosition
            positionSource = repository.state.positionSource
            manualActive = repository.manualPositionSelected
        }
        grantSeen = locationGranted
    }
    // Duty length lives in the controller's own storage, not in the style: it is timer
    // configuration, and the custom figure has to survive a trip through the presets.
    var durationPreset by remember { mutableStateOf(watchShift.durationPreset) }
    var customMillis by remember { mutableLongStateOf(watchShift.customDurationMillis) }
    val selectedMillis = WatchShiftController.presetMillis(durationPreset) ?: customMillis

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dutyState by remember { mutableIntStateOf(watchShift.state.dutyState(nowMillis)) }

    // Booked start, held locally: it is a specific instant, not a style, and it only matters until
    // the user presses BOOK START. Defaults to the next round five minutes an hour from now.
    var bookedStart by remember { mutableLongStateOf(defaultBookedStart(nowMillis)) }

    // Keep the countdown on the chip honest while the screen is open.
    LaunchedEffect(dutyState) {
        while (true) {
            delay(20_000L)
            nowMillis = System.currentTimeMillis()
            dutyState = watchShift.state.dutyState(nowMillis)
        }
    }

    // Read once per opening of the editor. The monitor loads it at process start and only the
    // service ever appends, so there is nothing here to keep live.
    val editorContext = LocalContext.current
    var sensorLeft by rememberOption(session, StyleSchema.SENSOR_LEFT, SensorSlots.Kind.OFF.id)
    var sensorRight by rememberOption(session, StyleSchema.SENSOR_RIGHT, SensorSlots.Kind.OFF.id)
    val vigilanceMonitor = remember { VigilanceMonitor.get(editorContext) }
    var incidents by remember { mutableStateOf(vigilanceMonitor.state.incidents) }

    // The release the daily check found, if any. It is named on the ABOUT version row and nowhere
    // else: a banner at the top of this list was worth its interruption only while it could put one
    // tap between hearing about a release and installing it, and Wear OS took the second half away.
    // Somebody who opens the settings has come to change a setting.
    val pendingUpdate = remember { UpdateStore.pendingVersion(editorContext) }
    var updateCheck by remember { mutableStateOf(UpdateStore.checkEnabled(editorContext)) }
    var hints by remember { mutableStateOf(EditorPrefs.hintsShown(editorContext)) }


    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    // Which section is open. One at a time, and not remembered between openings of the editor: a
    // fresh activity every time means the list always starts in the same shape, which is worth more
    // here than continuity — the rows are navigated by looking at where they are, and a layout that
    // depended on what was expanded last week would be unreadable.
    var openSection by remember { mutableIntStateOf(SECTION_DUTY) }

    // Toggling never scrolls towards the freshly opened content. It used to, and the whole list
    // lurched on every tap: the eye had just committed to a spot and the spot moved. Instead the
    // tapped header is pinned exactly where the finger found it — even at the bottom of the
    // screen, even though that leaves the opened rows below the fold — by re-anchoring the list
    // one frame after the sections reflow. Headers carry keys because a header's *index* moves
    // when a section above it collapses; its key does not.
    val toggleSection: (Int) -> Unit = { id ->
        val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == headerKey(id) }
        openSection = if (openSection == id) SECTION_NONE else id
        if (anchor != null) {
            scope.launch {
                runCatching {
                    // Re-pinned every frame for a handful of frames rather than once. Correcting
                    // after a single frame still let one frame of the jump reach the screen, and
                    // one frame of a header sliding is exactly what the eye catches; the list also
                    // settles over more than one pass when the section that closed was taller than
                    // the one that opened. Cheap: it stops as soon as the offset stops moving.
                    repeat(PIN_FRAMES) {
                        withFrameNanos { }
                        val moved = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == headerKey(id) } ?: return@runCatching
                        if (moved.offset == anchor.offset) return@runCatching
                        listState.scrollToItem(moved.index, anchor.offset)
                    }
                }
            }
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
        // The scroll boundary is anchored to item 0 — the DUTY CONTROL header, whose size never
        // changes — rather than the default item 1, which is the first *row* of whatever is open.
        // Anchored there, expanding the first section changed the boundary's height, and the
        // whole list jumped to the re-clamped position on every open: the one header the pin
        // below could not hold, because a clamp outranks a scroll.
        autoCentering = AutoCenteringParams(itemIndex = 0),
    ) {
        // ---- Duty ------------------------------------------------------------------------------
        // Both actions in this section spend the same duration, so it is chosen once at the top and
        // neither START NOW nor ARM TIMER carries a length of its own. Scheduling used to be a
        // section two screens further down, which put the figure it spends out of sight of the rows
        // that set it.
        item(key = headerKey(SECTION_DUTY)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_watch),
                expanded = openSection == SECTION_DUTY,
                onClick = { toggleSection(SECTION_DUTY) },
            )
        }
        if (openSection == SECTION_DUTY) {

            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_duration),
                    options = DURATION_PRESETS,
                    selectedId = durationPreset,
                    onSelect = { id ->
                        durationPreset = id
                        watchShift.durationPreset = id
                    },
                )
            }
            // The custom row appears only when CST is chosen, directly under the presets it belongs to.
            if (durationPreset == WatchShiftController.PRESET_CUSTOM) {
                item {
                    StepperRow(
                        value = stringResource(
                            R.string.editor_duration_value,
                            (customMillis / 3_600_000L).toInt(),
                            (customMillis % 3_600_000L / 60_000L).toInt(),
                        ),
                        onDown = { n ->
                            customMillis = (customMillis - n * WatchShiftController.STEP_MILLIS)
                                .coerceAtLeast(WatchShiftController.MIN_MILLIS)
                        },
                        onUp = { n ->
                            customMillis = (customMillis + n * WatchShiftController.STEP_MILLIS)
                                .coerceAtMost(WatchShiftController.MAX_MILLIS)
                        },
                        maxStep = DURATION_MAX_STEP,
                        // Written on release rather than per tick, and written at all so that CST comes
                        // back to the figure it was left at.
                        onCommit = { watchShift.customDurationMillis = customMillis },
                    )
                }
            }

            item { Label(stringResource(R.string.editor_label_schedule)) }
            // Both steppers floor at the next bookable instant, read off a live clock rather
            // than the 20-second `nowMillis`: stepping below "now" would arm a start that
            // schedule() converts into an immediate, chiming start — a mis-tap in disguise.
            item {
                StepperRow(
                    value = formatBookedDate(bookedStart),
                    onDown = { n ->
                        bookedStart = (bookedStart - n * DAY_MILLIS)
                            .coerceAtLeast(earliestBookableNow())
                    },
                    onUp = { n -> bookedStart += n * DAY_MILLIS },
                )
            }
            // One HH:MM row rather than an hour row and a minute row: the hold accelerates fast enough
            // to cross half a day in a couple of seconds, so splitting them bought nothing but height.
            item {
                StepperRow(
                    value = stringResource(
                        R.string.editor_start_time, hourOf(bookedStart), minuteOf(bookedStart)
                    ),
                    onDown = { n ->
                        bookedStart = (bookedStart - n * WatchShiftController.STEP_MILLIS)
                            .coerceAtLeast(earliestBookableNow())
                    },
                    onUp = { n -> bookedStart += n * WatchShiftController.STEP_MILLIS },
                    maxStep = DURATION_MAX_STEP,
                )
            }
            item {
                OptionChip(
                    label = stringResource(R.string.editor_schedule_watch),
                    selected = dutyState == WatchShiftState.DUTY_PENDING,
                    onClick = {
                        // The chosen minute may have slipped past while the editor sat open;
                        // re-clamp so arming books the next step instead of starting now.
                        bookedStart = bookedStart.coerceAtLeast(earliestBookableNow())
                        watchShift.schedule(bookedStart, selectedMillis)
                        nowMillis = System.currentTimeMillis()
                        dutyState = watchShift.state.dutyState(nowMillis)
                    },
                )
            }
            // The section's conclusion, and so its last row: everything above decides what it will do.
            item {
                val running = dutyState == WatchShiftState.DUTY_ACTIVE ||
                    dutyState == WatchShiftState.DUTY_PENDING
                val secondary = dutySecondaryText(dutyState, watchShift, nowMillis)
                Chip(
                    onClick = {
                        if (running) watchShift.cancel() else watchShift.start(selectedMillis)
                        nowMillis = System.currentTimeMillis()
                        dutyState = watchShift.state.dutyState(nowMillis)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (running) {
                        // Terracotta rather than the palette's alarm red: ending a duty on purpose is
                        // a decision, not an emergency.
                        ChipDefaults.primaryChipColors(backgroundColor = Terracotta)
                    } else {
                        ChipDefaults.primaryChipColors()
                    },
                    label = {
                        Text(
                            text = stringResource(
                                if (running) R.string.editor_end_watch else R.string.editor_start_watch
                            ),
                            maxLines = 1,
                        )
                    },
                    secondaryLabel = if (secondary != null) {
                        { Text(text = secondary, maxLines = 1) }
                    } else {
                        null
                    },
                )
            }
        }


        // ---- Vigilance -------------------------------------------------------------------------
        // Directly after the duty it watches over, rather than between the two ways of starting one.
        item(key = headerKey(SECTION_VIGILANCE)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_vigilance),
                expanded = openSection == SECTION_VIGILANCE,
                onClick = { toggleSection(SECTION_VIGILANCE) },
            )
        }
        if (openSection == SECTION_VIGILANCE) {
            item {
                SegmentedRow(
                    options = VIGILANCE_OPTIONS,
                    selectedId = vigilance,
                    onSelect = { id ->
                        session.select(StyleSchema.VIGILANCE, id)
                        vigilance = id
                        // What newer platforms gate the monitor on, asked for at the moment of
                        // switching it on: API 34 refuses the health foreground service without
                        // ACTIVITY_RECOGNITION, and API 33 hides its notification without
                        // POST_NOTIFICATIONS. One at a time - a second launch cancels the first.
                        if (id == StyleSchema.VIGILANCE_ON) {
                            vigilancePermissionToRequest(editorContext, grants)
                                ?.let(onRequestPermission)
                        }
                    },
                )
            }
            item { Hint(stringResource(R.string.editor_vigilance_needs_watch), hints) }
            if (vigilance == StyleSchema.VIGILANCE_ON &&
                vigilancePermissionToRequest(editorContext, grants) != null
            ) {
                item { Hint(stringResource(R.string.editor_vigilance_notifications), hints) }
            }

            // The log has nowhere else to be read. It is written to device-protected preferences
            // that a release build gives no way into - run-as needs a debuggable package — so
            // without this row the marks on the duty arc are the only sight of it, and they only
            // ever show the watch under way.
            item { Label(stringResource(R.string.editor_label_incidents)) }
            // Which watch these instants belong to. Without it the list is bare moments, and a
            // bare moment cannot say whether it was ten minutes into a night watch or the last
            // hour of a sixteen — which is most of what the list is read for.
            formatLogShift(
                vigilanceMonitor.state.logShiftStartMillis,
                vigilanceMonitor.state.logShiftEndMillis,
            )?.let { watch -> item { Caption(watch) } }
            if (incidents.isEmpty()) {
                item { Caption(stringResource(R.string.editor_incidents_none)) }
            } else {
                // Newest first: the question asked of a log on a wrist is almost always "when was
                // the last one", and that answer should not need scrolling to.
                items(incidents.size) { index ->
                    val record = incidents[incidents.size - 1 - index]
                    // One list item, two lines — stacked in a Column rather than emitted as two
                    // composables, which a lazy list draws on top of each other inside the one
                    // slot it gave them. The pulse is a second line rather than a longer first
                    // one: the instant is what the eye scans for, and a reading it had to read
                    // past would cost the scan. Absent readings print nothing at all — never a
                    // dash that could be mistaken for a measured zero.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Caption(formatIncident(record))
                        formatIncidentPulse(record)?.let { Caption(it) }
                    }
                }
                // The log's way off a release build: no companion app, no network, no debug
                // bridge — but every watch has a speaker and every phone has a camera and a
                // microphone. One screen shows the QR and plays the Bell 202 burst together; see
                // export/Afsk.kt for why the sound is a 1979 standard and nothing bespoke.
                item {
                    OptionChip(
                        label = stringResource(R.string.editor_tx_log),
                        selected = false,
                        onClick = {
                            editorContext.startActivity(
                                Intent(editorContext, LogExportActivity::class.java)
                            )
                        },
                    )
                }
                item { Hint(stringResource(R.string.editor_tx_caption), hints) }
                item {
                    OptionChip(
                        label = stringResource(R.string.editor_incidents_clear),
                        selected = false,
                        onClick = {
                            vigilanceMonitor.clearLog()
                            incidents = VigilanceStore.EMPTY
                        },
                    )
                }
            }
            if (vigilance == StyleSchema.VIGILANCE_ON) {
                item {
                    SegmentedSetting(
                        label = stringResource(R.string.editor_label_vigilance_interval),
                        options = VIGILANCE_INTERVAL_OPTIONS,
                        selectedId = vigilanceInterval,
                        onSelect = { id ->
                            session.select(StyleSchema.VIGILANCE_INTERVAL, id)
                            vigilanceInterval = id
                        },
                    )
                }
                // Doze will not run an exact alarm oftener than every nine minutes per app, so
                // the shortest setting quietly stretches. Said here, not discovered on a wrist.
                if (vigilanceInterval == "5") {
                    item { Hint(stringResource(R.string.editor_vigilance_doze), hints) }
                }
                item {
                    SegmentedSetting(
                        label = stringResource(R.string.editor_label_vibe),
                        options = VIBE_OPTIONS,
                        selectedId = vibeStrength,
                        onSelect = { id ->
                            session.select(StyleSchema.VIBE_STRENGTH, id)
                            vibeStrength = id
                        },
                    )
                }
                // Sound is its own setting because it addresses somebody else: the buzz is for the
                // wearer, the tone is for whoever has to find them.
                item {
                    SegmentedSetting(
                        label = stringResource(R.string.editor_label_sos_sound),
                        options = SOS_SOUND_OPTIONS,
                        selectedId = sosSound,
                        onSelect = { id ->
                            session.select(StyleSchema.SOS_SOUND, id)
                            sosSound = id
                        },
                    )
                }
                item { Hint(stringResource(R.string.editor_sos_sound_note), hints) }
                // Offered only where it can work. BODY_SENSORS is a runtime grant a watch face
                // cannot request for itself, so with it missing this row would be a switch that
                // switches nothing — the permission caption above already asks.
                if (bodySensorsGranted(editorContext, grants)) {
                    item {
                        SegmentedSetting(
                            label = stringResource(R.string.editor_label_log_hr),
                            options = LOG_HR_OPTIONS,
                            selectedId = logHeartRate,
                            onSelect = { id ->
                                session.select(StyleSchema.LOG_HEART_RATE, id)
                                logHeartRate = id
                            },
                        )
                    }
                    item { Hint(stringResource(R.string.editor_log_hr_note), hints) }
                }
            }
        }


        // ---- Sensors ---------------------------------------------------------------------------
        // Between the monitor and the position, not at the bottom of the list: everything from
        // here up is about what hardware watches or reads the wearer, which is one family of
        // decision — and the headers now taper down the screen, so a swipe reads as one shape
        // instead of a long word snagging the eye below the fold.
        item(key = headerKey(SECTION_SENSORS)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_sensors),
                expanded = openSection == SECTION_SENSORS,
                onClick = { toggleSection(SECTION_SENSORS) },
            )
        }
        if (openSection == SECTION_SENSORS) {
            item {
                SensorSlotSetting(
                    label = stringResource(R.string.editor_label_sensor_left),
                    selectedId = sensorLeft,
                    otherId = sensorRight,
                    grants = grants,
                    onRequestPermission = onRequestPermission,
                    onSelect = { id ->
                        session.select(StyleSchema.SENSOR_LEFT, id)
                        sensorLeft = id
                    },
                )
            }
            item {
                SensorSlotSetting(
                    label = stringResource(R.string.editor_label_sensor_right),
                    selectedId = sensorRight,
                    otherId = sensorLeft,
                    grants = grants,
                    onRequestPermission = onRequestPermission,
                    onSelect = { id ->
                        session.select(StyleSchema.SENSOR_RIGHT, id)
                        sensorRight = id
                    },
                )
            }
            if (missingSensorPermission(editorContext, sensorLeft, sensorRight, grants)) {
                item { Hint(stringResource(R.string.editor_sensor_needs_permission), hints) }
            }
        }

        // ---- Position --------------------------------------------------------------------------
        // Ahead of the display settings because NADIR and WEATHER both need a position: this was the
        // last section on the screen, so the line saying one is required pointed at rows the user
        // had not scrolled to yet.
        item(key = headerKey(SECTION_POSITION)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_position),
                expanded = openSection == SECTION_POSITION,
                onClick = { toggleSection(SECTION_POSITION) },
            )
        }
        if (openSection == SECTION_POSITION) {

            // Where the position comes from is the user's choice, not a reading of the OS permission.
            // It used to be the latter, which left no way to switch the device off: on API 30 an app
            // cannot hand its own location permission back, so with the permission granted the chip
            // had nothing to toggle and the manual entry below it could never appear.
            item {
                SegmentedRow(
                    options = POSITION_OPTIONS,
                    selectedId = if (manualActive) POSITION_MANUAL else POSITION_AUTO,
                    onSelect = { id ->
                        if (id == POSITION_AUTO && !locationGranted) {
                            autoRequested = true
                            onRequestLocation()
                        } else {
                            val manual = id == POSITION_MANUAL
                            repository.manualPositionSelected = manual
                            manualActive = manual
                            hasPosition = repository.state.hasPosition
                            positionSource = repository.state.positionSource
                            if (!manual) {
                                // Go and get one now; the point of switching back is to see it happen.
                                scope.launch {
                                    runCatching { repository.onLocationGranted() }
                                    hasPosition = repository.state.hasPosition
                                    positionSource = repository.state.positionSource
                                }
                            }
                        }
                    },
                )
            }

            // What is actually in force, which is not always what was asked for: choose manual with no
            // coordinates on file yet and the answer is still "nothing".
            item {
                val source = positionSource
                val subRes = when (source) {
                    TelemetryState.POSITION_DEVICE -> R.string.editor_position_device_sub
                    TelemetryState.POSITION_MANUAL -> R.string.editor_position_manual_sub
                    else -> R.string.editor_position_none_sub
                }
                val labelRes = when (source) {
                    TelemetryState.POSITION_DEVICE -> R.string.editor_location_device
                    TelemetryState.POSITION_MANUAL -> R.string.editor_location_manual
                    else -> R.string.editor_location_none
                }
                Chip(
                    onClick = { if (!locationGranted && !manualActive) onRequestLocation() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (source == TelemetryState.POSITION_NONE) {
                        ChipDefaults.secondaryChipColors()
                    } else {
                        ChipDefaults.primaryChipColors()
                    },
                    label = { Text(text = stringResource(labelRes), maxLines = 1) },
                    secondaryLabel = { Text(text = stringResource(subRes), maxLines = 1) },
                )
            }
            item { Hint(stringResource(R.string.editor_location_rationale), hints) }

            // "While in use" covers only this screen: the half-hourly worker and the face itself
            // are background callers, and without "all the time" the platform hands them null.
            // The system grants it on a settings screen, so the answer comes back through
            // onResume rather than the launcher.
            if (locationGranted && !manualActive &&
                !heldPermission(
                    editorContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION, grants
                )
            ) {
                item { Hint(stringResource(R.string.editor_background_rationale), hints) }
                item {
                    OptionChip(
                        label = stringResource(R.string.editor_background_location),
                        selected = false,
                        onClick = {
                            onRequestPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        },
                    )
                }
            }

            // Coordinate entry follows the choice, not the permission: picking manual is exactly when
            // you need to be able to type one in, whether or not the platform would also give you one.
            if (manualActive) {
                item {
                    StepperRow(
                        value = stringResource(R.string.editor_manual_lat, formatDegrees(manualLat)),
                        onDown = { n -> manualLat = (manualLat - n).coerceAtLeast(-9000) },
                        onUp = { n -> manualLat = (manualLat + n).coerceAtMost(9000) },
                        // 18000 hundredths from pole to pole; this is the row the hold exists for.
                        maxStep = COORD_MAX_STEP,
                    )
                }
                item {
                    StepperRow(
                        value = stringResource(R.string.editor_manual_lon, formatDegrees(manualLon)),
                        onDown = { n -> manualLon = (manualLon - n).coerceAtLeast(-18000) },
                        onUp = { n -> manualLon = (manualLon + n).coerceAtMost(18000) },
                        maxStep = COORD_MAX_STEP,
                    )
                }
                item {
                    OptionChip(
                        label = stringResource(R.string.editor_manual_apply),
                        selected = positionSource == TelemetryState.POSITION_MANUAL,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    repository.setManualPosition(
                                        manualLat / 100.0,
                                        manualLon / 100.0,
                                    )
                                }
                                manualActive = true
                                hasPosition = repository.state.hasPosition
                                positionSource = repository.state.positionSource
                            }
                        },
                    )
                }
                if (repository.hasManualPosition()) {
                    item {
                        OptionChip(
                            label = stringResource(R.string.editor_manual_clear),
                            selected = false,
                            onClick = {
                                repository.clearManualPosition()
                                manualActive = false
                                hasPosition = repository.state.hasPosition
                                positionSource = repository.state.positionSource
                            },
                        )
                    }
                }
            }
        }


        // ---- Display ---------------------------------------------------------------------------
        // Seven settings that only change how the face looks, under one heading. They were seven
        // sections of a header and two full-width chips each: fifteen rows to say seven things, and
        // nothing in the list to show which of them belonged together.
        item(key = headerKey(SECTION_DISPLAY)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_display),
                expanded = openSection == SECTION_DISPLAY,
                onClick = { toggleSection(SECTION_DISPLAY) },
            )
        }
        if (openSection == SECTION_DISPLAY) {
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_palette),
                    options = PALETTE_OPTIONS,
                    selectedId = palette,
                    onSelect = { id ->
                        session.select(StyleSchema.PALETTE, id)
                        palette = id
                    },
                )
            }
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_dial_top),
                    options = DIAL_TOP_OPTIONS,
                    selectedId = dialTop,
                    onSelect = { id ->
                        session.select(StyleSchema.DIAL_TOP, id)
                        dialTop = id
                    },
                )
            }
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_midnight),
                    options = MIDNIGHT_OPTIONS,
                    selectedId = midnightLabel,
                    onSelect = { id ->
                        session.select(StyleSchema.MIDNIGHT_LABEL, id)
                        midnightLabel = id
                    },
                )
            }
            item {
                // Nothing to shade without a position, so the choice is not offered as if it were live.
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_nadir),
                    options = NADIR_OPTIONS,
                    selectedId = nadir,
                    onSelect = { id ->
                        session.select(StyleSchema.NADIR, id)
                        nadir = id
                    },
                    isEnabled = { hasPosition },
                )
            }
            if (!hasPosition) {
                item { Hint(stringResource(R.string.editor_nadir_needs_position), hints) }
            }
            // Below Nadir because it draws on Nadir: the sun's true position on the band, which
            // is a solar compass for whoever knows how to hold one. Gated the same way — with no
            // band there is nothing to read the mark against.
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_solar),
                    options = SOLAR_OPTIONS,
                    selectedId = solarMark,
                    onSelect = { id ->
                        session.select(StyleSchema.SOLAR_MARK, id)
                        solarMark = id
                    },
                    isEnabled = { id ->
                        id == StyleSchema.SOLAR_OFF ||
                            (nadir == StyleSchema.NADIR_ON && hasPosition)
                    },
                )
            }
            if (solarMark == StyleSchema.SOLAR_ON) {
                item { Hint(stringResource(R.string.editor_solar_note), hints) }
            }
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_lunar),
                    options = LUNAR_OPTIONS,
                    selectedId = lunarMark,
                    onSelect = { id ->
                        session.select(StyleSchema.LUNAR_MARK, id)
                        lunarMark = id
                    },
                    isEnabled = { id ->
                        id == StyleSchema.LUNAR_OFF ||
                            (nadir == StyleSchema.NADIR_ON && hasPosition)
                    },
                )
            }
            if (lunarMark == StyleSchema.LUNAR_ON) {
                item { Hint(stringResource(R.string.editor_lunar_note), hints) }
            }
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_weather),
                    options = WEATHER_OPTIONS,
                    selectedId = weather,
                    onSelect = { id ->
                        session.select(StyleSchema.WEATHER, id)
                        weather = id
                    },
                    isEnabled = { id -> hasPosition || id == StyleSchema.WEATHER_OFF },
                )
            }
            // Always-on wears the lume palette, dimmed -- there is no separate colour to choose, and
            // so nothing changes hue when the watch wakes. All that is left is whether it is thinned.
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_ambient),
                    options = AMBIENT_DENSITY_OPTIONS,
                    selectedId = ambientDensity,
                    onSelect = { id ->
                        session.select(StyleSchema.AMBIENT_DENSITY, id)
                        ambientDensity = id
                    },
                )
            }
            // AUTO is a rule, not a state, so it gets the one line the other options do not need.
            if (ambientDensity == StyleSchema.AMBIENT_AUTO) {
                item { Hint(stringResource(R.string.editor_ambient_auto_note), hints) }
            }
            // Last in the section that decides how things look, because this decides how *this
            // list* looks. Not a style setting for all that: it changes nothing the renderer
            // draws, and a schema change must not cost somebody their palette to reset it.
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_hints),
                    options = HINT_OPTIONS,
                    selectedId = if (hints) HINTS_ON else HINTS_OFF,
                    onSelect = { id ->
                        hints = id == HINTS_ON
                        EditorPrefs.setHintsShown(editorContext, hints)
                    },
                )
            }
            item { Hint(stringResource(R.string.editor_hints_note), hints) }
        }


        // ---- Units -----------------------------------------------------------------------------
        // Both rows labelled: unlabelled, the four chips ran together and the second pair read as
        // more of the first.
        item(key = headerKey(SECTION_UNITS)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_units),
                expanded = openSection == SECTION_UNITS,
                onClick = { toggleSection(SECTION_UNITS) },
            )
        }
        if (openSection == SECTION_UNITS) {
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_temperature),
                    options = TEMP_OPTIONS,
                    selectedId = tempUnit,
                    onSelect = { id ->
                        session.select(StyleSchema.TEMP_UNIT, id)
                        tempUnit = id
                    },
                )
            }
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_pressure),
                    options = PRESSURE_OPTIONS,
                    selectedId = pressureUnit,
                    onSelect = { id ->
                        session.select(StyleSchema.PRESSURE_UNIT, id)
                        pressureUnit = id
                    },
                )
            }
        }


        // ---- About -----------------------------------------------------------------------------
        // Last on purpose: identity and maintenance, not operation. The update path itself lives
        // in UpdateActivity — the notes deserve a whole screen, not three rows of an accordion.
        item(key = headerKey(SECTION_ABOUT)) {
            SectionHeader(
                text = stringResource(R.string.editor_section_about),
                expanded = openSection == SECTION_ABOUT,
                onClick = { toggleSection(SECTION_ABOUT) },
            )
        }
        if (openSection == SECTION_ABOUT) {
            item {
                Caption(
                    stringResource(
                        R.string.editor_about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    )
                )
            }
            // The one switch that decides whether this app talks to the network unasked. Not a
            // style setting: it changes nothing the renderer draws, and it must survive a schema
            // change.
            item {
                SegmentedSetting(
                    label = stringResource(R.string.editor_label_update_check),
                    options = UPDATE_CHECK_OPTIONS,
                    selectedId = if (updateCheck) UPDATE_CHECK_ON else UPDATE_CHECK_OFF,
                    onSelect = { id ->
                        updateCheck = id == UPDATE_CHECK_ON
                        UpdateStore.setCheckEnabled(editorContext, updateCheck)
                    },
                )
            }
            item { Hint(stringResource(R.string.editor_update_check_note), hints) }
            item {
                OptionChip(
                    label = stringResource(R.string.editor_about_github),
                    selected = false,
                    onClick = {
                        editorContext.startActivity(
                            Intent(editorContext, RepoLinkActivity::class.java)
                        )
                    },
                )
            }
            // One chip, and it says whether there is anything to go and get. Tapping it shows the
            // release page as a QR — the notes, the checksum and the APK are all there, in a
            // browser, at a size a person can read. Nothing is laid out on the watch and nothing is
            // installed by it, because Wear OS will not let an app install an app.
            item {
                OptionChip(
                    label = if (pendingUpdate == null) {
                        stringResource(R.string.editor_about_update)
                    } else {
                        stringResource(R.string.editor_about_update_available, pendingUpdate)
                    },
                    selected = pendingUpdate != null,
                    onClick = {
                        editorContext.startActivity(
                            Intent(editorContext, ReleaseLinkActivity::class.java)
                                .putExtra(ReleaseLinkActivity.EXTRA_VERSION, pendingUpdate.orEmpty())
                        )
                    },
                )
            }
            item { Hint(stringResource(R.string.editor_about_update_note), hints) }
        }


        // The list's full stop. It stays, though a swipe also leaves: on a round screen a swipe
        // that starts on a row is a scroll and a swipe that starts on the bezel is a dismissal,
        // and the two are half a centimetre apart — a deliberate way out is worth one row. Centred
        // and in the lume amber, so it reads as the end of the list rather than as one more
        // setting that happens to be last.
        item {
            Chip(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        text = stringResource(R.string.editor_done),
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

/**
 * The seven sections of the list, and the sentinel for none open.
 *
 * Identity, not position: a header's item index moves when a section above it expands, which is
 * why the headers are keyed by [headerKey] and the pinning in `toggleSection` looks items up by
 * key rather than by index.
 */
private const val SECTION_NONE = -1
private const val SECTION_DUTY = 0
private const val SECTION_VIGILANCE = 1
private const val SECTION_SENSORS = 2
private const val SECTION_POSITION = 3
private const val SECTION_DISPLAY = 4
private const val SECTION_UNITS = 5
private const val SECTION_ABOUT = 6

/** Stable identity for a section header in the lazy list, whatever its index this frame. */
private fun headerKey(section: Int): String = "header-" + section

/** How many frames the tapped header is held in place for while the list reflows. */
private const val PIN_FRAMES = 6

/**
 * Ceiling on one repeat tick of a five-minute stepper: three steps, so a quarter of an hour a tick at
 * full speed. Enough to cross sixteen hours in about three seconds and still stop where you meant.
 */
private const val DURATION_MAX_STEP = 3

/**
 * A whole degree per tick at full speed, i.e. about twenty degrees a second — enough to cross the
 * globe in a few seconds now that the unit is a hundredth rather than a tenth.
 */
private const val COORD_MAX_STEP = 100
