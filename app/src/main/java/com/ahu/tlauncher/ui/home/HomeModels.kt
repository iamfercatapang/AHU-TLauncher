package com.ahu.tlauncher.ui.home

import com.ahu.tlauncher.data.Settings
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.notify.NavCardState
import com.ahu.tlauncher.obd.ObdState
import com.ahu.tlauncher.system.VolumeState
import java.time.LocalDateTime

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
    val bluetoothOn: Boolean = false,
    val isDefaultLauncher: Boolean = true,
)

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
    // settings
    fun setTheme(mode: com.ahu.tlauncher.ui.theme.ThemeMode) {}
    fun setSpeedUnit(u: com.ahu.tlauncher.data.SpeedUnit) {}
    fun setTempUnit(u: com.ahu.tlauncher.data.TempUnit) {}
    fun setNavApp(a: com.ahu.tlauncher.data.NavApp) {}
    fun setHome(addr: String) {}
    fun setWork(addr: String) {}
    fun setObdSource(s: com.ahu.tlauncher.data.ObdSourceType) {}
    fun pickObdDevice(addr: String, name: String) {}
    fun bondedDevices(): List<Pair<String, String>> = emptyList()
    fun pickCarImage(view: com.ahu.tlauncher.data.CarView) {}
    fun resetCarImage(view: com.ahu.tlauncher.data.CarView) {}
}

object NoopActions : HomeActions
