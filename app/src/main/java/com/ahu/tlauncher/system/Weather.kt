package com.ahu.tlauncher.system

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** One hour of forecast. [hour] is 0–23 local time; [rainPct] is the chance of precipitation. */
data class HourForecast(val hour: Int, val tempC: Double, val kind: WeatherKind, val isDay: Boolean, val rainPct: Int?)

data class Weather(
    val tempC: Double,
    val kind: WeatherKind,
    val isDay: Boolean,
    val fetchedAtMs: Long,
    val feelsC: Double? = null,
    val humidityPct: Int? = null,
    val windKmh: Double? = null,
    /** The next hours after the current one, for the status bar's weather popup. */
    val hourly: List<HourForecast> = emptyList(),
)

/** Pure parsing helpers, unit tested. */
object WeatherParser {
    /** WMO weather interpretation code (Open-Meteo `weather_code`). */
    fun kind(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..57 -> WeatherKind.DRIZZLE
        in 61..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUDY
    }

    /** Reads the `"current":{…}` block of an Open-Meteo forecast reply. */
    fun parse(json: String, nowMs: Long): Weather? {
        val start = json.indexOf("\"current\":{").takeIf { it >= 0 } ?: return null
        val block = json.substring(start, json.indexOf('}', start).takeIf { it > 0 } ?: return null)
        fun num(key: String) = Regex("\"$key\":(-?[0-9.]+)").find(block)?.groupValues?.get(1)?.toDoubleOrNull()
        val t = num("temperature_2m") ?: return null
        val code = num("weather_code")?.toInt() ?: return null
        val day = (num("is_day") ?: 1.0) >= 1.0
        val now = Regex("\"time\":\"([^\"]+)\"").find(block)?.groupValues?.get(1)
        return Weather(
            t, kind(code), day, nowMs,
            feelsC = num("apparent_temperature"),
            humidityPct = num("relative_humidity_2m")?.toInt(),
            windKmh = num("wind_speed_10m"),
            hourly = hourly(json, now),
        )
    }

    /** Reads the `"hourly":{…}` arrays: hours strictly after [now] (local ISO time), at most [max]. */
    fun hourly(json: String, now: String?, max: Int = HOURS): List<HourForecast> {
        val start = json.indexOf("\"hourly\":{").takeIf { it >= 0 } ?: return emptyList()
        val end = json.indexOf('}', start).takeIf { it > 0 } ?: return emptyList()
        val block = json.substring(start, end)
        fun arr(key: String): List<String> =
            Regex("\"$key\":\\[([^\\]]*)\\]").find(block)?.groupValues?.get(1)?.split(',')?.map { it.trim().removeSurrounding("\"") } ?: emptyList()
        val times = arr("time")
        val temps = arr("temperature_2m")
        val codes = arr("weather_code")
        val rain = arr("precipitation_probability")
        val days = arr("is_day")
        return times.indices.mapNotNull { i ->
            val time = times[i]
            if (now != null && time <= now) return@mapNotNull null
            val hour = time.substringAfter('T', "").take(2).toIntOrNull() ?: return@mapNotNull null
            val temp = temps.getOrNull(i)?.toDoubleOrNull() ?: return@mapNotNull null
            val code = codes.getOrNull(i)?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            HourForecast(hour, temp, kind(code), (days.getOrNull(i)?.toDoubleOrNull() ?: 1.0) >= 1.0, rain.getOrNull(i)?.toDoubleOrNull()?.toInt())
        }.take(max)
    }

    /** Forecast hours shown in the popup. */
    const val HOURS = 6

    fun url(lat: Double, lon: Double): String =
        // ~1 km precision is plenty for weather and keeps the exact position private.
        String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day&forecast_hours=${HOURS + 1}&timezone=auto",
            lat, lon,
        )

    /** Hide readings older than this (e.g. no internet for a while). */
    const val MAX_AGE_MS = 2 * 60 * 60 * 1000L
}

/** Current weather from Open-Meteo (free, no API key), refreshed every 30 minutes. */
class WeatherRepository(scope: CoroutineScope, location: StateFlow<Location?>) {
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    init {
        scope.launch {
            location.filterNotNull().first()
            while (true) {
                val loc = location.value
                val ok = loc != null && fetch(loc.latitude, loc.longitude)
                delay(if (ok) 30 * 60_000L else 5 * 60_000L)
            }
        }
    }

    private suspend fun fetch(lat: Double, lon: Double): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(WeatherParser.url(lat, lon)).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            try {
                if (c.responseCode != 200) return@runCatching false
                val w = WeatherParser.parse(c.inputStream.bufferedReader().readText(), System.currentTimeMillis()) ?: return@runCatching false
                _weather.value = w
                true
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }
}
