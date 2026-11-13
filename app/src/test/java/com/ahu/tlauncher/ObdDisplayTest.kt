package com.ahu.tlauncher

import com.ahu.tlauncher.obd.GpsSpeed
import com.ahu.tlauncher.obd.ObdDisplay
import com.ahu.tlauncher.obd.ObdStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdDisplayTest {
    @Test fun shownOnlyWithData() {
        assertTrue(ObdDisplay.show(ObdStatus.CONNECTED))
        assertTrue(ObdDisplay.show(ObdStatus.DEMO))
        assertFalse(ObdDisplay.show(ObdStatus.DISCONNECTED))
        assertFalse(ObdDisplay.show(ObdStatus.CONNECTING))
        assertFalse(ObdDisplay.show(ObdStatus.ERROR))
    }

    @Test fun gpsSpeedConversion() = assertEquals(58, GpsSpeed.kmh(true, 16.1f, 500))
    @Test fun gpsJitterIsZero() = assertEquals(0, GpsSpeed.kmh(true, 0.4f, 500))
    @Test fun gpsWithoutSpeed() = assertNull(GpsSpeed.kmh(false, 10f, 500))
    @Test fun gpsStaleFix() = assertNull(GpsSpeed.kmh(true, 10f, 30_000))
}
