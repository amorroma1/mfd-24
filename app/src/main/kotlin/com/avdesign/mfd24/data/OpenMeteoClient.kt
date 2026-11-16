// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.data

import com.avdesign.mfd24.Diag
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * One observation, already scaled into the integer units the renderer works in. [observedAt] is
 * when it was fetched, which the cache ages from; [issuedAt] is the source's own observation time
 * where it has one — a METAR's — which says when the next report will be out.
 */
data class WeatherSample(
    val temperatureDeciC: Int,
    val pressureDeciHpa: Int,
    val conditionIndex: Int,
    val observedAt: Long,
    val issuedAt: Long = observedAt,
    val metar: Boolean = false,
)

/**
 * Weather from Open-Meteo.
 *
 * Chosen because it needs no API key and no account, which keeps the watch face installable
 * straight from a build. `pressure_msl` is mean-sea-level pressure, which is QNH by definition — no
 * conversion is needed for the altimeter setting shown on the dial.
 *
 * Parsed with the platform `org.json` rather than a serialisation library: the payload is three
 * numbers and pulling in Moshi or kotlinx-serialization for it would be pure weight.
 */
object OpenMeteoClient {

    private const val TAG = "OpenMeteoClient"
    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    // Short, because a METAR that does not answer is followed by Open-Meteo in the same try,
    // and fifteen seconds apiece held one try for most of a minute on a network that was not there.
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000

    /** Blocking; call from a background dispatcher. Returns null on any failure. */
    fun fetch(latitude: Double, longitude: Double, nowMillis: Long): WeatherSample? {
        val url = String.format(
            Locale.US,
            "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,pressure_msl" +
                "&timezone=UTC",
            ENDPOINT, latitude, longitude,
        )

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                Diag.w(TAG, "Open-Meteo returned HTTP $status")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body, nowMillis)
        } catch (e: IOException) {
            Diag.w(TAG, "Weather fetch failed", e)
            null
        } catch (e: org.json.JSONException) {
            Diag.w(TAG, "Malformed weather payload", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parse(body: String, nowMillis: Long): WeatherSample? {
        val current = JSONObject(body).optJSONObject("current") ?: return null
        if (!current.has("temperature_2m")) return null
        val temperature = current.getDouble("temperature_2m")
        val pressure = current.optDouble("pressure_msl", Double.NaN)
        val code = current.optInt("weather_code", -1)
        return WeatherSample(
            temperatureDeciC = (temperature * 10.0).roundToInt(),
            pressureDeciHpa = if (pressure.isNaN()) 0 else (pressure * 10.0).roundToInt(),
            conditionIndex = if (code < 0) WeatherCondition.UNKNOWN else WeatherCondition.fromWmoCode(code),
            observedAt = nowMillis,
        )
    }
}
