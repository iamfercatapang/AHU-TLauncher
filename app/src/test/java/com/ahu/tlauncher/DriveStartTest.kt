package com.ahu.tlauncher

import com.ahu.tlauncher.route.Place
import com.ahu.tlauncher.system.DriveStartRules
import com.ahu.tlauncher.system.SavedTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStartTest {
    private val min = 60_000L
    private val trip = SavedTrip(Place("SM Megamall", "Ortigas", 14.585, 121.056), startedAtMs = 1_000_000)

    @Test fun screenOffBriefly() = assertFalse(DriveStartRules.afterScreenOn(offAtElapsed = 0, nowElapsed = 30_000))
    @Test fun screenOffLong() = assertTrue(DriveStartRules.afterScreenOn(offAtElapsed = 0, nowElapsed = 3 * min))
    @Test fun screenOnWithoutOff() = assertFalse(DriveStartRules.afterScreenOn(offAtElapsed = null, nowElapsed = 10 * min))

    @Test fun awake() = assertFalse(DriveStartRules.slept(elapsedDelta = 30_000, uptimeDelta = 30_000))
    @Test fun sleptAnHour() = assertTrue(DriveStartRules.slept(elapsedDelta = 60 * min + 10_000, uptimeDelta = 10_000))
    @Test fun dozedBriefly() = assertFalse(DriveStartRules.slept(elapsedDelta = 90_000, uptimeDelta = 30_000))

    @Test fun resumesMusic() = assertTrue(DriveStartRules.shouldResumeMusic(true, wasPlaying = true, accessGranted = true, somethingPlaying = false, inCall = false))
    @Test fun notIfPaused() = assertFalse(DriveStartRules.shouldResumeMusic(true, wasPlaying = false, accessGranted = true, somethingPlaying = false, inCall = false))
    @Test fun notIfRadioPlaying() = assertFalse(DriveStartRules.shouldResumeMusic(true, wasPlaying = true, accessGranted = true, somethingPlaying = true, inCall = false))
    @Test fun notInCall() = assertFalse(DriveStartRules.shouldResumeMusic(true, wasPlaying = true, accessGranted = true, somethingPlaying = false, inCall = true))
    // Without notification access we can't see what's playing, so don't risk starting music over something else.
    @Test fun notWithoutAccess() = assertFalse(DriveStartRules.shouldResumeMusic(true, wasPlaying = true, accessGranted = false, somethingPlaying = false, inCall = false))
    @Test fun notWhenOff() = assertFalse(DriveStartRules.shouldResumeMusic(false, wasPlaying = true, accessGranted = true, somethingPlaying = false, inCall = false))

    @Test fun offersRecentTrip() = assertEquals(trip, DriveStartRules.tripToOffer(true, trip, trip.startedAtMs + 30 * min, routeShowing = false))
    @Test fun notOldTrip() = assertNull(DriveStartRules.tripToOffer(true, trip, trip.startedAtMs + 13 * 60 * min, routeShowing = false))
    @Test fun notWhileRouting() = assertNull(DriveStartRules.tripToOffer(true, trip, trip.startedAtMs + min, routeShowing = true))
    @Test fun notWhenOfferOff() = assertNull(DriveStartRules.tripToOffer(false, trip, trip.startedAtMs + min, routeShowing = false))
    @Test fun noTrip() = assertNull(DriveStartRules.tripToOffer(true, null, 0, routeShowing = false))
    @Test fun clockWentBack() = assertNull(DriveStartRules.tripToOffer(true, trip, trip.startedAtMs - min, routeShowing = false))

    @Test fun userEndForgets() = assertTrue(DriveStartRules.forgetOnEnd(byUser = true, metresToDestination = 5_000.0))
    @Test fun navAppGoneKeeps() = assertFalse(DriveStartRules.forgetOnEnd(byUser = false, metresToDestination = 5_000.0))
    @Test fun navAppGoneAtDestinationForgets() = assertTrue(DriveStartRules.forgetOnEnd(byUser = false, metresToDestination = 120.0))
    @Test fun navAppGoneNoPositionKeeps() = assertFalse(DriveStartRules.forgetOnEnd(byUser = false, metresToDestination = null))
}
