package com.ahu.tlauncher

import com.ahu.tlauncher.ui.theme.DayNight
import com.ahu.tlauncher.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DayNightTest {
    private val manila = ZoneId.of("Asia/Manila")
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(LocalDate.of(2026, 11, 13), LocalTime.of(h, m), manila)

    @Test fun manualModes() {
        assertTrue(DayNight.isDark(ThemeMode.DARK, at(12), null, null))
        assertFalse(DayNight.isDark(ThemeMode.LIGHT, at(23), null, null))
    }

    @Test fun fallbackWithoutLocation() {
        assertTrue(DayNight.isDark(ThemeMode.AUTO, at(6, 59), null, null))
        assertFalse(DayNight.isDark(ThemeMode.AUTO, at(7, 0), null, null))
        assertFalse(DayNight.isDark(ThemeMode.AUTO, at(18, 59), null, null))
        assertTrue(DayNight.isDark(ThemeMode.AUTO, at(19, 0), null, null))
    }

    @Test fun manilaSunTimesAreReasonable() {
        // Manila mid-November: sunrise ≈ 05:58, sunset ≈ 17:25.
        val (rise, set) = DayNight.sunTimes(LocalDate.of(2026, 11, 13), manila, 14.5995, 120.9842)!!
        assertTrue("sunrise $rise", rise.isAfter(LocalTime.of(5, 45)) && rise.isBefore(LocalTime.of(6, 10)))
        assertTrue("sunset $set", set.isAfter(LocalTime.of(17, 15)) && set.isBefore(LocalTime.of(17, 40)))
        assertTrue(DayNight.isDark(ThemeMode.AUTO, at(17, 50), 14.5995, 120.9842))
        assertFalse(DayNight.isDark(ThemeMode.AUTO, at(6, 30), 14.5995, 120.9842))
    }

    @Test fun polarNightReturnsNullSoFallbackApplies() {
        val r = DayNight.sunTimes(LocalDate.of(2026, 12, 21), ZoneId.of("Europe/Oslo"), 78.2, 15.6)
        assertTrue(r == null)
        assertNotNull(DayNight.isDark(ThemeMode.AUTO, at(12), 78.2, 15.6))
    }
}
