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

data class Weather(val tempC: Double, val kind: WeatherKind, val isDay: Boolean, val fetchedAtMs: Long)

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
        return Weather(t, kind(code), day, nowMs)
    }

    fun url(lat: Double, lon: Double): String =
        // ~1 km precision is plenty for weather and keeps the exact position private.
        String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day", lat, lon)

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
