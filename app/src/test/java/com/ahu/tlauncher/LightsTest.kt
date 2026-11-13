package com.ahu.tlauncher

import com.ahu.tlauncher.system.AaLogic
import com.ahu.tlauncher.system.LightsLogic
import com.ahu.tlauncher.system.LightsSnapshot
import com.ahu.tlauncher.system.LightsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightsTest {
    private fun snap(night: Boolean = false, b: Int? = 200, vararg kv: Pair<String, String>) = LightsSnapshot(night, b, mapOf(*kv))

    @Test fun learnsVendorSetting() {
        val off = snap(false, 200, "system:ill_state" to "0", "system:screen_brightness" to "200", "global:wifi_on" to "1")
        val on = snap(false, 80, "system:ill_state" to "1", "system:screen_brightness" to "80", "global:wifi_on" to "0")
        val src = LightsLogic.learn(off, on)
        assertEquals(LightsSource.Setting("system:ill_state", "1"), src)
        assertTrue(LightsLogic.isOn(src!!, on))
        assertFalse(LightsLogic.isOn(src, off))
    }

    @Test fun prefersLikelyNames() {
        val off = snap(kv = arrayOf("system:foo" to "a", "system:car_lamp" to "0"))
        val on = snap(kv = arrayOf("system:foo" to "b", "system:car_lamp" to "1"))
        assertEquals(LightsSource.Setting("system:car_lamp", "1"), LightsLogic.learn(off, on))
    }

    @Test fun learnsNightMode() = assertEquals(LightsSource.NightMode, LightsLogic.learn(snap(false), snap(true)))

    @Test fun learnsBrightness() {
        val src = LightsLogic.learn(snap(b = 200), snap(b = 60)) as LightsSource.Brightness
        assertEquals(130, src.threshold)
        assertTrue(LightsLogic.isOn(src, snap(b = 60)))
        assertFalse(LightsLogic.isOn(src, snap(b = 200)))
    }

    @Test fun nothingChanged() = assertNull(LightsLogic.learn(snap(), snap()))

    @Test fun encodeRoundTrip() {
        listOf(LightsSource.Off, LightsSource.NightMode, LightsSource.Brightness(120), LightsSource.Setting("system:a:b", "on:1")).forEach {
            assertEquals(it, LightsSource.decode(it.encode()))
        }
    }

    @Test fun gatewayIp() {
        assertEquals("192.168.43.1", AaLogic.ipFromInt(0x012BA8C0))
        assertNull(AaLogic.ipFromInt(0))
    }
}
