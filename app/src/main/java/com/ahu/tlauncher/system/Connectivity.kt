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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class BtStatus { OFF, ON, CONNECTED }

/** Mobile network (head units with a SIM): bars 0–4 and a short type label such as "4G". */
data class CellStatus(val level: Int, val type: String?)

/** level: 0–4 signal bars when connected. */
data class WifiStatus(val enabled: Boolean, val connected: Boolean, val level: Int)

/** A paired Bluetooth device, for the status bar's Bluetooth popup. */
data class BtDevice(val name: String, val connected: Boolean)

/** [permitted] is false when Android 12+ hasn't granted "Nearby devices", so devices can't be listed. */
data class BtDetails(val status: BtStatus, val devices: List<BtDevice> = emptyList(), val permitted: Boolean = true)

/** A network from the last Wi-Fi scan. [level] is 0–3, as drawn by the status bar's Wi-Fi icon. */
data class WifiNetwork(val ssid: String, val level: Int, val secured: Boolean)

/** [canScan] is false without location permission: Android hides network names and scan results then. */
data class WifiDetails(val status: WifiStatus, val ssid: String? = null, val nearby: List<WifiNetwork> = emptyList(), val canScan: Boolean = true)

data class CellDetails(val status: CellStatus, val carrier: String? = null, val roaming: Boolean = false)

/** Pure rule for the Bluetooth status icon, unit tested. */
object BtRule {
    /**
     * [profileConnected]: any audio/call profile reports a connection. Both directions count: a head unit
     * is usually the A2DP sink / hands-free client of the phone, not the source.
     */
    fun status(enabled: Boolean, canQuery: Boolean, profileConnected: Boolean): BtStatus = when {
        !enabled -> BtStatus.OFF
        canQuery && profileConnected -> BtStatus.CONNECTED
        else -> BtStatus.ON
    }
}

object Connectivity {
    /**
     * Status for the status bar icon and the Bluetooth popup (the same answer for both). Asks the adapter's
     * cached state first, then the profiles themselves, which also covers the head-unit (sink) side.
     */
    @SuppressLint("MissingPermission")
    suspend fun bluetoothStatus(ctx: Context): BtStatus {
        val adapter = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull() ?: return BtStatus.OFF
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        val canQuery = canQueryBt(ctx)
        if (!enabled || !canQuery) return BtRule.status(enabled, canQuery, false)
        val cached = AUDIO_PROFILES.any { p -> runCatching { adapter.getProfileConnectionState(p) == BluetoothProfile.STATE_CONNECTED }.getOrDefault(false) }
        val connected = cached || connectedAddresses(ctx.applicationContext, adapter).isNotEmpty()
        return BtRule.status(enabled, canQuery, connected)
    }

