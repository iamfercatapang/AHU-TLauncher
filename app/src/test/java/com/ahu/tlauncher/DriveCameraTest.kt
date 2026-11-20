package com.ahu.tlauncher

import com.ahu.tlauncher.ui.home.DriveCamera
import com.ahu.tlauncher.ui.home.DriveCamera.Mode
import org.junit.Assert.assertEquals
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
}
