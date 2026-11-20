package com.ahu.tlauncher

import com.ahu.tlauncher.ui.home.DriveCamera
import com.ahu.tlauncher.ui.home.DriveCamera.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCameraTest {
    @Test fun hysteresis() {
        assertEquals(Mode.PARK, DriveCamera.nextMode(Mode.PARK, 7))
        assertEquals(Mode.DRIVE, DriveCamera.nextMode(Mode.PARK, 8))
        assertEquals(Mode.DRIVE, DriveCamera.nextMode(Mode.DRIVE, 5))
        assertEquals(Mode.PARK, DriveCamera.nextMode(Mode.DRIVE, 3))
        assertEquals(Mode.DRIVE, DriveCamera.nextMode(Mode.DRIVE, null))
        assertEquals(Mode.PARK, DriveCamera.nextMode(Mode.PARK, null))
    }

    @Test fun zoomBySpeed() {
        assertEquals(18.25, DriveCamera.zoomFor(20), 0.001)
        assertEquals(18.25, DriveCamera.zoomFor(40), 0.001)
        assertEquals(17.75, DriveCamera.zoomFor(65), 0.001)
        assertEquals(17.25, DriveCamera.zoomFor(90), 0.001)
        assertEquals(17.25, DriveCamera.zoomFor(120), 0.001)
        assertEquals(17.0, DriveCamera.PARK_ZOOM, 0.001)
        assertEquals(0.0, DriveCamera.driveTilt(false), 0.001)
    }

    @org.junit.Test fun carSitsLowAndAwayFromCards() {
        // No route: low, centred left-right.
        assertEquals(listOf(0, 912, 0, 0), DriveCamera.padding(1000, 1200, DriveCamera.carAnchor(false, false)).toList())
        // Navigating, cards on the left: car to the right (72 % across), padding on the left.
        assertEquals(listOf(440, 912, 0, 0), DriveCamera.padding(1000, 1200, DriveCamera.carAnchor(true, false)).toList())
        // Cards on the right: mirrored.
        assertEquals(listOf(0, 912, 440, 0), DriveCamera.padding(1000, 1200, DriveCamera.carAnchor(true, true)).toList())
        // Navigating keeps the drive view while stopped.
        assertEquals(true, DriveCamera.driveView(DriveCamera.Mode.PARK, true))
        assertEquals(false, DriveCamera.driveView(DriveCamera.Mode.PARK, false))
    }

    @Test fun glidesBetweenFixes() {
        val g = com.ahu.tlauncher.ui.home.MapGlide()
        g.fix(14.0, 121.0, 0f, nowMs = 0)
        assertEquals(14.0, g.lat(0), 1e-9)
        g.fix(14.0001, 121.0, 10f, nowMs = 1_000) // ~11 m north, a second later
        assertEquals(14.0, g.lat(1_000), 1e-9) // starts where the car was shown
        assertEquals(14.00005, g.lat(1_500), 1e-7) // halfway after half the gap
        assertEquals(14.0001, g.lat(2_000), 1e-9)
        assertEquals(false, g.moving(2_000))
        // a new fix mid-glide carries on from where the car is shown, no jump back
        g.fix(14.0002, 121.0, 10f, nowMs = 2_500)
        assertEquals(14.0001, g.lat(2_500), 1e-9)
        // far away (after a tunnel): jump
        g.fix(14.1, 121.0, 10f, nowMs = 3_000)
        assertEquals(14.1, g.lat(3_000), 1e-9)
    }

    @Test fun angles() {
        assertEquals(0f, com.ahu.tlauncher.ui.home.lerpAngle(350f, 10f, 0.5f), 1e-3f)
        assertEquals(355f, com.ahu.tlauncher.ui.home.lerpAngle(350f, 10f, 0.25f), 1e-3f)
        assertTrue(com.ahu.tlauncher.ui.home.anglesClose(359.9f, 0.1f, 0.3f))
        val eased = com.ahu.tlauncher.ui.home.approach(16.0, 17.0, dtMs = 1_200, tauMs = 1_200)
        assertEquals(17.0 - Math.exp(-1.0), eased, 1e-9)
    }
}
