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

/** Mobile network (head units with a SIM): bars 0–4 and a short type label such as "4G". */
data class CellStatus(val level: Int, val type: String?)

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

    /** Null when there's no SIM / no mobile radio (most head units without a SIM). */
    @SuppressLint("MissingPermission")
    fun cell(ctx: Context): CellStatus? = runCatching {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return null
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        if (tm.simState != android.telephony.TelephonyManager.SIM_STATE_READY) return null
        val level = if (Build.VERSION.SDK_INT >= 28) tm.signalStrength?.level ?: 0 else 0
        val canRead = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val type = if (!canRead) null else CellType.label(tm.dataNetworkType)
        CellStatus(level.coerceIn(0, 4), type)
    }.getOrNull()

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

/** Network type → short label. Pure, unit tested. */
object CellType {
    fun label(t: Int): String? = when (t) {
        20 -> "5G"                       // NR
        13, 19 -> "4G"                   // LTE, LTE_CA
        3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
        1, 2, 4, 7, 11, 16 -> "2G"
        else -> null
    }
}
