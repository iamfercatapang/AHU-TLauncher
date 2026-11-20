package com.ahu.tlauncher

import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.system.Coding
import com.ahu.tlauncher.system.Maintenance
import com.ahu.tlauncher.system.MaintenanceMath
import com.ahu.tlauncher.system.ServiceItem
import com.ahu.tlauncher.system.ServiceRecord
import com.ahu.tlauncher.system.Trip
import com.ahu.tlauncher.system.TripMath
import com.ahu.tlauncher.system.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CodingTest {
    private val hours = Coding.parseHours(Coding.DEFAULT_HOURS)
    // 2026-11-16 is a Monday
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    @Test fun parsesHours() {
        assertEquals(listOf(Coding.Window(LocalTime.of(7, 0), LocalTime.of(10, 0)), Coding.Window(LocalTime.of(17, 0), LocalTime.of(20, 0))), hours)
        assertEquals(listOf(Coding.Window(LocalTime.of(7, 30), LocalTime.of(9, 0))), Coding.parseHours("7:30-9, nonsense, 10-8"))
    }

    @Test fun days() {
        assertEquals(java.time.DayOfWeek.MONDAY, Coding.day(1))
        assertEquals(java.time.DayOfWeek.FRIDAY, Coding.day(0))
        assertNull(Coding.day(-1))
    }

    @Test fun status() {
        assertEquals(Coding.Status.None, Coding.status(3, hours, at(16, 8))) // Monday, plate ends in 3
        assertEquals(Coding.Status.Now(LocalTime.of(10, 0)), Coding.status(1, hours, at(16, 8)))
        assertEquals(Coding.Status.Today(hours), Coding.status(2, hours, at(16, 6)))
        assertEquals(Coding.Status.Today(hours.drop(1)), Coding.status(2, hours, at(16, 12)))
        assertEquals(Coding.Status.None, Coding.status(2, hours, at(16, 21))) // over for the day
        assertEquals(Coding.Status.None, Coding.status(-1, hours, at(16, 8))) // off
        assertEquals(Coding.Status.None, Coding.status(1, hours, at(22, 8))) // Sunday
    }

    @Test fun labels() {
        assertEquals("Coding until 10 AM", Coding.label(Coding.Status.Now(LocalTime.of(10, 0)), h24 = false))
        assertEquals("Coding today 7 AM–10 AM, 5 PM–8 PM", Coding.label(Coding.Status.Today(hours), h24 = false))
        assertEquals("Coding until 20:00", Coding.label(Coding.Status.Now(LocalTime.of(20, 0)), h24 = true))
        assertNull(Coding.label(Coding.Status.None, false))
    }
}

class TripLogTest {
    @Test fun steps() {
        val a = LatLon(14.0, 121.0); val b = LatLon(14.001, 121.0) // ~110 m
        assertEquals(0.11, TripMath.stepKm(a, b, 5f, 10_000), 0.005)
        assertEquals(0.0, TripMath.stepKm(a, b, 80f, 10_000), 0.0) // poor fix
        assertEquals(0.0, TripMath.stepKm(a, LatLon(14.00001, 121.0), 5f, 1_000), 0.0) // jitter
        assertEquals(0.0, TripMath.stepKm(a, b, 5f, 1_000), 0.0) // 110 m in a second: a jump
    }

    @Test fun totals() {
        val trips = listOf(
            Trip(1_000, 1_000 + 30 * 60_000, 20.0, 1.6),
            Trip(10_000_000, 10_000_000 + 15 * 60_000, 8.0, null),
        )
        val all = TripMath.totals(trips, 0)
        assertEquals(2, all.count); assertEquals(28.0, all.km, 1e-9); assertEquals(45, all.minutes)
        assertEquals(12.5, all.kmPerL!!, 1e-9) // only the trip with fuel data counts for km/L
        assertEquals(1, TripMath.totals(trips, 5_000_000).count)
        assertEquals(12.5, TripMath.fuelEconomy(trips)!!, 1e-9)
    }

