// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import android.content.Context
import android.location.Location
import com.avdesign.mfd24.Diag
import com.avdesign.mfd24.astro.SolarDay
import com.avdesign.mfd24.astro.SolarTime
import com.avdesign.mfd24.geo.PoiFormat
import com.avdesign.mfd24.geo.PoiResolver
import com.avdesign.mfd24.geo.TzGrid
import com.avdesign.mfd24.geo.ZoneHysteresis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId
import kotlin.math.abs

enum class RefreshOutcome {
    /** Everything that could be refreshed was. */
    OK,

    /** The user has not granted location access; nothing to retry. */
    NO_PERMISSION,

    /** Permission is granted but no fix arrived; worth trying again later. */
    NO_FIX,

    /** We had a fix but the weather call failed; worth retrying. */
    NETWORK_ERROR,
}

/**
 * Single owner of everything the telemetry window shows.
 *
 * Process-wide, because two very different callers need the same data: the watch face service (for
 * rendering, and for an opportunistic refresh when the screen comes on) and the periodic worker.
 * Keeping one instance means the worker's results are visible to the renderer immediately, without
 * a broadcast or a second read of the cache.
 */
class TelemetryRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    val state = TelemetryState()

    private val store = TelemetryStore(appContext)
    private val location = LocationRepository(appContext)
    private val poi = PoiResolver(appContext, state)

    private val refreshLock = Mutex()

    private val solarDay = SolarDay()

    /**
     * The position everything downstream actually uses: a device fix when there is one, otherwise
     * a hand-typed fallback, otherwise NaN. Derived rather than stored, because which of the two
     * sources wins changes the moment a permission is granted or a fix arrives.
     */
    @Volatile
    private var activeLatitude: Double = Double.NaN

    @Volatile
    private var activeLongitude: Double = Double.NaN

    /** Where the 5 km search last ran, so a move of any kind can retrigger it. */
    private val resolveOrigin = ResolveOrigin()

    /**
     * The zone of the position in force, or null before the first lookup or without a grid.
     * Declared above `init` with the window beside it, because `init` already looks the zone up:
     * a field declared below it is still null at that moment, and on a release build that is a
     * NullPointerException with an obfuscated name in `onCreate`, which takes the face down.
     */
    @Volatile
    private var placeZone: ZoneId? = null

    /** The grid's index for [placeZone], which the border band compares against. */
    private var placeZoneIndex = -1
    private var placeZoneSource = TelemetryState.POSITION_NONE
    private var placeZoneLat = Double.NaN
    private var placeZoneLon = Double.NaN

    private val placeWindow = PlaceZone.Window()

    init {
        // Populate from the device-protected cache so the very first frame is not blank.
        store.restoreInto(state)
        state.showCoordinates = store.showCoordinates
        state.compassTicks = store.compassTicks
        adoptBestPosition()
        refreshDaylight(System.currentTimeMillis())
    }

    /**
     * Chooses between the cached device fix and the hand-typed fallback and publishes the result.
     *
     * A device fix wins unless the user has explicitly asked for manual. Both are accurate
     * enough for the site lock now that the manual entry steps in hundredths of a degree, so the
     * choice is about which position is right, not about which features survive.
     */
    @Synchronized
    private fun adoptBestPosition() {
        // An explicit choice of manual outranks a perfectly good device fix: that is the whole
        // point of the switch.
        if (!(store.manualPositionSelected && store.hasManualPosition) &&
            location.hasPermission() && store.hasDevicePosition
        ) {
            activeLatitude = store.deviceLatitude
            activeLongitude = store.deviceLongitude
            state.positionLatDeg = activeLatitude
            state.positionLonDeg = activeLongitude
            state.positionSource = TelemetryState.POSITION_DEVICE
            publishFixAge(TelemetryState.POSITION_DEVICE)
            return
        }
        if (store.hasManualPosition) {
            activeLatitude = store.manualLatitude
            activeLongitude = store.manualLongitude
            state.positionLatDeg = activeLatitude
            state.positionLonDeg = activeLongitude
            state.positionSource = TelemetryState.POSITION_MANUAL
            publishFixAge(TelemetryState.POSITION_MANUAL)
            // Left to the refresh that follows: the site is no longer forfeit just because the
            // position was typed rather than measured.
            return
        }
        activeLatitude = Double.NaN
        activeLongitude = Double.NaN
        state.positionLatDeg = Double.NaN
        state.positionLonDeg = Double.NaN
        state.positionSource = TelemetryState.POSITION_NONE
        poi.clear()
        store.clearSite()
    }

    /**
     * Recomputes the daylight window and the place's clock from the position in force. Cheap,
     * offline and independent of the weather call, so it runs on every refresh and on start-up —
     * the band would otherwise be a day stale after midnight, and the zone's transition window a
     * season stale after a change of summer time.
     */
    @Synchronized
    fun refreshDaylight(nowMillis: Long) {
        val lat = activeLatitude
        val lon = activeLongitude
        publishDialRule()
        if (lat.isNaN() || lon.isNaN()) {
            state.clearDaylight()
            state.clearPlaceZone()
            return
        }
        SolarTime.compute(nowMillis, lat, lon, solarDay)
        state.setDaylight(solarDay.kind, solarDay.sunriseMillis, solarDay.sunsetMillis)
        refreshPlaceZone(lat, lon, nowMillis)
    }

    // --- The place's clock -----------------------------------------------------------------

    /**
     * Whether the measured position sets the dial's clock: `LOCAL TIME` in the position section,
     * offered only while the position is automatic and located. Setting it republishes at once,
     * so the dial glides as the wearer flips it rather than half an hour later.
     */
    var zoneFromPosition: Boolean
        get() = store.zoneFromPosition
        set(value) {
            if (store.zoneFromPosition == value) return
            store.zoneFromPosition = value
            refreshDaylight(System.currentTimeMillis())
        }

    /** `COORDINATES` in the position section; see [TelemetryStore.showCoordinates]. */
    var showCoordinates: Boolean
        get() = store.showCoordinates
        set(value) {
            store.showCoordinates = value
            state.showCoordinates = value
        }

    /** `ACTIVE SN` in the display section; see [TelemetryStore.compassTicks]. */
    var compassTicks: Boolean
        get() = store.compassTicks
        set(value) {
            store.compassTicks = value
            state.compassTicks = value
        }

    /**
     * The zone the dial is set to: the place's when [TelemetryState.dialFollowsPlace], the
     * device's otherwise. The editor formats a booked start with this, so the time it shows is
     * the time the hour hand will point at.
     */
    fun dialZone(): ZoneId {
        val place = placeZone
        return if (state.dialFollowsPlace && place != null) place else ZoneId.systemDefault()
    }

    /**
     * A typed position always brings its clock; a measured one only when the wearer asked for it
     * and the platform still lets us locate. A revoked permission falls back to the phone's zone
     * rather than to a fix that can no longer be refreshed.
     */
    private fun publishDialRule() {
        state.dialFollowsPlace = when (state.positionSource) {
            TelemetryState.POSITION_MANUAL -> true
            TelemetryState.POSITION_DEVICE -> store.zoneFromPosition && location.hasPermission()
            else -> false
        }
    }

    /**
     * Looks the zone up again when the position has moved or changed source; the window is
     * recomputed every time, because that is what rolls it past a transition.
     *
     * A measured position goes through [ZoneHysteresis]: the zone in force is kept while the fix
     * is within `max(3 km, its accuracy)` of it, so a wearer on a border does not watch the dial
     * glide an hour and back on alternate refreshes. A typed one does not: it is a decision, and
     * the zone is the point's own.
     */
    private fun refreshPlaceZone(lat: Double, lon: Double, nowMillis: Long) {
        val source = state.positionSource
        if (placeZone == null || source != placeZoneSource ||
            abs(lat - placeZoneLat) >= ZONE_LOOKUP_DEGREES ||
            abs(lon - placeZoneLon) >= ZONE_LOOKUP_DEGREES
        ) {
            val grid = loadZoneGrid()
            if (grid == null) {
                placeZone = null
                placeZoneIndex = -1
            } else {
                val here = grid.zoneAt(lat, lon)
                var chosen = here
                if (source == TelemetryState.POSITION_DEVICE) {
                    // The zone a measured position was last in, from memory or — first thing after
                    // a restart — from the store.
                    val held = if (placeZoneSource == TelemetryState.POSITION_DEVICE && placeZoneIndex >= 0) {
                        placeZoneIndex
                    } else {
                        store.measuredZoneName?.let { grid.indexOf(it) } ?: -1
                    }
                    if (held >= 0 && held != here && ZoneHysteresis.keeps(
                            grid, lat, lon, ZoneHysteresis.radiusFor(store.deviceAccuracyMeters), held,
                        )
                    ) {
                        chosen = held
                    }
                    val name = grid.names(chosen)[0]
                    if (store.measuredZoneName != name) store.measuredZoneName = name
                }
                if (placeZone == null || chosen != placeZoneIndex) {
                    val known = ZoneId.getAvailableZoneIds()
                    placeZone = PlaceZone.choose(grid.names(chosen), grid.standardOffsetSeconds(chosen)) {
                        it in known
                    }
                    placeZoneIndex = chosen
                }
            }
            placeZoneLat = lat
            placeZoneLon = lon
            placeZoneSource = source
        }
        val zone = placeZone
        if (zone == null) {
            state.clearPlaceZone()
            return
        }
        PlaceZone.window(zone, nowMillis, placeWindow)
        state.setPlaceZone(placeWindow)
    }

    /**
     * The time-zone grid, read and dropped rather than held: 660 KB kept for a question asked when
     * the position moves is a poor trade on a watch, and reading it costs a few milliseconds off
     * the render thread.
     */
    private fun loadZoneGrid(): TzGrid? {
        val bytes = try {
            appContext.assets.open(TzGrid.ASSET_NAME).use { it.readBytes() }
        } catch (e: IOException) {
            Diag.w(TAG, "time-zone grid unavailable", e)
            return null
        }
        return TzGrid.fromBytes(bytes)
    }

    fun hasLocationPermission(): Boolean = location.hasPermission()

    /** True once the platform has given us a real fix, i.e. this is not a cold start. */
    fun hasCachedPosition(): Boolean = store.hasDevicePosition

    /**
     * Called the moment the user grants location access.
     *
     * Worth an active fix rather than waiting: the periodic worker may be half an hour away, and
     * the editor — where the grant happens — is exactly where the user is looking for a position
     * to appear. Any manual fallback stays on file but stops being used as soon as this lands.
     */
    suspend fun onLocationGranted(): RefreshOutcome =
        refresh(allowActiveFix = true, nowMillis = System.currentTimeMillis())

    /**
     * Whether the weather row is wanted at all. Set from the user style; when off nothing is
     * fetched, so the radio stays asleep as well as the row staying empty.
     */
    @Volatile
    var weatherEnabled: Boolean = true

    // --- Manual position -------------------------------------------------------------------

    fun hasManualPosition(): Boolean = store.hasManualPosition

    /**
     * Whether the hand-typed position is being used in place of the device.
     *
     * Setting it re-resolves immediately: switching back to automatic asks for a fresh fix rather
     * than waiting on the half-hourly worker, which is the whole reason anyone flips it back.
     */
    var manualPositionSelected: Boolean
        get() = store.manualPositionSelected
        set(value) {
            if (store.manualPositionSelected == value) return
            store.manualPositionSelected = value
            adoptBestPosition()
            refreshDaylight(System.currentTimeMillis())
        }

    fun manualLatitude(): Double = store.manualLatitude

    fun manualLongitude(): Double = store.manualLongitude

    /**
     * The position in force, or NaN. The editor seeds its coordinate steppers from this: starting
     * at 0.00 means a long hold to reach anywhere real, and wherever you are now is a far better
     * guess than the Gulf of Guinea.
     */
    fun currentLatitude(): Double = activeLatitude

    fun currentLongitude(): Double = activeLongitude

    /**
     * Records a hand-typed position and applies it, unless a real fix is already in force — in
     * which case it is kept on file as a fallback and nothing on the dial changes.
     */
    suspend fun setManualPosition(latitude: Double, longitude: Double) {
        store.saveManualPosition(latitude, longitude)
        // Typing a position in and pressing apply *is* choosing it; anything else leaves the user
        // staring at a device fix wondering why their coordinates did nothing.
        store.manualPositionSelected = true
        adoptBestPosition()
        refreshDaylight(System.currentTimeMillis())
        // Site and weather both follow the position, and both want to be off the main thread: the
        // first POI query reads a 138 KB asset and the weather call is a network round trip.
        refresh(allowActiveFix = false, nowMillis = System.currentTimeMillis())
    }

    fun clearManualPosition() {
        val wasInForce = state.positionSource == TelemetryState.POSITION_MANUAL
        store.clearManualPosition()
        store.manualPositionSelected = false
        if (wasInForce) {
            adoptBestPosition()
            state.clearWeather()
            refreshDaylight(System.currentTimeMillis())
        }
    }

    /**
     * Brings position, site and weather up to date.
     *
     * @param allowActiveFix when false only a cached fix is used, so the screen coming on never
     *   wakes the GPS; the periodic worker passes true.
     */
    suspend fun refresh(allowActiveFix: Boolean, nowMillis: Long): RefreshOutcome =
        refreshLock.withLock {
            withContext(Dispatchers.IO) {
                val outcome = refreshLocked(allowActiveFix, nowMillis)
                Diag.d(
                    TAG,
                    "refresh(activeFix=$allowActiveFix) -> $outcome " +
                        "[site=${state.siteValid} weather=${state.weatherValid}]",
                )
                outcome
            }
        }

    private suspend fun refreshLocked(allowActiveFix: Boolean, nowMillis: Long): RefreshOutcome {
        // With manual selected there is nothing to ask the platform for: the position is already
        // decided, and waking the GPS to then ignore the answer is the worst of both.
        val manualChosen = store.manualPositionSelected && store.hasManualPosition
        // A fix older than the one on file is no news: `lastKnown` picks the newest per provider,
        // and a provider that has not heard anything since hands back what it had hours ago.
        // Taken as new, it moved the position back and reset its age.
        val fix = if (!manualChosen && location.hasPermission()) {
            obtainFix(allowActiveFix, nowMillis)?.takeIf { it.time >= store.deviceFixMillis }
        } else {
            null
        }

        val lat: Double
        val lon: Double
        if (fix != null) {
            lat = fix.latitude
            lon = fix.longitude
            // The same fix read again keeps the speed it was saved with: recomputed against
            // itself it would come out as standing still.
            val previousMillis = store.deviceFixMillis
            val speed = if (fix.time == previousMillis) {
                store.deviceSpeedMps
            } else {
                PositionAge.speedFor(
                    if (fix.hasSpeed()) fix.speed else -1f, lat, lon, fix.time,
                    store.deviceLatitude, store.deviceLongitude, previousMillis,
                )
            }
            store.saveDevicePosition(
                lat, lon, if (fix.hasAccuracy()) fix.accuracy else Float.NaN, fix.time, speed,
            )
            setActivePosition(lat, lon, TelemetryState.POSITION_DEVICE)
            maybeResolveSite(lat, lon)
        } else if (!manualChosen && location.hasPermission() && store.hasDevicePosition) {
            // The platform said nothing, not "nowhere". A wallpaper and a worker are both
            // background callers, and on API 29+ every provider hands them null without
            // ACCESS_BACKGROUND_LOCATION — clearing the state on that answer threw away a
            // perfectly good cached fix every half hour and left the dial blank between visits
            // to the editor, which is the only foreground this app has. The cache wins over a
            // manual fallback for the same reason adoptBestPosition gives the device first
            // refusal: manual only rules when the user chose it.
            lat = store.deviceLatitude
            lon = store.deviceLongitude
            setActivePosition(lat, lon, TelemetryState.POSITION_DEVICE)
            maybeResolveSite(lat, lon)
        } else if (store.hasManualPosition) {
            lat = store.manualLatitude
            lon = store.manualLongitude
            setActivePosition(lat, lon, TelemetryState.POSITION_MANUAL)
            maybeResolveSite(lat, lon)
        } else {
            setActivePosition(Double.NaN, Double.NaN, TelemetryState.POSITION_NONE)
            poi.clear()
            state.clearWeather()
            state.clearDaylight()
            state.clearPlaceZone()
            return if (location.hasPermission()) {
                RefreshOutcome.NO_FIX
            } else {
                RefreshOutcome.NO_PERMISSION
            }
        }

        refreshDaylight(nowMillis)

        if (!weatherEnabled) {
            state.clearWeather()
            return RefreshOutcome.OK
        }

        // Age *and* distance. A reading is of one place as well as one moment, and the watch can
        // leave the place a great deal faster than the reading goes off.
        val stale = WeatherCache.isStale(
            store.weatherObservedAt, store.weatherLatitude, store.weatherLongitude,
            nowMillis, lat, lon,
        )
        if (!stale && state.weatherValid) {
            return RefreshOutcome.OK
        }

        // Locked to an aerodrome, the row shows that field's own METAR — the observation its
        // tower reads out, not a model cell that happens to contain it. Any miss falls back to
        // Open-Meteo, and the miss is not a corner case: most sites never had a METAR, and whole
        // regions stop publishing in wartime.
        val sample = metarIcao()?.let { MetarClient.fetch(it, nowMillis) }
            ?: OpenMeteoClient.fetch(lat, lon, nowMillis)
        if (sample == null) {
            Diag.i(TAG, "Weather refresh failed; keeping the cached observation")
            return RefreshOutcome.NETWORK_ERROR
        }
        state.setWeather(
            sample.temperatureDeciC,
            sample.pressureDeciHpa,
            sample.conditionIndex,
            sample.observedAt,
        )
        store.saveWeather(
            sample.temperatureDeciC,
            sample.pressureDeciHpa,
            sample.conditionIndex,
            sample.observedAt,
            lat,
            lon,
        )
        return RefreshOutcome.OK
    }

    private suspend fun obtainFix(allowActiveFix: Boolean, nowMillis: Long): Location? {
        val cached = location.lastKnown()
        if (cached != null && nowMillis - cached.time < FIX_TTL_MS) return cached
        if (!allowActiveFix) return cached
        return location.current() ?: cached
    }

    /**
     * Runs the site search when it could say something new: the position has left the last
     * search's 500 m, or there is no site on the dial to be stale.
     */
    private fun maybeResolveSite(lat: Double, lon: Double) {
        if (resolveOrigin.movedBeyond(lat, lon, RESOLVE_DISTANCE_M) || !state.siteValid) {
            resolveSite(lat, lon)
        }
    }

    /** Finds the nearest site, publishes it to the state and mirrors it into the cache. */
    private fun resolveSite(lat: Double, lon: Double) {
        // Recorded beside the search, never in the distance check: see [ResolveOrigin] for the
        // two failure modes that placement is guarding against.
        resolveOrigin.record(lat, lon)
        val hit = poi.resolve(lat, lon)
        if (hit == null) {
            store.clearSite()
            return
        }
        // The same rounding the resolver published to the state, so the row does not shift by a
        // tenth of a kilometre when it is restored from the cache.
        store.saveSite(
            String(hit.code, 0, hit.codeLength),
            hit.type,
            hit.flags,
            state.siteDistanceHm,
        )
    }

    /**
     * The locked site's ICAO, or null when the site is not METAR-worthy: not an aerodrome, a
     * helipad, or one of the few fields whose code is a local identifier because no ICAO was
     * ever assigned. Four upper-case letters is the gate — the airports data carries ICAO only,
     * a decision made after a mixed column burned once (Hostomel was ICAO, Zhuliany was IATA).
     */
    private fun metarIcao(): String? {
        if (!state.siteValid || state.siteType != PoiFormat.TYPE_AIRPORT) return null
        if (state.siteFlags and PoiFormat.FLAG_HELIPAD != 0) return null
        val code = CharArray(8)
        val length = state.copySiteCode(code)
        if (length != 4) return null
        for (i in 0 until length) if (code[i] !in 'A'..'Z') return null
        return String(code, 0, length)
    }

    @Synchronized
    private fun setActivePosition(lat: Double, lon: Double, source: Int) {
        activeLatitude = lat
        activeLongitude = lon
        state.positionLatDeg = lat
        state.positionLonDeg = lon
        state.positionSource = source
        publishFixAge(source)
    }

    /** Only a measured position ages; a typed one is a decision, see [PositionAge]. */
    private fun publishFixAge(source: Int) {
        if (source == TelemetryState.POSITION_DEVICE) {
            state.positionFixMillis = store.deviceFixMillis
            state.positionSpeedMps = store.deviceSpeedMps
        } else {
            state.positionFixMillis = 0L
            state.positionSpeedMps = 0f
        }
    }

    companion object {
        private const val TAG = "TelemetryRepository"

        /** How stale a cached fix may be before we ask for a new one. */
        const val FIX_TTL_MS: Long = 15 * 60 * 1000L

        /**
         * The step of a typed position, and a fifth of the time-zone grid's cell: any change of a
         * manual position looks the zone up again, while a device fix's few metres of jitter does
         * not re-read the grid every half hour.
         */
        const val ZONE_LOOKUP_DEGREES: Double = 0.01

        /** Re-running the 5 km search below this displacement cannot change the answer much. */
        const val RESOLVE_DISTANCE_M: Double = 500.0

        @Volatile
        private var instance: TelemetryRepository? = null

        fun get(context: Context): TelemetryRepository =
            instance ?: synchronized(this) {
                instance ?: TelemetryRepository(context).also { instance = it }
            }
    }
}
