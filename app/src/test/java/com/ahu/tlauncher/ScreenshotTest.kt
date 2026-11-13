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
        obd = ObdState(ObdStatus.DEMO, ObdData(speedKmh = 62, rpm = 2340, coolantC = 86, batteryV = 14.1)),
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
    @Test fun settingsLight() = shot("settings_display_light", dark = false) { HomeScreen(state(), NoopActions, initialOverlay = Overlay.SETTINGS) }
}
