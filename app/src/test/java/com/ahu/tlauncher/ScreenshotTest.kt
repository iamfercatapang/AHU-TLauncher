package com.ahu.tlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.ahu.tlauncher.data.Settings
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.notify.NavCardState
import com.ahu.tlauncher.notify.NavInfo
import com.ahu.tlauncher.obd.ObdData
import com.ahu.tlauncher.obd.ObdState
import com.ahu.tlauncher.obd.ObdStatus
import com.ahu.tlauncher.ui.home.HomeScreen
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.home.NoopActions
import com.ahu.tlauncher.ui.home.Overlay
import com.ahu.tlauncher.ui.theme.TLauncherTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Renders the dashboard on head-unit sized screens. Run: ./gradlew recordPaparazziDebug */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1024, screenHeight = 600, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        showSystemUi = false,
        maxPercentDifference = 0.5,
    )


    private fun fakeApp(label: String, color: Long): AppEntry {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }
        c.drawRoundRect(0f, 0f, 96f, 96f, 22f, 22f, p)
        p.color = 0xFFFFFFFF.toInt(); p.textSize = 44f; p.textAlign = Paint.Align.CENTER
        c.drawText(label.take(1), 48f, 63f, p)
        return AppEntry("pkg.$label/.Main", label, "pkg.$label", android.content.ComponentName("pkg.$label", ".Main"), b.asImageBitmap())
    }

    private val apps by lazy {
        listOf(
            fakeApp("Maps", 0xFF34A853), fakeApp("Waze", 0xFF33CCFF), fakeApp("Spotify", 0xFF1DB954),
            fakeApp("YouTube Music", 0xFFFF0000), fakeApp("Phone", 0xFF1A73E8), fakeApp("Settings", 0xFF5F6368),
            fakeApp("Camera", 0xFF444444), fakeApp("Chrome", 0xFFFBBC04), fakeApp("Files", 0xFF4285F4),
            fakeApp("Radio", 0xFFE91E63), fakeApp("Gallery", 0xFF9C27B0), fakeApp("Calendar", 0xFF0B8043),
        )
    }

    private fun art(): Bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint()
        for (y in 0 until 120) { p.color = android.graphics.Color.rgb(200 - y, 80 + y / 2, 60 + y); c.drawLine(0f, y.toFloat(), 120f, y.toFloat(), p) }
    }

    private fun state(nav: Boolean = false, access: Boolean = true) = HomeUiState(
        settings = Settings(pinnedApps = apps.take(5).map { it.key }),
        obd = ObdState(ObdStatus.DEMO, ObdData(speedKmh = 62, rpm = 2340, coolantC = 86, batteryV = 14.1, fuelPct = 62)),
        fuel = com.ahu.tlauncher.obd.FuelStats(tripKm = 23.4, tripLitres = 1.9, avgKmPerL = 12.6, rangeKm = 335),
        media = MediaInfo("com.spotify.music", "Midnight Drive", "The Night Lines", art(), true, 95_000, 214_000, 0, 1f),
        nav = if (nav) NavCardState(NavInfo("com.google.android.apps.maps", "Google Maps", "300 m", "Turn right onto Ayala Ave", null, "12 min · 4.3 km · 10:42 ETA"), null, null) else null,
        notificationAccess = access,
        apps = apps,
        now = LocalDateTime.of(2026, 11, 13, 21, 41),
        locationPermission = true,
        bluetoothOn = true,
        bluetooth = com.ahu.tlauncher.system.BtStatus.CONNECTED,
        wifi = com.ahu.tlauncher.system.WifiStatus(true, true, 3),
    )

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                TLauncherTheme(dark = dark) { ScaledCanvas { content() } }
            }
        }
    }

    @Test fun carDark() = shot("car_full_dark") { HomeScreen(state(), NoopActions, initialMapVisible = false) }
    @Test fun carLight() = shot("car_full_light", dark = false) { HomeScreen(state(), NoopActions, initialMapVisible = false) }
    @Test fun noObdGps() = shot("no_obd_gps_speed") { HomeScreen(state().copy(obd = ObdState(ObdStatus.DISCONNECTED), gpsSpeedKmh = 47), NoopActions) }
    @Test fun noObdNoFix() = shot("no_obd_no_fix", dark = false) { HomeScreen(state().copy(obd = ObdState(ObdStatus.DISCONNECTED), gpsSpeedKmh = null), NoopActions, initialMapVisible = false) }
    @Test fun updateToast() = shot("update_available") {
        HomeScreen(state().copy(update = com.ahu.tlauncher.update.UpdateState.Available(com.ahu.tlauncher.update.RemoteVersion(12, "1.0.12"))), NoopActions)
    }
    @Test fun carOnRight() = shot("car_panel_right") { HomeScreen(state().copy(settings = state().settings.copy(carSide = com.ahu.tlauncher.data.CarSide.RIGHT)), NoopActions) }
    @Test fun homeDark() = shot("home_dark") { HomeScreen(state(), NoopActions) }
    @Test fun homeLight() = shot("home_light", dark = false) { HomeScreen(state(), NoopActions) }
    @Test fun homeNavigatingDark() = shot("home_navigating_dark") { HomeScreen(state(nav = true), NoopActions) }
    @Test fun homeNoAccess() = shot("home_first_run") { HomeScreen(state(access = false).copy(media = null), NoopActions) }
    @Test fun appDrawer() = shot("app_drawer") { HomeScreen(state(), NoopActions, initialOverlay = Overlay.APPS) }
    @Test fun musicPanel() = shot("music_panel") { HomeScreen(state(), NoopActions, initialOverlay = Overlay.MUSIC) }
    @Test fun settings() = shot("settings_display") { HomeScreen(state(), NoopActions, initialOverlay = Overlay.SETTINGS) }
    @Test fun weatherAndViews() = shot("weather_single_view", dark = false) {
        val s = state()
        HomeScreen(
            s.copy(
                settings = s.settings.copy(carViews = setOf(com.ahu.tlauncher.data.CarView.FRONT)),
                weather = com.ahu.tlauncher.system.Weather(31.4, com.ahu.tlauncher.system.WeatherKind.PARTLY_CLOUDY, true, 0),
            ),
            NoopActions, initialMapVisible = false,
        )
    }
    @Test fun diagnostics() = shot("settings_diagnostics") {
        val s = state()
        HomeScreen(
            s.copy(
                obd = s.obd.copy(data = s.obd.data.copy(loadPct = 34, throttlePct = 18, intakeC = 34, mafGs = 6.2, timingDeg = 21.5, stftPct = -1.6, unsupported = setOf("07"))),
                diag = com.ahu.tlauncher.obd.DiagState(milOn = true, stored = listOf("P0171", "P1349"), pending = emptyList()),
            ),
            NoopActions, initialOverlay = Overlay.SETTINGS, initialSettings = com.ahu.tlauncher.ui.settings.Section.DIAGNOSTICS,
        )
    }
    @Test fun incomingCall() = shot("call_incoming") {
        HomeScreen(state().copy(call = com.ahu.tlauncher.ui.home.CallUi(ringing = true, caller = "Maria Santos", startedAtMs = null)), NoopActions)
    }
    @Test fun activeCall() = shot("call_active", dark = false) {
        HomeScreen(state().copy(call = com.ahu.tlauncher.ui.home.CallUi(ringing = false, caller = "+63 917 123 4567", startedAtMs = null, muted = true, canSwitchAudio = true)), NoopActions, initialMapVisible = false)
    }
    @Test fun headlightsAndAa() = shot("headlights_android_auto") {
        HomeScreen(
            state().copy(
                obd = ObdState(ObdStatus.DISCONNECTED), gpsSpeedKmh = 38,
                lights = com.ahu.tlauncher.system.LightsState(on = true),
                aa = com.ahu.tlauncher.system.AaState.Ready("192.168.43.1"), aaReceiver = "Headunit Reloaded",
            ),
            NoopActions,
        )
    }
    @Test fun settingsAndroidAuto() = shot("settings_android_auto") {
        HomeScreen(state().copy(aa = com.ahu.tlauncher.system.AaState.Searching("192.168.43.1")), NoopActions, initialOverlay = Overlay.SETTINGS, initialSettings = com.ahu.tlauncher.ui.settings.Section.ANDROID_AUTO)
    }
    @Test fun lowFuel() = shot("fuel_low", dark = false) {
        val st = state()
        HomeScreen(st.copy(obd = st.obd.copy(data = st.obd.data.copy(fuelPct = 9)), fuel = st.fuel.copy(rangeKm = 48)), NoopActions)
    }
    @Test fun musicQueue() = shot("music_queue") {
        val st = state()
        val titles = listOf("Midnight Drive" to "The Night Lines", "Coastal Highway" to "Paper Lanterns", "Slow Burn" to "Mira Vale",
            "静かな夜" to "Kumo", "Long Way Home" to "The Night Lines", "Afterglow" to "Sun Theory")
        val colors = listOf(0xFFB0524A, 0xFF3E6AE1, 0xFF25A06B, 0xFF8E5BD8, 0xFFD89B2B, 0xFF2B8FD8)
        val queue = titles.mapIndexed { i, (t, a) ->
            com.ahu.tlauncher.notify.QueueEntry(i + 1L, t, a, android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888).also { it.eraseColor(colors[i].toInt()) }, null, 180_000L + i * 23_000L)
        }
        HomeScreen(st.copy(media = st.media!!.copy(queue = queue, activeQueueId = 1, shuffleOn = false, repeatMode = 2)), NoopActions, initialOverlay = Overlay.MUSIC)
    }
    @Test fun settingsNavigation() = shot("settings_navigation") {
        HomeScreen(state().copy(googleMapsAvailable = true), NoopActions, initialOverlay = Overlay.SETTINGS, initialSettings = com.ahu.tlauncher.ui.settings.Section.NAVIGATION)
    }
    private fun routeState() = state().copy(
        googleMapsAvailable = true,
        settings = state().settings.copy(homeAddress = "123 Mabini St, Makati", workAddress = "Ayala Ave, Makati"),
        etaMin = mapOf("123 Mabini St, Makati" to 22, "Ayala Ave, Makati" to 35),
        favourites = listOf(com.ahu.tlauncher.route.Place("Gym", "Bonifacio Global City")),
        recents = listOf(com.ahu.tlauncher.route.Place("SM Megamall", "Ortigas Center, Mandaluyong"), com.ahu.tlauncher.route.Place("NAIA Terminal 3", "Pasay City")),
    )
    private val demoRoute = com.ahu.tlauncher.route.RouteData(
        listOf(com.ahu.tlauncher.route.LatLon(14.55, 121.02), com.ahu.tlauncher.route.LatLon(14.585, 121.056)), 1080, 7400, "EDSA",
        listOf(com.ahu.tlauncher.route.TrafficSpan(0, 1, com.ahu.tlauncher.route.Traffic.SLOW)),
    )
    @Test fun routeSearch() = shot("route_search") {
        HomeScreen(routeState().copy(search = com.ahu.tlauncher.route.SearchState("sm mega", listOf(
            com.ahu.tlauncher.route.Suggestion("a", "SM Megamall", "EDSA cor. Doña Julia Vargas Ave, Mandaluyong", 7400),
            com.ahu.tlauncher.route.Suggestion("b", "SM Megamall Cinema", "Building A, SM Megamall, Mandaluyong", 7500),
        ))), NoopActions, initialOverlay = Overlay.SEARCH)
    }
    @Test fun routeRecents() = shot("route_search_recents", dark = false) { HomeScreen(routeState(), NoopActions, initialOverlay = Overlay.SEARCH) }
    @Test fun routePreview() = shot("route_preview") {
        HomeScreen(routeState().copy(route = com.ahu.tlauncher.route.RouteState(
            com.ahu.tlauncher.route.Place("SM Megamall", "Ortigas Center, Mandaluyong", placeId = "a"), com.ahu.tlauncher.route.RoutePhase.PREVIEW, demoRoute, loading = false)), NoopActions)
    }
    @Test fun routeActive() = shot("route_active", dark = false) {
        HomeScreen(state(nav = true).copy(googleMapsAvailable = true, route = com.ahu.tlauncher.route.RouteState(
            com.ahu.tlauncher.route.Place("SM Megamall", "Ortigas Center"), com.ahu.tlauncher.route.RoutePhase.ACTIVE, demoRoute, loading = false, remainingM = 6100, remainingS = 900)), NoopActions)
    }
    @Test fun settingsLight() = shot("settings_display_light", dark = false) { HomeScreen(state(), NoopActions, initialOverlay = Overlay.SETTINGS) }
}
