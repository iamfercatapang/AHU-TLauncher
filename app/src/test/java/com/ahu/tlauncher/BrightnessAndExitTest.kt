package com.ahu.tlauncher

import com.ahu.tlauncher.notify.NavParser
import com.ahu.tlauncher.system.BrightnessMath
import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessAndExitTest {
    @Test fun brightnessScale() {
        assertEquals(255, BrightnessMath.toRaw(100))
        assertEquals(3, BrightnessMath.toRaw(1))
        assertEquals(128, BrightnessMath.toRaw(50))
        assertEquals(100, BrightnessMath.toPercent(255))
        assertEquals(1, BrightnessMath.toPercent(0))
        assertEquals(50, BrightnessMath.toPercent(BrightnessMath.toRaw(50)))
    }

    @Test fun findsExitNavigationButton() {
        assertEquals(1, NavParser.exitActionIndex(listOf("Mute", "Exit navigation")))
        assertEquals(0, NavParser.exitActionIndex(listOf("Stop", "Open")))
        assertEquals(0, NavParser.exitActionIndex(listOf("Salir de la navegación")))
        assertEquals(-1, NavParser.exitActionIndex(listOf("Mute", "Weekend route", null)))
    }
}
