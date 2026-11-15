package com.ahu.tlauncher

import com.ahu.tlauncher.system.WifiList
import com.ahu.tlauncher.system.WifiNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiListTest {
    @Test fun cleansSsid() {
        assertEquals("Home", WifiList.cleanSsid("\"Home\""))
        assertNull(WifiList.cleanSsid("<unknown ssid>"))
        assertNull(WifiList.cleanSsid("\"\""))
        assertNull(WifiList.cleanSsid(null))
    }

    @Test fun nearbyDedupesSortsAndSkipsCurrent() {
        val raw = listOf(
            WifiList.Raw("Cafe", 1, "[ESS]"),
            WifiList.Raw("Cafe", 4, "[ESS]"),
            WifiList.Raw("Home", 4, "[WPA2-PSK-CCMP][ESS]"),
            WifiList.Raw("", 4, "[WPA2]"),
            WifiList.Raw("Office", 2, "[WPA3-SAE][ESS]"),
        )
        assertEquals(
            listOf(WifiNetwork("Cafe", 3, false), WifiNetwork("Office", 2, true)),
            WifiList.nearby(raw, current = "Home"),
        )
    }

    @Test fun nearbyLimit() {
        val raw = (1..12).map { WifiList.Raw("Net$it", it % 5, "[WPA2]") }
        assertEquals(8, WifiList.nearby(raw, null).size)
    }

    @Test fun secured() {
        assertTrue(WifiList.secured("[WPA2-PSK-CCMP][ESS]"))
        assertTrue(WifiList.secured("[WEP][ESS]"))
        assertFalse(WifiList.secured("[ESS]"))
        assertFalse(WifiList.secured(null))
    }
}
