package com.ahu.tlauncher.ui.home

import com.ahu.tlauncher.data.Settings
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.notify.NavCardState
import com.ahu.tlauncher.obd.ObdState
import com.ahu.tlauncher.system.VolumeState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * Values that change several times a second: engine data, GPS and trip fuel. They live in snapshot
 * state instead of [HomeUiState], so a new reading only redraws the widgets that show it (speed,
 * callouts, map camera), not the whole home screen. The same instance is kept for the app's life.
 */
@Stable
class LiveState(
    obd: ObdState = ObdState(),
    fuel: com.ahu.tlauncher.obd.FuelStats = com.ahu.tlauncher.obd.FuelStats(),
    gpsSpeedKmh: Int? = null,
    locationLat: Double? = null,
    locationLon: Double? = null,
    locationBearing: Float? = null,
) {
    var obd by mutableStateOf(obd)
    var fuel by mutableStateOf(fuel)
    var gpsSpeedKmh by mutableStateOf(gpsSpeedKmh)
    var locationLat by mutableStateOf(locationLat)
    var locationLon by mutableStateOf(locationLon)
    var locationBearing by mutableStateOf(locationBearing)
}

data class HomeUiState(
    val settings: Settings = Settings(),
    /** Fast-changing values; read them through [obd], [fuel], [gpsSpeedKmh] and the location fields. */
    val live: LiveState = LiveState(),
    val media: MediaInfo? = null,
    /** Last YouTube Music / Spotify song, shown when nothing is playing (e.g. after the head unit restarts). */
    val lastMedia: MediaInfo? = null,
    /** Play was pressed on the last song and we're waiting for the app to start. */
    val resumingMedia: Boolean = false,
    val nav: NavCardState? = null,
    val notificationAccess: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val volume: VolumeState = VolumeState(7, 15, false),
    /** Updated once a minute (the clock shows hours and minutes). */
    val now: LocalDateTime = LocalDateTime.now(),
    /** Google map can run: API key built in and Google Play services present. */
    val googleMapsAvailable: Boolean = false,
    val locationPermission: Boolean = false,
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
    /** "Display over other apps": lets the dashboard come back after Start opens Google Maps. */
    val returnPermission: Boolean = false,
    /** Current screen brightness %, whether it's the night level, and whether TLauncher may change it. */
    val brightness: Int = 50,
    val brightnessNight: Boolean = false,
    val brightnessAllowed: Boolean = true,
    /** Package that last posted a call notification (Settings readout). */
    val callSource: String? = null,
    // dashboard route (Google search + Routes)
    val route: com.ahu.tlauncher.route.RouteState? = null,
    val search: com.ahu.tlauncher.route.SearchState = com.ahu.tlauncher.route.SearchState(),
    val recents: List<com.ahu.tlauncher.route.Place> = emptyList(),
    val favourites: List<com.ahu.tlauncher.route.Place> = emptyList(),
    /** Spot long-pressed on the map (pin card shown), null when none. */
    val pin: com.ahu.tlauncher.route.Place? = null,
    /** Home / Work being pinned on the map (from Settings), null otherwise. */
    val picking: com.ahu.tlauncher.data.SavedSpot? = null,
    /** Drive minutes to Home / Work, keyed by address. */
    val etaMin: Map<String, Int> = emptyMap(),
    /** Free map: OpenStreetMap search/routing and TLauncher's own spoken guidance. */
    val freeNav: Boolean = false,
    /** Mobile signal (SIM head units only). */
    val cell: com.ahu.tlauncher.system.CellStatus? = null,
    /** Notifications for the status-bar bell. */
    val inbox: List<com.ahu.tlauncher.notify.InboxItem> = emptyList(),
    /** Low-performance mode is on (set, or detected for this head unit). */
    val lowPerf: Boolean = false,
    /** RAM and cores of this head unit, for Settings. */
    val deviceSpec: com.ahu.tlauncher.system.DeviceSpec? = null,
    /** Unfinished trip offered after the car starts ("Continue to X?"), null when none. */
    val tripOffer: com.ahu.tlauncher.system.SavedTrip? = null,
    /** Saved offline map areas (free map). */
    val offlineAreas: List<com.ahu.tlauncher.map.OfflineArea> = emptyList(),
    /** Trip log (oldest first), all distance ever logged (km), and the drive under way (km so far). */
    val trips: List<com.ahu.tlauncher.system.Trip> = emptyList(),
    val loggedKm: Double = 0.0,
    val currentTripKm: Double? = null,
    /** Maintenance: odometer (null until read once), tracked items soonest first, and the reminder for this start. */
    val odometerKm: Int? = null,
    val maintenance: com.ahu.tlauncher.system.Maintenance = com.ahu.tlauncher.system.Maintenance(),
    val serviceDue: List<com.ahu.tlauncher.system.ServiceDue> = emptyList(),
    val serviceReminder: com.ahu.tlauncher.system.ServiceDue? = null,
    /** Bumped to open the destination search (e.g. "find a gas station" said by voice). */
    val searchRequest: Int = 0,
) {
    // Reading these in a composable redraws only that composable when they change.
    val obd: ObdState get() = live.obd
    val fuel: com.ahu.tlauncher.obd.FuelStats get() = live.fuel
    /** Speed from GPS (km/h), null without a fresh fix. Used when no OBD adapter is connected. */
    val gpsSpeedKmh: Int? get() = live.gpsSpeedKmh
    val locationLat: Double? get() = live.locationLat
    val locationLon: Double? get() = live.locationLon
    /** GPS course over ground (degrees), for the heading-up Google map. */
    val locationBearing: Float? get() = live.locationBearing

    /** A copy with its own live values (previews and tests). */
    fun withLive(
        obd: ObdState = this.obd,
        fuel: com.ahu.tlauncher.obd.FuelStats = this.fuel,
        gpsSpeedKmh: Int? = this.gpsSpeedKmh,
        locationLat: Double? = this.locationLat,
        locationLon: Double? = this.locationLon,
        locationBearing: Float? = this.locationBearing,
    ) = copy(live = LiveState(obd, fuel, gpsSpeedKmh, locationLat, locationLon, locationBearing))
}

