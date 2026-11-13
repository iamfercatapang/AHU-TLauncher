package com.ahu.tlauncher

import com.ahu.tlauncher.system.WeatherKind
import com.ahu.tlauncher.system.WeatherParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val sample = """{"latitude":14.6,"longitude":121.0,"current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code","is_day":""},"current":{"time":"2026-11-13T10:00","interval":900,"temperature_2m":31.4,"weather_code":2,"is_day":1}}"""

    @Test fun parsesCurrent() {
        val w = WeatherParser.parse(sample, 5L)!!
        assertEquals(31.4, w.tempC, 0.001)
        assertEquals(WeatherKind.PARTLY_CLOUDY, w.kind)
        assertTrue(w.isDay)
        assertEquals(5L, w.fetchedAtMs)
    }

    @Test fun negativeAndNight() {
        val w = WeatherParser.parse("""{"current":{"temperature_2m":-3.5,"weather_code":73,"is_day":0}}""", 0)!!
        assertEquals(-3.5, w.tempC, 0.001)
        assertEquals(WeatherKind.SNOW, w.kind)
        assertFalse(w.isDay)
    }

    @Test fun badReply() { assertNull(WeatherParser.parse("""{"error":true}""", 0)) }

    @Test fun codes() {
        assertEquals(WeatherKind.CLEAR, WeatherParser.kind(0))
        assertEquals(WeatherKind.FOG, WeatherParser.kind(45))
        assertEquals(WeatherKind.RAIN, WeatherParser.kind(63))
        assertEquals(WeatherKind.RAIN, WeatherParser.kind(81))
        assertEquals(WeatherKind.STORM, WeatherParser.kind(95))
    }

    @Test fun urlRoundsLocation() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=14.60&longitude=121.04&current=temperature_2m,weather_code,is_day",
            WeatherParser.url(14.599512, 121.0437),
        )
    }
}
