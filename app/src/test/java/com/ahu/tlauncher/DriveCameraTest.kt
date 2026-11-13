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
        assertEquals(17.0, DriveCamera.zoomFor(20), 0.001)
        assertEquals(16.5, DriveCamera.zoomFor(45), 0.001)
        assertEquals(16.0, DriveCamera.zoomFor(60), 0.001)
        assertEquals(15.0, DriveCamera.zoomFor(120), 0.001)
    }
}
