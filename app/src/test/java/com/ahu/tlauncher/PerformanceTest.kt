package com.ahu.tlauncher

import com.ahu.tlauncher.data.PerfMode
import com.ahu.tlauncher.obd.ObdData
import com.ahu.tlauncher.obd.ObdDisplay
import com.ahu.tlauncher.obd.ObdState
import com.ahu.tlauncher.obd.ObdStatus
import com.ahu.tlauncher.system.DeviceSpec
import com.ahu.tlauncher.system.PerfPolicy
import com.ahu.tlauncher.ui.home.DriveCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    private fun gib(g: Double) = (g * DeviceSpec.GIB).toLong()
    private val cheapUnit = DeviceSpec(gib(1.8), 4, false) // "2 GB" head unit
    private val goodUnit = DeviceSpec(gib(3.7), 8, false) // "4 GB", 8 cores

    @Test fun twoGbUnitIsLowEnd() = assertTrue(PerfPolicy.isLowEnd(cheapUnit))
    @Test fun fourGbEightCoreIsNot() = assertFalse(PerfPolicy.isLowEnd(goodUnit))
    @Test fun lowRamFlagWins() = assertTrue(PerfPolicy.isLowEnd(goodUnit.copy(lowRamFlag = true)))
    @Test fun fewCoresIsLowEnd() = assertTrue(PerfPolicy.isLowEnd(goodUnit.copy(cores = 2)))
    @Test fun unknownRamIsNotLowEnd() = assertFalse(PerfPolicy.isLowEnd(goodUnit.copy(ramBytes = 0)))

    @Test fun modeOverridesDetection() {
        assertTrue(PerfPolicy.active(PerfMode.AUTO, cheapUnit))
        assertFalse(PerfPolicy.active(PerfMode.AUTO, goodUnit))
        assertTrue(PerfPolicy.active(PerfMode.ON, goodUnit))
        assertFalse(PerfPolicy.active(PerfMode.OFF, cheapUnit))
    }

    @Test fun specLabel() = assertEquals("2 GB RAM · 4 cores", cheapUnit.label())

    @Test fun flatMapInLowPerf() {
        assertEquals(DriveCamera.PARK_TILT, DriveCamera.driveTilt(true), 0.0)
        assertEquals(DriveCamera.DRIVE_TILT, DriveCamera.driveTilt(false), 0.0)
    }

    private val base = ObdState(ObdStatus.CONNECTED, ObdData(speedKmh = 60, rpm = 2000, coolantC = 88, batteryV = 14.12, sampleAtMs = 1))
    private fun with(d: ObdData) = base.copy(data = d)

    @Test fun newSampleTimeAloneIsInvisible() = assertFalse(ObdDisplay.visiblyChanged(base, with(base.data.copy(sampleAtMs = 2))))
    @Test fun smallRpmChangeIsInvisible() = assertFalse(ObdDisplay.visiblyChanged(base, with(base.data.copy(rpm = 2010))))
    @Test fun rpmStepIsVisible() = assertTrue(ObdDisplay.visiblyChanged(base, with(base.data.copy(rpm = 2060))))
    @Test fun voltageWithinTenthIsInvisible() = assertFalse(ObdDisplay.visiblyChanged(base, with(base.data.copy(batteryV = 14.14))))
    @Test fun voltageStepIsVisible() = assertTrue(ObdDisplay.visiblyChanged(base, with(base.data.copy(batteryV = 14.3))))
    @Test fun speedChangeIsVisible() = assertTrue(ObdDisplay.visiblyChanged(base, with(base.data.copy(speedKmh = 61))))
    @Test fun statusChangeIsVisible() = assertTrue(ObdDisplay.visiblyChanged(base, base.copy(status = ObdStatus.ERROR)))
    @Test fun valueAppearingIsVisible() = assertTrue(ObdDisplay.visiblyChanged(base, with(base.data.copy(fuelPct = 50))))
}
