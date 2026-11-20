package com.ahu.tlauncher.system

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Metro Manila number coding (UVVRP) by the plate's last digit: Monday 1 and 2, Tuesday 3 and 4, Wednesday 5 and 6,
 * Thursday 7 and 8, Friday 9 and 0, during the hours set in Settings (7–10 AM and 5–8 PM by default).
 * Holidays aren't known to the app. Pure, unit tested.
 */
object Coding {
    const val DEFAULT_HOURS = "7-10, 17-20"

    sealed interface Status {
        data object None : Status
        /** Coding day; [windows] still to come or under way later today. */
        data class Today(val windows: List<Window>) : Status
        /** Coding hours now, until [until]. */
        data class Now(val until: LocalTime) : Status
    }

    data class Window(val start: LocalTime, val end: LocalTime)

    fun day(digit: Int): DayOfWeek? = when (digit) {
        1, 2 -> DayOfWeek.MONDAY
        3, 4 -> DayOfWeek.TUESDAY
        5, 6 -> DayOfWeek.WEDNESDAY
        7, 8 -> DayOfWeek.THURSDAY
        9, 0 -> DayOfWeek.FRIDAY
        else -> null
    }

    /** "7-10, 17-20" or "7:30-10:00": 24-hour times. Anything unreadable is skipped. */
    fun parseHours(text: String): List<Window> = text.split(',', ';').mapNotNull { part ->
        val ends = part.trim().split('-', '–')
        if (ends.size != 2) return@mapNotNull null
        val a = time(ends[0]) ?: return@mapNotNull null
        val b = time(ends[1]) ?: return@mapNotNull null
        if (b > a) Window(a, b) else null
    }

    private fun time(s: String): LocalTime? {
        val p = s.trim().split(':')
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = if (p.size > 1) p[1].trim().toIntOrNull() ?: return null else 0
        if (h !in 0..24 || m !in 0..59) return null
        return if (h == 24) LocalTime.MAX else LocalTime.of(h, m)
    }

    /** [digit] -1 (or any non-digit) means number coding is off. */
    fun status(digit: Int, hours: List<Window>, now: LocalDateTime): Status {
        if (day(digit) != now.dayOfWeek) return Status.None
        val t = now.toLocalTime()
        hours.firstOrNull { t >= it.start && t < it.end }?.let { return Status.Now(it.end) }
        val later = hours.filter { t < it.end }
        return if (later.isEmpty()) Status.None else Status.Today(later)
    }

    /** "7–10 AM, 5–8 PM" style text for the status bar. */
    fun label(s: Status, h24: Boolean): String? = when (s) {
        Status.None -> null
        is Status.Now -> "Coding until ${fmt(s.until, h24)}"
        is Status.Today -> "Coding today " + s.windows.joinToString(", ") { "${fmt(it.start, h24)}–${fmt(it.end, h24)}" }
    }

    fun fmt(t: LocalTime, h24: Boolean): String {
        if (h24) return String.format(java.util.Locale.US, "%d:%02d", t.hour, t.minute)
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        val ap = if (t.hour < 12) "AM" else "PM"
        return if (t.minute == 0) "$h12 $ap" else String.format(java.util.Locale.US, "%d:%02d %s", h12, t.minute, ap)
    }
}
