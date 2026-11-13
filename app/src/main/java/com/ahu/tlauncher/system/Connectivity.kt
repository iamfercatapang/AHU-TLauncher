package com.ahu.tlauncher.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class BtStatus { OFF, ON, CONNECTED }

/** level: 0–4 signal bars when connected. */
data class WifiStatus(val enabled: Boolean, val connected: Boolean, val level: Int)

object Connectivity {
    @SuppressLint("MissingPermission")
    fun bluetooth(ctx: Context): BtStatus = runCatching {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return BtStatus.OFF
        if (!adapter.isEnabled) return BtStatus.OFF
        val canQuery = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!canQuery) return BtStatus.ON
        val connected = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
            .any { adapter.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED }
        if (connected) BtStatus.CONNECTED else BtStatus.ON
    }.getOrDefault(BtStatus.OFF)

    @Suppress("DEPRECATION")
    fun wifi(ctx: Context): WifiStatus = runCatching {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val level = if (!onWifi) 0 else WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 5).coerceIn(0, 4)
        WifiStatus(wm.isWifiEnabled, onWifi, level)
    }.getOrDefault(WifiStatus(false, false, 0))
}
