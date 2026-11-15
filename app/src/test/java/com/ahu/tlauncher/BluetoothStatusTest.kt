package com.ahu.tlauncher

import com.ahu.tlauncher.system.BtRule
import com.ahu.tlauncher.system.BtStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothStatusTest {
    @Test fun offWhenDisabled() = assertEquals(BtStatus.OFF, BtRule.status(enabled = false, canQuery = true, profileConnected = true))
    @Test fun connectedWhenAProfileIs() = assertEquals(BtStatus.CONNECTED, BtRule.status(enabled = true, canQuery = true, profileConnected = true))
    @Test fun onWithoutConnection() = assertEquals(BtStatus.ON, BtRule.status(enabled = true, canQuery = true, profileConnected = false))
    // Without "Nearby devices" Android won't say what's connected: show plain "on" rather than guess.
    @Test fun onWithoutPermission() = assertEquals(BtStatus.ON, BtRule.status(enabled = true, canQuery = false, profileConnected = true))
}
