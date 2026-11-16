// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent cache for the last known weather, position and site.
 *
 * It lives in **device-protected** storage on purpose. The watch face service is
 * `directBootAware`, so it can be asked to render before the user has unlocked the watch, and
 * credential-encrypted storage is simply not readable at that point. Keeping the cache here means
 * the telemetry window comes up populated straight after a reboot instead of showing `WX ---`
 * until first unlock.
 */
class TelemetryStore(context: Context) {

    private val prefs: SharedPreferences = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // --- Device fix ------------------------------------------------------------------------

    /**
     * The last position the platform actually gave us, and nothing else.
     *
     * A hand-typed position used to be written here too, which quietly cost the app a real fix: it
     * made [hasDevicePosition] true, so the cold-start branch stopped asking for one, and it gave
     * the site search's [ResolveOrigin] a fictional point to measure from. The two now live in separate
     * keys and only the device may write these.
     */
    val deviceLatitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_LAT, NO_FIX_BITS))

    val deviceLongitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_LON, NO_FIX_BITS))

    val hasDevicePosition: Boolean
        get() = !deviceLatitude.isNaN() && !deviceLongitude.isNaN()

    /**
     * The fix's reported accuracy in metres, NaN when the platform gave none. Kept beside the fix
     * because the time-zone border band widens to it: a fix that says it is good to 5 km is a
     * cloud 5 km wide, wherever its centre happens to fall.
     */
    val deviceAccuracyMeters: Float
        get() = prefs.getFloat(KEY_ACCURACY, Float.NaN)

    /**
     * When the fix was taken, by the platform's own clock on it — not when it was read, because
     * `lastKnown` hands back fixes hours old as readily as fresh ones. 0 when unknown, which is
     * every fix stored before 2.11.0: such a fix is treated as standing still, see [PositionAge].
     */
    val deviceFixMillis: Long
        get() = prefs.getLong(KEY_FIX_TIME, 0L)

    /** Ground speed at the fix in metres a second, 0 when standing or unknown. See [PositionAge]. */
    val deviceSpeedMps: Float
        get() = prefs.getFloat(KEY_SPEED, 0f)

    fun saveDevicePosition(
        lat: Double,
        lon: Double,
        accuracyMeters: Float,
        fixMillis: Long,
        speedMps: Float,
    ) {
        prefs.edit()
            .putLong(KEY_LAT, lat.toRawBits())
            .putLong(KEY_LON, lon.toRawBits())
            .putFloat(KEY_ACCURACY, accuracyMeters)
            .putLong(KEY_FIX_TIME, fixMillis)
            .putFloat(KEY_SPEED, speedMps)
            .apply()
    }

    /**
     * Whether the site row prints the position when there is no site to name, rather than
     * `NO SITE`. Off by default: a screenshot of the dial would otherwise carry where the wearer
     * lives to a kilometre. Outside the style schema like [zoneFromPosition], for the same reason.
     */
    var showCoordinates: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COORDINATES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_COORDINATES, value).apply()

    /**
     * Whether the meridian's two cardinal ticks are drawn as a compass — red north, white south —
     * while the screen is on. Off by default, like every mark this dial adds to its own scale.
     */
    var compassTicks: Boolean
        get() = prefs.getBoolean(KEY_COMPASS_TICKS, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPASS_TICKS, value).apply()

    /**
     * Whether a changed character on the dial's rows rolls into place, see
     * [com.avdesign.mfd24.render.DrumRow]. Off by default: it is ornament, and it buys its
     * movement with fast frames, so the wearer chooses it. Outside the style schema so that adding
     * it reset nothing.
     */
    var rollingDigits: Boolean
        get() = prefs.getBoolean(KEY_ROLLING_DIGITS, false)
        set(value) = prefs.edit().putBoolean(KEY_ROLLING_DIGITS, value).apply()

    /**
     * Whether a measured position sets the dial's clock, rather than the phone's zone doing so.
     * Off by default: the phone's zone is the one every other screen on the watch shows, and a
     * dial that disagrees with them should be something the wearer asked for. Here and not in the
     * style schema, like the position choice beside it, so that adding it did not reset every
     * wearer's settings on update.
     */
    var zoneFromPosition: Boolean
        get() = prefs.getBoolean(KEY_ZONE_FROM_POSITION, false)
        set(value) = prefs.edit().putBoolean(KEY_ZONE_FROM_POSITION, value).apply()

    /**
     * The zone a measured position was last placed in, by the grid's newest name. The border band
     * compares a new fix against it, so it has to outlive the process: a face restarted on a border
     * — an update, a reboot — would otherwise meet its first fix with no zone to hold on to, and
     * hand the dial to whichever side the fix fell. A name rather than the grid's index, so a new
     * grid in a later release cannot turn it into a different zone.
     */
    var measuredZoneName: String?
        get() = prefs.getString(KEY_MEASURED_ZONE, null)
        set(value) = prefs.edit().putString(KEY_MEASURED_ZONE, value).apply()

    // --- Manual position -------------------------------------------------------------------

    /**
     * A position typed in on the watch, for when location is switched off or refused. Kept apart
     * from the cached device fix so the two never get confused: only a real fix earns the site
     * lock.
     */
    /**
     * Whether the user has asked for the manual position to be used *instead of* the device.
     *
     * Separate from whether one is on file, because the two answer different questions: a manual
     * position can sit there as a fallback for when the platform gives us nothing, or it can be
     * the deliberate choice. Nothing here is derivable from the OS permission -- on API 30 an app
     * cannot hand its own location permission back, so "stop using my location" has to be the
     * app's own switch or it cannot exist at all.
     */
    var manualPositionSelected: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_SELECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_SELECTED, value).apply()

    val hasManualPosition: Boolean
        get() = prefs.contains(KEY_MANUAL_LAT)

    val manualLatitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_MANUAL_LAT, NO_FIX_BITS))

    val manualLongitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_MANUAL_LON, NO_FIX_BITS))

    fun saveManualPosition(lat: Double, lon: Double) {
        prefs.edit()
            .putLong(KEY_MANUAL_LAT, lat.toRawBits())
            .putLong(KEY_MANUAL_LON, lon.toRawBits())
            .apply()
    }

    fun clearManualPosition() {
        prefs.edit().remove(KEY_MANUAL_LAT).remove(KEY_MANUAL_LON).apply()
    }

    /**
     * The weather readings kept per place, as [WeatherPlaces.encode] wrote them. A store written
     * before there was more than one reading still has that one under the old keys; it is read as
     * a place of its own, so an update does not empty the row.
     */
    fun loadWeatherPlaces(): WeatherPlaces {
        val text = prefs.getString(KEY_WX_PLACES, null)
        if (text != null) return WeatherPlaces.decode(text)
        val places = WeatherPlaces()
        val at = prefs.getLong(KEY_WX_AT, 0L)
        val lat = Double.fromBits(prefs.getLong(KEY_WX_LAT, NO_FIX_BITS))
        val lon = Double.fromBits(prefs.getLong(KEY_WX_LON, NO_FIX_BITS))
        if (at > 0L && !lat.isNaN() && !lon.isNaN()) {
            places.put(
                WeatherPlaces.Entry(
                    lat, lon, at, at, false,
                    prefs.getInt(KEY_WX_TEMP, 0),
                    prefs.getInt(KEY_WX_PRESSURE, 0),
                    prefs.getInt(KEY_WX_CONDITION, WeatherCondition.UNKNOWN),
                ),
            )
        }
        return places
    }

    fun saveWeatherPlaces(places: WeatherPlaces) {
        prefs.edit()
            .putString(KEY_WX_PLACES, places.encode())
            .remove(KEY_WX_TEMP).remove(KEY_WX_PRESSURE).remove(KEY_WX_CONDITION)
            .remove(KEY_WX_AT).remove(KEY_WX_LAT).remove(KEY_WX_LON)
            .apply()
    }

    /**
     * Minutes between weather requests, one of [WeatherSchedule.INTERVAL_MINUTES]. Outside the
     * style schema like [zoneFromPosition], so adding it reset nobody's settings.
     */
    var weatherIntervalMinutes: Int
        get() = WeatherSchedule.intervalMinutes(
            prefs.getInt(KEY_WX_INTERVAL, WeatherSchedule.DEFAULT_INTERVAL_MINUTES),
        )
        set(value) = prefs.edit()
            .putInt(KEY_WX_INTERVAL, WeatherSchedule.intervalMinutes(value)).apply()

    fun saveSite(code: String, type: Int, flags: Int, distanceHm: Int) {
        prefs.edit()
            .putString(KEY_SITE_CODE, code)
            .putInt(KEY_SITE_TYPE, type)
            .putInt(KEY_SITE_FLAGS, flags)
            .putInt(KEY_SITE_DISTANCE, distanceHm)
            .apply()
    }

    fun clearSite() {
        prefs.edit().remove(KEY_SITE_CODE).apply()
    }

    /**
     * Repopulates [state] from the cache so the first frame after a reboot is not blank. The
     * weather is not among it: which reading belongs on the dial depends on the position, so the
     * repository picks it from [loadWeatherPlaces] once the position is decided.
     */
    fun restoreInto(state: TelemetryState) {
        val code = prefs.getString(KEY_SITE_CODE, null)
        if (code != null && code.isNotEmpty()) {
            val chars = CharArray(code.length)
            code.toCharArray(chars, 0, 0, code.length)
            state.setSite(
                chars,
                chars.size,
                prefs.getInt(KEY_SITE_TYPE, 0),
                prefs.getInt(KEY_SITE_FLAGS, 0),
                prefs.getInt(KEY_SITE_DISTANCE, 0),
            )
        } else {
            state.clearSite()
        }
    }

    private companion object {
        const val NAME = "mfd24_telemetry"

        val NO_FIX_BITS: Long = Double.NaN.toRawBits()

        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_ACCURACY = "accuracy"
        const val KEY_FIX_TIME = "fix_time"
        const val KEY_SPEED = "speed"
        const val KEY_SHOW_COORDINATES = "show_coordinates"
        const val KEY_COMPASS_TICKS = "compass_ticks"
        const val KEY_ROLLING_DIGITS = "rolling_digits"
        const val KEY_ZONE_FROM_POSITION = "zone_from_position"
        const val KEY_MEASURED_ZONE = "measured_zone"
        const val KEY_MANUAL_SELECTED = "manual_selected"
        const val KEY_MANUAL_LAT = "manual_lat"
        const val KEY_MANUAL_LON = "manual_lon"
        const val KEY_WX_TEMP = "wx_temp"
        const val KEY_WX_PRESSURE = "wx_pressure"
        const val KEY_WX_CONDITION = "wx_condition"
        const val KEY_WX_AT = "wx_at"
        const val KEY_WX_LAT = "wx_lat"
        const val KEY_WX_LON = "wx_lon"
        const val KEY_WX_PLACES = "wx_places"
        const val KEY_WX_INTERVAL = "wx_interval"
        const val KEY_SITE_CODE = "site_code"
        const val KEY_SITE_TYPE = "site_type"
        const val KEY_SITE_FLAGS = "site_flags"
        const val KEY_SITE_DISTANCE = "site_distance"
    }
}