    @Test fun periods() {
        val zone = ZoneId.of("Asia/Manila")
        val wed = LocalDateTime.of(2026, 11, 18, 15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0).atZone(zone).toInstant().toEpochMilli(), TripMath.weekStartMs(wed, zone))
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0).atZone(zone).toInstant().toEpochMilli(), TripMath.monthStartMs(wed, zone))
    }

    @Test fun roundTrip() {
        val trips = listOf(Trip(1, 2, 3.5, null), Trip(4, 5, 6.0, 0.5))
        assertEquals(trips, TripMath.decode(TripMath.encode(trips)))
        assertTrue(TripMath.decode("garbage").isEmpty())
    }
}

class MaintenanceTest {
    private val today = LocalDate.of(2026, 11, 20)

    @Test fun odometerCountsWithTheLog() {
        val m = Maintenance(odoKm = 50_000, odoAtLoggedKm = 100.0)
        assertEquals(50_250, MaintenanceMath.odometer(m, 350.0))
        assertNull(MaintenanceMath.odometer(Maintenance(), 350.0))
    }

    @Test fun due() {
        val rec = ServiceRecord(doneKm = 45_000, doneDay = today.minusMonths(5).toEpochDay(), intervalKm = 5_000)
        val d = MaintenanceMath.due(ServiceItem.OIL, rec, 49_700, today)
        assertEquals(300, d.kmLeft)
        assertTrue(d.soon); assertFalse(d.overdue)
        assertTrue(d.daysLeft!! in 28..31)
        val late = MaintenanceMath.due(ServiceItem.OIL, rec, 50_200, today)
        assertTrue(late.overdue)
        assertEquals("Overdue by 200 km", MaintenanceMath.text(late))
        // tyre rotation goes by km only
        assertNull(MaintenanceMath.due(ServiceItem.ROTATION, rec, 46_000, today).daysLeft)
    }

    @Test fun text() {
        val d = MaintenanceMath.due(ServiceItem.ROTATION, ServiceRecord(40_000, today.toEpochDay(), 10_000), 41_000, today)
        assertEquals("Due in 9,000 km", MaintenanceMath.text(d))
    }

    @Test fun roundTrip() {
        val m = Maintenance(61_234, 12.5, mapOf(ServiceItem.OIL to ServiceRecord(60_000, 20_000, 7_500)))
        assertEquals(m, MaintenanceMath.decode(MaintenanceMath.encode(m)))
        assertEquals(Maintenance(), MaintenanceMath.decode(null))
    }
}

class VoiceCommandTest {
    private fun p(s: String) = VoiceCommand.parse(s)

    @Test fun navigation() {
        assertEquals(VoiceCommand.Navigate("sm megamall"), p("Navigate to SM Megamall"))
        assertEquals(VoiceCommand.Navigate("bgc"), p("take me to BGC please"))
        assertEquals(VoiceCommand.Home, p("Take me home"))
        assertEquals(VoiceCommand.Home, p("navigate home"))
        assertEquals(VoiceCommand.Work, p("drive to work"))
        assertEquals(VoiceCommand.FindFuel, p("find the nearest gas station"))
        assertEquals(VoiceCommand.FindFuel, p("I'm low on fuel"))
        assertEquals(VoiceCommand.StopNavigation, p("stop navigation"))
    }

    @Test fun media() {
        assertEquals(VoiceCommand.Play, p("play music"))
        assertEquals(VoiceCommand.Pause, p("pause"))
        assertEquals(VoiceCommand.Pause, p("stop the music"))
        assertEquals(VoiceCommand.Next, p("next song"))
        assertEquals(VoiceCommand.Next, p("skip"))
        assertEquals(VoiceCommand.Previous, p("previous track"))
        assertEquals(VoiceCommand.VolumeUp, p("turn it up"))
        assertEquals(VoiceCommand.VolumeDown, p("volume down"))
        assertEquals(VoiceCommand.Mute, p("mute"))
    }

    @Test fun phoneAndApps() {
        assertEquals(VoiceCommand.Call("mom"), p("Call Mom"))
        assertEquals("09171234567", VoiceCommand.phoneNumber("0917 123 4567"))
        assertNull(VoiceCommand.phoneNumber("mom"))
        assertEquals(VoiceCommand.Open("spotify"), p("open Spotify"))
        assertEquals(VoiceCommand.Open("waze"), p("launch the Waze app"))
        assertTrue(p("what's the weather") is VoiceCommand.Unknown)
    }
}