    private fun canQueryBt(ctx: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Broadcasts that can change the Bluetooth status (adapter on/off, a device or audio/call profile connecting). */
    private val BT_ACTIONS = listOf(
        android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
        android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED,
        android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED,
        android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
        android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        // Head-unit side (hidden in the SDK): A2DP sink and hands-free client.
        "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED",
    )

    /** Emits once at the start and on every Bluetooth change, until collection stops. */
    fun bluetoothChanges(ctx: Context): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) { trySend(Unit) }
        }
        val filter = android.content.IntentFilter().apply { BT_ACTIONS.forEach(::addAction) }
        val app = ctx.applicationContext
        // System broadcasts: exported is right (and required on Android 14 for the hidden actions).
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        trySend(Unit)
        awaitClose { runCatching { app.unregisterReceiver(receiver) } }
    }

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

    /**
     * Status plus paired devices, connected ones first. "Connected" comes from the audio and call
     * profiles in both directions (a head unit is usually the A2DP sink / hands-free side).
     */
    @SuppressLint("MissingPermission")
    suspend fun bluetoothDetails(ctx: Context): BtDetails {
        val adapter = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        val enabled = adapter != null && runCatching { adapter.isEnabled }.getOrDefault(false)
        val permitted = canQueryBt(ctx)
        if (adapter == null || !enabled || !permitted) return BtDetails(BtRule.status(enabled, permitted, false), permitted = permitted)
        val bonded = runCatching { adapter.bondedDevices.orEmpty().toList() }.getOrDefault(emptyList())
        val connected = connectedAddresses(ctx.applicationContext, adapter)
        val devices = bonded.map { BtDevice(it.name ?: it.address, it.address in connected) }
            .sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
        // Same rule as the icon, from the same profiles, so the popup's heading matches its list.
        return BtDetails(BtRule.status(enabled, permitted, connected.isNotEmpty()), devices)
    }

    /** HEADSET, A2DP, A2DP_SINK and HEADSET_CLIENT (the last two are hidden constants; unsupported ones answer nothing). */
    private val AUDIO_PROFILES = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, 11, 16)

    @SuppressLint("MissingPermission")
    private suspend fun connectedAddresses(ctx: Context, adapter: android.bluetooth.BluetoothAdapter): Set<String> = coroutineScope {
        AUDIO_PROFILES.map { profile ->
            async {
                withTimeoutOrNull(1_500) {
                    suspendCancellableCoroutine { cont ->
                        val listener = object : BluetoothProfile.ServiceListener {
                            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                                val set = runCatching { proxy.connectedDevices.map { it.address }.toSet() }.getOrDefault(emptySet())
                                runCatching { adapter.closeProfileProxy(p, proxy) }
                                if (cont.isActive) cont.resume(set)
                            }
                            override fun onServiceDisconnected(p: Int) {}
                        }
                        val ok = runCatching { adapter.getProfileProxy(ctx, listener, profile) }.getOrDefault(false)
                        if (!ok && cont.isActive) cont.resume(emptySet())
                    }
                } ?: emptySet()
            }
        }.awaitAll().flatten().toSet()
    }

    /** Current network and nearby networks. [scan] asks for a fresh scan (Android throttles these). */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun wifiDetails(ctx: Context, scan: Boolean): WifiDetails {
        val status = wifi(ctx)
        val canScan = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!status.enabled) return WifiDetails(status, canScan = canScan)
        return runCatching {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = if (status.connected) WifiList.cleanSsid(wm.connectionInfo.ssid) else null
            if (!canScan) return WifiDetails(status, ssid, canScan = false)
            if (scan) runCatching { wm.startScan() }
            val raw = wm.scanResults.orEmpty().map { WifiList.Raw(it.SSID, WifiManager.calculateSignalLevel(it.level, 5), it.capabilities) }
            WifiDetails(status, ssid, WifiList.nearby(raw, ssid))
        }.getOrDefault(WifiDetails(status, canScan = canScan))
    }

    fun cellDetails(ctx: Context): CellDetails? {
        val status = cell(ctx) ?: return null
        return runCatching {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            CellDetails(status, tm.networkOperatorName?.trim()?.ifBlank { null }, tm.isNetworkRoaming)
        }.getOrDefault(CellDetails(status))
    }
}

/** Pure Wi-Fi list helpers, unit tested. */
object WifiList {
    /** One scan result: SSID as Android reports it, level 0–4, capability string such as "[WPA2-PSK-CCMP][ESS]". */
    data class Raw(val ssid: String?, val level: Int, val capabilities: String?)

    /** Strips Android's quotes; null for hidden or unknown networks. */
    fun cleanSsid(s: String?): String? = s?.removeSurrounding("\"")?.trim()?.takeUnless { it.isEmpty() || it == "<unknown ssid>" }

    /** Named networks except the current one, strongest first, one row per name (its best signal), at most [max]. */
    fun nearby(raw: List<Raw>, current: String?, max: Int = 8): List<WifiNetwork> =
        raw.mapNotNull { r -> cleanSsid(r.ssid)?.let { it to r } }
            .filter { (name, _) -> name != current }
            .groupBy({ it.first }, { it.second })
            .map { (name, rs) ->
                val best = rs.maxBy { it.level }
                WifiNetwork(name, best.level.coerceIn(0, 4).let { if (it >= 3) 3 else it }, secured(best.capabilities))
            }
            .sortedWith(compareByDescending<WifiNetwork> { it.level }.thenBy { it.ssid.lowercase() })
            .take(max)

    fun secured(caps: String?): Boolean = caps != null && listOf("WEP", "WPA", "SAE", "PSK", "EAP").any { caps.contains(it) }
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
