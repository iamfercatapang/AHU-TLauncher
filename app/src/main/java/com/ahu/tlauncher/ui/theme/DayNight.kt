package com.ahu.tlauncher.ui.theme

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class ThemeMode { AUTO, DARK, LIGHT }

/** Pure day/night logic so it can be unit tested. */
object DayNight {
    val FALLBACK_SUNRISE: LocalTime = LocalTime.of(7, 0)
    val FALLBACK_SUNSET: LocalTime = LocalTime.of(19, 0)

    fun isDark(mode: ThemeMode, now: ZonedDateTime, lat: Double?, lon: Double?): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> {
            val (rise, set) = sunTimes(now.toLocalDate(), now.zone, lat, lon)
                ?: (FALLBACK_SUNRISE to FALLBACK_SUNSET)
            val t = now.toLocalTime()
            t.isBefore(rise) || !t.isBefore(set)
        }
    }

    /**
     * Sunrise/sunset in the given zone using the standard NOAA "sunrise equation"
     * (accurate to a couple of minutes, plenty for theme switching).
     * Returns null when location is unknown or the sun doesn't rise/set (polar day/night).
     */
    fun sunTimes(date: LocalDate, zone: ZoneId, lat: Double?, lon: Double?): Pair<LocalTime, LocalTime>? {
        if (lat == null || lon == null) return null
        val rise = calc(date, zone, lat, lon, true) ?: return null
        val set = calc(date, zone, lat, lon, false) ?: return null
        return rise to set
    }

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun norm(x: Double, m: Double) = ((x % m) + m) % m

    private fun calc(date: LocalDate, zone: ZoneId, lat: Double, lon: Double, rising: Boolean): LocalTime? {
        val zenith = 90.833
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(zenith)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val localMean = h + ra - 0.06571 * t - 6.622
        val utc = norm(localMean - lngHour, 24.0)
        val secs = (utc * 3600).toLong()
        val utcTime = date.atStartOfDay(ZoneId.of("UTC")).plusSeconds(secs)
        return utcTime.withZoneSameInstant(zone).toLocalTime()
    }
}