/** Route search and drawing need the Google key, Play services and the Google map selected. */
val HomeUiState.routesEnabled: Boolean
    get() = freeNav || (googleMapsAvailable && settings.mapEngine == com.ahu.tlauncher.data.MapEngine.GOOGLE)

/** Second line under the song title: artist, or "Last played" / "Starting …" for a remembered song. */
fun HomeUiState.musicSubtitle(appLabel: String?): String? {
    val live = media
    val last = lastMedia
    return when {
        live != null -> live.artist.ifBlank { null }
        last == null -> null
        resumingMedia -> "Starting ${appLabel ?: "music"}…"
        else -> "Last played" + (last.artist.ifBlank { null }?.let { " · $it" } ?: "")
    }
}

/** A route is being followed: TLauncher's own guidance, or Google Maps / Waze navigating (their turn card shows). */
val HomeUiState.isNavigating: Boolean
    get() = route?.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE || (nav != null && route?.phase != com.ahu.tlauncher.route.RoutePhase.PREVIEW)

/** Speed shown on screen and used by the map: OBD when it has live data, otherwise GPS. */
val HomeUiState.displaySpeedKmh: Int?
    get() = (if (com.ahu.tlauncher.obd.ObdDisplay.show(obd.status)) obd.data.speedKmh else null) ?: gpsSpeedKmh

