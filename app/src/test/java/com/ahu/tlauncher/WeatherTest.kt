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
            "https://api.open-meteo.com/v1/forecast?latitude=14.60&longitude=121.04" +
                "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day&forecast_hours=7&timezone=auto",
            WeatherParser.url(14.599512, 121.0437),
        )
    }

    private val withHourly = """{"current_units":{"time":"iso8601"},"current":{"time":"2026-11-13T10:15","interval":900,"temperature_2m":31.4,"weather_code":2,"is_day":1,"apparent_temperature":36.2,"relative_humidity_2m":70,"wind_speed_10m":12.5},""" +
        """"hourly_units":{"time":"iso8601"},"hourly":{"time":["2026-11-13T10:00","2026-11-13T11:00","2026-11-13T12:00","2026-11-13T23:00","2026-11-14T00:00"],""" +
        """"temperature_2m":[31.0,32.1,33.0,26.5,25.9],"weather_code":[2,3,61,0,95],"precipitation_probability":[10,20,null,0,80],"is_day":[1,1,1,0,0]}}"""

    @Test fun parsesExtrasAndNextHours() {
        val w = WeatherParser.parse(withHourly, 0)!!
        assertEquals(36.2, w.feelsC!!, 0.001)
        assertEquals(70, w.humidityPct)
        assertEquals(12.5, w.windKmh!!, 0.001)
        // 10:00 is the current hour (before 10:15), so the list starts at 11:00 and runs past midnight.
        assertEquals(listOf(11, 12, 23, 0), w.hourly.map { it.hour })
        assertEquals(WeatherKind.RAIN, w.hourly[1].kind)
        assertNull(w.hourly[1].rainPct)
        assertEquals(80, w.hourly[3].rainPct)
        assertFalse(w.hourly[2].isDay)
    }

    @Test fun hourlyCappedAndOptional() {
        assertEquals(2, WeatherParser.hourly(withHourly, null, max = 2).size)
        // Older replies without the hourly block still parse.
        assertTrue(WeatherParser.parse(sample, 0)!!.hourly.isEmpty())
    }
}
