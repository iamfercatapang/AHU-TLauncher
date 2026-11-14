package com.ahu.tlauncher

import com.ahu.tlauncher.system.EqMath
import com.ahu.tlauncher.system.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class EqTest {
    private val five = listOf(60, 230, 910, 3600, 14000)

    @Test fun flatIsZero() = assertEquals(List(5) { 0f }, EqMath.curve(EqMath.PRESETS.getValue("Flat"), five))

    @Test fun bassBoostFallsWithFrequency() {
        val g = EqMath.curve(EqMath.PRESETS.getValue("Bass boost"), five)
        assertEquals(6f, g[0], 0f)
        assertEquals(0f, g[4], 0f)
        assert(g[0] > g[1] && g[1] > g[2]) { g.toString() }
    }

    @Test fun interpolatesOnLogScale() {
        // halfway between 1 kHz (0 dB) and 4 kHz (4 dB) on a log scale is 2 kHz → 2 dB
        assertEquals(listOf(2f), EqMath.curve(listOf(1000 to 0f, 4000 to 4f), listOf(2000)))
    }

    @Test fun tenBandsWork() = assertEquals(10, EqMath.curve(EqMath.PRESETS.getValue("Rock"), listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)).size)

    @Test fun roundTrip() {
        val s = EqSettings(true, "Mine", listOf(1.5f, -2f, 0f), 40, 20, mapOf("Mine" to listOf(1.5f, -2f, 0f)))
        assertEquals(s, EqMath.decode(EqMath.encode(s)))
        assertEquals(EqSettings(), EqMath.decode(null))
    }
}

class EqLabelTest {
    @Test fun freqLabels() {
        org.junit.Assert.assertEquals(listOf("31", "176", "1K", "2.4K", "14K"), listOf(31, 176, 1000, 2400, 14000).map { EqMath.freqLabel(it) })
    }
}