/** Everything the UI can ask for. Default no-ops keep previews and screenshot tests simple. */
interface HomeActions {
    fun launch(app: AppEntry) {}
    fun togglePin(app: AppEntry) {}
    fun appInfo(app: AppEntry) {}
    fun setBrightness(percent: Int) {}
    fun requestBrightnessPermission() {}
    fun uninstallApp(app: AppEntry) {}
    fun movePin(key: String, delta: Int) {}
    fun playPause() {}
    fun next() {}
    fun previous() {}
    fun seekTo(ms: Long) {}
    fun toggleShuffle() {}
    fun cycleRepeat() {}
    fun playQueueItem(id: Long) {}
    fun openMediaApp() {}
    fun openNavApp() {}
    fun navigateHome() {}
    fun navigateWork() {}
    fun openNavCard() {}
    /** Gear on the map: Settings → Navigation (handled by the home screen). */
    fun openMapSettings() {}
    /** Tap on the music card: player over the car panel (handled by the home screen). */
    fun openCardPlayer() {}
    fun openInbox(item: com.ahu.tlauncher.notify.InboxItem) {}
    fun dismissInbox(item: com.ahu.tlauncher.notify.InboxItem) {}
    fun dismissAllInbox() {}
    fun volumeStep(up: Boolean) {}
    fun setVolume(level: Int) {}
    fun toggleMute() {}
    fun requestNotificationAccess() {}
    fun requestLocation() {}
    fun setDefaultLauncher() {}
    fun openAndroidSettings() {}
    // Status bar popups: details are read when a popup is open; the settings pages are Android's own.
    suspend fun bluetoothDetails(): com.ahu.tlauncher.system.BtDetails? = null
    fun wifiDetails(scan: Boolean): com.ahu.tlauncher.system.WifiDetails? = null
    fun cellDetails(): com.ahu.tlauncher.system.CellDetails? = null
    fun openBluetoothSettings() {}
    fun openWifiSettings() {}
    fun openMobileSettings() {}
    fun startUpdate() {}
    fun dismissUpdate() {}
    fun checkForUpdates() {}
    // settings
    fun setTheme(mode: com.ahu.tlauncher.ui.theme.ThemeMode) {}
    fun setSpeedUnit(u: com.ahu.tlauncher.data.SpeedUnit) {}
    fun setTempUnit(u: com.ahu.tlauncher.data.TempUnit) {}
    fun setNavApp(a: com.ahu.tlauncher.data.NavApp) {}
    fun setCarSide(side: com.ahu.tlauncher.data.CarSide) {}
    fun setPerfMode(m: com.ahu.tlauncher.data.PerfMode) {}
    fun setHome(addr: String) {}
    fun setWork(addr: String) {}
    fun setObdSource(s: com.ahu.tlauncher.data.ObdSourceType) {}
    fun pickObdDevice(addr: String, name: String) {}
    fun bondedDevices(): List<Pair<String, String>> = emptyList()
    fun pickCarImage(view: com.ahu.tlauncher.data.CarView) {}
    fun resetCarImage(view: com.ahu.tlauncher.data.CarView) {}
    fun setCar3d(on: Boolean) {}
    fun setCarPaint(p: com.ahu.tlauncher.data.CarPaint) {}
    fun setCarViewShown(view: com.ahu.tlauncher.data.CarView, shown: Boolean) {}
    // OBD diagnostics
    fun setObdExtended(on: Boolean) {}
    fun readObdCodes() {}
    fun clearObdCodes() {}
    fun resetTrip() {}
    fun setTankL(l: Int) {}
    fun setMapEngine(e: com.ahu.tlauncher.data.MapEngine) {}
    fun setMapTraffic(on: Boolean) {}
    fun setResumeMusic(on: Boolean) {}
    fun setOfferTrip(on: Boolean) {}
    fun setOpenOnStart(on: Boolean) {}
    // offline maps (free map)
    fun loadOfflineAreas() {}
    fun saveOfflineArea(name: String, lat: Double, lon: Double, radiusKm: Int) {}
    fun updateOfflineArea(id: Long) {}
    fun deleteOfflineArea(id: Long) {}
    /** "Continue to X?" after the car starts: route there and start. */
    fun continueTrip() {}
    fun dismissTrip() {}
    // route
    fun searchPlaces(query: String) {}
    fun clearSearch() {}
    fun pickSuggestion(s: com.ahu.tlauncher.route.Suggestion) {}
    fun previewPlace(p: com.ahu.tlauncher.route.Place) {}
    /** Route tile tapped in the preview (0 = recommended). */
    fun selectRoute(index: Int) {}
    fun startRoute() {}
    fun requestReturnPermission() {}
    fun endRoute() {}
    fun toggleFavourite(p: com.ahu.tlauncher.route.Place) {}
    /** Long-press on the map: drop a pin there. */
    fun dropPin(lat: Double, lon: Double) {}
    fun clearPin() {}
    /** Pin card's Directions: preview the route to the pin. */
    fun pinDirections() {}
    fun refreshEtas() {}
    /** "Pin on map" next to Home / Work in Settings: the next long-press sets it. False when a trip is running. */
    fun pickOnMap(spot: com.ahu.tlauncher.data.SavedSpot): Boolean = false
    fun cancelPicking() {}
    /** Pin card while picking: save the pin as Home / Work. */
    fun savePinAs(spot: com.ahu.tlauncher.data.SavedSpot) {}
    fun setEngineCc(cc: Int) {}
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
    // number coding
    fun setCoding(digit: Int, hours: String) {}
    // trip log and maintenance
    fun clearTrips() {}
    fun setOdometer(km: Int) {}
    fun markServiceDone(item: com.ahu.tlauncher.system.ServiceItem) {}
    fun setServiceInterval(item: com.ahu.tlauncher.system.ServiceItem, km: Int) {}
    fun stopService(item: com.ahu.tlauncher.system.ServiceItem) {}
    /** "Later" on the service reminder. */
    fun snoozeService() {}
    /** Fuel stations near the car (ahead on the route while navigating), shown in the search panel. */
    fun findFuel() {}
    /** Mic in the dock: listen for a spoken command. */
    fun voiceCommand() {}
}

object NoopActions : HomeActions
