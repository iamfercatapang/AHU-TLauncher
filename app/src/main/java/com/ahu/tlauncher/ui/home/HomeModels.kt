package com.ahu.tlauncher.ui.home

import com.ahu.tlauncher.data.Settings
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.notify.NavCardState
import com.ahu.tlauncher.obd.ObdState
import com.ahu.tlauncher.system.VolumeState
import java.time.LocalDateTime

/** The call shown on the dashboard. */
data class CallUi(
    val ringing: Boolean,
    val caller: String,
    /** When the call was answered (epoch ms), for the timer. */
    val startedAtMs: Long?,
    val muted: Boolean = false,
    val canSwitchAudio: Boolean = false,
)

data class HomeUiState(
    val settings: Settings = Settings(),
    val obd: ObdState = ObdState(),
    val media: MediaInfo? = null,
    val nav: NavCardState? = null,
    val notificationAccess: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val volume: VolumeState = VolumeState(7, 15, false),
    val now: LocalDateTime = LocalDateTime.now(),
    val locationLat: Double? = null,
    val locationLon: Double? = null,
    val locationPermission: Boolean = false,
    /** Speed from GPS (km/h), null without a fresh fix. Used when no OBD adapter is connected. */
    val gpsSpeedKmh: Int? = null,
    val bluetoothOn: Boolean = false,
    val bluetooth: com.ahu.tlauncher.system.BtStatus = com.ahu.tlauncher.system.BtStatus.OFF,
    val wifi: com.ahu.tlauncher.system.WifiStatus = com.ahu.tlauncher.system.WifiStatus(false, false, 0),
    val isDefaultLauncher: Boolean = true,
    val update: com.ahu.tlauncher.update.UpdateState = com.ahu.tlauncher.update.UpdateState.Idle,
    /** Update toast hidden for this versionCode (user tapped Later). */
    val updateDismissedCode: Int = 0,
    val appVersion: String = "",
    val weather: com.ahu.tlauncher.system.Weather? = null,
    val diag: com.ahu.tlauncher.obd.DiagState = com.ahu.tlauncher.obd.DiagState(),
    val lights: com.ahu.tlauncher.system.LightsState = com.ahu.tlauncher.system.LightsState(),
    val call: CallUi? = null,
    val callPermission: Boolean = false,
    /** Package that last posted a call notification (Settings readout). */
    val callSource: String? = null,
    val aa: com.ahu.tlauncher.system.AaState = com.ahu.tlauncher.system.AaState.Idle,
    /** Name of the installed Android Auto receiver app, null if none. */
    val aaReceiver: String? = null,
    /** "Android Auto ready" prompt dismissed for this network. */
    val aaDismissed: Boolean = false,
)

/** Speed shown on screen and used by the map: OBD when it has live data, otherwise GPS. */
val HomeUiState.displaySpeedKmh: Int?
    get() = (if (com.ahu.tlauncher.obd.ObdDisplay.show(obd.status)) obd.data.speedKmh else null) ?: gpsSpeedKmh

/** Everything the UI can ask for. Default no-ops keep previews and screenshot tests simple. */
interface HomeActions {
    fun launch(app: AppEntry) {}
    fun togglePin(app: AppEntry) {}
    fun movePin(key: String, delta: Int) {}
    fun playPause() {}
    fun next() {}
    fun previous() {}
    fun seekTo(ms: Long) {}
    fun openMediaApp() {}
    fun openNavApp() {}
    fun navigateHome() {}
    fun navigateWork() {}
    fun openNavCard() {}
    fun volumeStep(up: Boolean) {}
    fun setVolume(level: Int) {}
    fun toggleMute() {}
    fun requestNotificationAccess() {}
    fun requestLocation() {}
    fun setDefaultLauncher() {}
    fun openAndroidSettings() {}
    fun startUpdate() {}
    fun dismissUpdate() {}
    fun checkForUpdates() {}
    // settings
    fun setTheme(mode: com.ahu.tlauncher.ui.theme.ThemeMode) {}
    fun setSpeedUnit(u: com.ahu.tlauncher.data.SpeedUnit) {}
    fun setTempUnit(u: com.ahu.tlauncher.data.TempUnit) {}
    fun setNavApp(a: com.ahu.tlauncher.data.NavApp) {}
    fun setCarSide(side: com.ahu.tlauncher.data.CarSide) {}
    fun setHome(addr: String) {}
    fun setWork(addr: String) {}
    fun setObdSource(s: com.ahu.tlauncher.data.ObdSourceType) {}
    fun pickObdDevice(addr: String, name: String) {}
    fun bondedDevices(): List<Pair<String, String>> = emptyList()
    fun pickCarImage(view: com.ahu.tlauncher.data.CarView) {}
    fun resetCarImage(view: com.ahu.tlauncher.data.CarView) {}
    fun setCarViewShown(view: com.ahu.tlauncher.data.CarView, shown: Boolean) {}
    // OBD diagnostics
    fun setObdExtended(on: Boolean) {}
    fun readObdCodes() {}
    fun clearObdCodes() {}
    // headlights (ILL)
    fun lightsLearnOff() {}
    /** Returns a message describing the result. */
    fun lightsLearnOn(): String = ""
    fun lightsDisable() {}
    // calls
    fun answerCall() {}
    fun endCall() {}
    fun toggleCallMute() {}
    fun switchCallAudio() {}
    fun requestCallPermission() {}
    // Android Auto
    fun openAndroidAuto() {}
    fun dismissAndroidAuto() {}
    fun setAaDetect(on: Boolean) {}
    fun getHeadunitReloaded() {}
}

object NoopActions : HomeActions
