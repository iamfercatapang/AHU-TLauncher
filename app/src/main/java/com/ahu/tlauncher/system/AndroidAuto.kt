package com.ahu.tlauncher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

sealed interface AaState {
    /** Detection off, or no receiver app installed. */
    data object Idle : AaState
    data object NoWifi : AaState
    data class Searching(val gateway: String) : AaState
    /** The phone's head unit server answered: Android Auto can start. */
    data class Ready(val gateway: String) : AaState
}

object AaLogic {
    /** Android Auto's "Start head unit server" listens on this port on the phone. */
    const val PORT = 5277

    /** Headunit Reloaded (full and trial). Any installed app whose name contains "Headunit" also counts. */
    val RECEIVERS = listOf("gb.xxy.hr", "gb.xxy.trial")

    /** DhcpInfo.gateway is a little-endian int. */
    fun ipFromInt(v: Int): String? =
        if (v == 0) null else "${v and 0xFF}.${(v shr 8) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 24) and 0xFF}"
}

/**
 * Notices when the phone (whose hotspot the head unit is on) runs Android Auto's head unit server,
 * so the dashboard can offer to open the receiver app. Probes only while the dashboard is on screen,
 * and stops probing once found, so the phone's server isn't poked repeatedly.
 */
class AndroidAutoDetector(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<AaState>(AaState.Idle)
    val state: StateFlow<AaState> = _state.asStateFlow()
    private var job: Job? = null
    var enabled = true
        set(v) { field = v; if (!v) { job?.cancel(); _state.value = AaState.Idle } }

    fun receiverPackage(): String? {
        val pm = context.packageManager
        AaLogic.RECEIVERS.firstOrNull { p -> runCatching { pm.getPackageInfo(p, 0) }.isSuccess }?.let { return it }
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        return launchable.firstOrNull { it.loadLabel(pm).toString().contains("headunit", ignoreCase = true) }?.activityInfo?.packageName
    }

    fun receiverLabel(pkg: String): String =
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    /** Call with true while the dashboard is visible. */
    fun setActive(active: Boolean) {
        job?.cancel()
        if (!active || !enabled) return
        job = scope.launch {
            var foundOn: String? = (_state.value as? AaState.Ready)?.gateway
            while (isActive) {
                val gw = gateway()
                when {
                    receiverPackage() == null -> _state.value = AaState.Idle
                    gw == null -> { foundOn = null; _state.value = AaState.NoWifi }
                    gw == foundOn -> _state.value = AaState.Ready(gw) // already found on this network
                    else -> {
                        if (_state.value !is AaState.Ready) _state.value = AaState.Searching(gw)
                        if (probe(gw)) { foundOn = gw; _state.value = AaState.Ready(gw) } else { foundOn = null; _state.value = AaState.Searching(gw) }
                    }
                }
                delay(5_000)
            }
        }
    }

    /** Forget a finding, e.g. after Android Auto was opened and closed, so the next visit checks again. */
    fun reset() { if (_state.value is AaState.Ready) _state.value = AaState.Idle; setActive(job?.isActive == true) }

    @Suppress("DEPRECATION")
    private fun gateway(): String? = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) return null
        AaLogic.ipFromInt(wm.dhcpInfo?.gateway ?: 0)
    }.getOrNull()

    private suspend fun probe(host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, AaLogic.PORT), 800); true } }.getOrDefault(false)
    }
}
