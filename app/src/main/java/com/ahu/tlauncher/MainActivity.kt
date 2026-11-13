package com.ahu.tlauncher

import android.Manifest
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ahu.tlauncher.data.CarView
import com.ahu.tlauncher.data.NavApp
import com.ahu.tlauncher.data.ObdSourceType
import com.ahu.tlauncher.data.SpeedUnit
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.notify.NavParser
import com.ahu.tlauncher.notify.NotificationHub
import com.ahu.tlauncher.ui.home.CANVAS_H
import com.ahu.tlauncher.ui.home.CANVAS_W
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeScreen
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.theme.DayNight
import com.ahu.tlauncher.ui.theme.TLauncherTheme
import com.ahu.tlauncher.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.math.min

class MainActivity : ComponentActivity(), HomeActions {
    private val app get() = application as LauncherApp
    private var resumeTick by mutableStateOf(0)
    private var pendingCarView: CarView = CarView.SIDE

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.location.start(); resumeTick++
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val view = pendingCarView
            lifecycleScope.launch { app.prefs.setCarImage(view, uri.toString()) }
        }
    }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        if (!app.location.hasPermission()) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else app.location.start()

        // Seed the dock on first run once the app list has loaded.
        lifecycleScope.launch {
            val s = app.prefs.settings.first()
            if (!s.pinnedSeeded) {
                val apps = app.apps.apps.first { it.isNotEmpty() }
                if (apps.isNotEmpty()) app.prefs.setPinned(app.apps.defaultPins())
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { NotificationHub.refresh(this@MainActivity) }
        }

        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        NotificationHub.refresh(this)
        app.location.start()
        resumeTick++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("OVERRIDE_DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() { /* Home screen: back does nothing (overlays handle their own back). */ }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Composable
    private fun Root() {
        val settings by app.prefs.settings.collectAsState(initial = com.ahu.tlauncher.data.Settings())
        val obd by app.obd.state.collectAsState()
        val media by NotificationHub.media.collectAsState()
        val nav by NotificationHub.nav.collectAsState()
        val access by NotificationHub.accessGranted.collectAsState()
        val apps by app.apps.apps.collectAsState()
        val volume by app.volume.state.collectAsState()
        val location by app.location.location.collectAsState()
        val now by produceState(LocalDateTime.now()) {
            while (true) { value = LocalDateTime.now(); delay(1000 - (System.currentTimeMillis() % 1000)) }
        }
        val tick = resumeTick
        val btOn = remember(tick, now.minute) { bluetoothEnabled() }
        val isDefault = remember(tick) { isDefaultLauncher() }
        val locPerm = remember(tick) { app.location.hasPermission() }

        val dark = DayNight.isDark(settings.themeMode, ZonedDateTime.now(), location?.latitude, location?.longitude)
        LaunchedEffect(dark) {
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !dark
        }

        val state = HomeUiState(
            settings = settings, obd = obd, media = media, nav = nav, notificationAccess = access,
            apps = apps, volume = volume, now = now,
            locationLat = location?.latitude, locationLon = location?.longitude, locationPermission = locPerm,
            bluetoothOn = btOn, isDefaultLauncher = isDefault,
        )
        TLauncherTheme(dark = dark) { ScaledCanvas { HomeScreen(state, this@MainActivity) } }
    }

    // ---------------- HomeActions ----------------

    override fun launch(app: AppEntry) {
        if (!this.app.apps.launch(app)) toastMissing(app.label)
    }

    override fun togglePin(app: AppEntry) {
        lifecycleScope.launch {
            val cur = this@MainActivity.app.prefs.settings.first().pinnedApps
            this@MainActivity.app.prefs.setPinned(if (app.key in cur) cur - app.key else (cur + app.key).takeLast(6))
        }
    }

    override fun movePin(key: String, delta: Int) {
        lifecycleScope.launch {
            val cur = app.prefs.settings.first().pinnedApps.toMutableList()
            val i = cur.indexOf(key); val j = i + delta
            if (i >= 0 && j in cur.indices) { cur.removeAt(i); cur.add(j, key); app.prefs.setPinned(cur) }
        }
    }

    override fun playPause() = NotificationHub.playPause()
    override fun next() = NotificationHub.next()
    override fun previous() = NotificationHub.previous()
    override fun seekTo(ms: Long) = NotificationHub.seekTo(ms)

    override fun openMediaApp() {
        val pi = NotificationHub.mediaSessionActivity()
        if (pi != null) { runCatching { pi.send(this, 0, null, null, null, null, activityOptionsBundle()) }; return }
        val pkg = NotificationHub.media.value?.packageName
        if (pkg != null && app.apps.launchPackage(pkg)) return
        listOf("com.spotify.music", "com.google.android.apps.youtube.music").firstOrNull { app.apps.launchPackage(it) }
    }

    override fun openNavApp() {
        lifecycleScope.launch {
            val pref = app.prefs.settings.first().navApp
            val pkg = when (pref) { NavApp.MAPS -> NavParser.MAPS; NavApp.WAZE -> NavParser.WAZE; NavApp.ASK -> null }
            if (pkg != null && app.apps.launchPackage(pkg)) return@launch
            startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
        }
    }

    override fun navigateHome() = navigateTo { it.homeAddress }
    override fun navigateWork() = navigateTo { it.workAddress }

    private fun navigateTo(pick: (com.ahu.tlauncher.data.Settings) -> String) {
        lifecycleScope.launch {
            val s = app.prefs.settings.first()
            val addr = pick(s).trim()
            if (addr.isEmpty()) {
                android.widget.Toast.makeText(this@MainActivity, "Set the address in Settings → Navigation", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val q = Uri.encode(addr)
            val intent = when (s.navApp) {
                NavApp.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(NavParser.MAPS)
                NavApp.WAZE -> Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage(NavParser.WAZE)
                NavApp.ASK -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))
            }
            if (!startSafely(intent)) startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
        }
    }

    override fun openNavCard() {
        val n = NotificationHub.nav.value ?: return
        val sent = n.contentIntent?.let { runCatching { it.send(this, 0, null, null, null, null, activityOptionsBundle()) }.isSuccess } ?: false
        if (!sent) app.apps.launchPackage(n.info.packageName)
    }

    /** Android 14+ blocks background activity starts from PendingIntents unless the sender opts in. */
    private fun activityOptionsBundle(): Bundle? = if (Build.VERSION.SDK_INT >= 34) {
        ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
    } else null

    override fun volumeStep(up: Boolean) = app.volume.step(up)
    override fun setVolume(level: Int) = app.volume.set(level)
    override fun toggleMute() = app.volume.toggleMute()

    override fun requestNotificationAccess() {
        startSafely(NotificationHub.accessSettingsIntent())
    }

    override fun requestLocation() {
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun setDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.onSuccess { return }
            }
        }
        if (!startSafely(Intent(AndroidSettings.ACTION_HOME_SETTINGS))) startSafely(Intent(AndroidSettings.ACTION_SETTINGS))
    }

    override fun openAndroidSettings() { startSafely(Intent(AndroidSettings.ACTION_SETTINGS)) }

    override fun setTheme(mode: ThemeMode) { lifecycleScope.launch { app.prefs.setTheme(mode) } }
    override fun setSpeedUnit(u: SpeedUnit) { lifecycleScope.launch { app.prefs.setSpeedUnit(u) } }
    override fun setTempUnit(u: TempUnit) { lifecycleScope.launch { app.prefs.setTempUnit(u) } }
    override fun setNavApp(a: NavApp) { lifecycleScope.launch { app.prefs.setNavApp(a) } }
    override fun setHome(addr: String) { lifecycleScope.launch { app.prefs.setHome(addr) } }
    override fun setWork(addr: String) { lifecycleScope.launch { app.prefs.setWork(addr) } }

    override fun setObdSource(s: ObdSourceType) {
        if (s == ObdSourceType.BLUETOOTH && Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        lifecycleScope.launch { app.prefs.setObdSource(s) }
    }

    override fun pickObdDevice(addr: String, name: String) { lifecycleScope.launch { app.prefs.setObdDevice(addr, name) } }

    @android.annotation.SuppressLint("MissingPermission")
    override fun bondedDevices(): List<Pair<String, String>> {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT); return emptyList() }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty().map { it.address to (it.name ?: it.address) }
                .sortedByDescending { (_, n) -> n.contains("OBD", true) || n.contains("ELM", true) || n.contains("V-LINK", true) }
        }.getOrDefault(emptyList())
    }

    override fun pickCarImage(view: CarView) {
        pendingCarView = view
        runCatching { pickImage.launch(arrayOf("image/*")) }
    }

    override fun resetCarImage(view: CarView) { lifecycleScope.launch { app.prefs.setCarImage(view, "") } }

    // ---------------- helpers ----------------

    @android.annotation.SuppressLint("MissingPermission")
    private fun bluetoothEnabled(): Boolean = runCatching {
        getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun isDefaultLauncher(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    private fun startSafely(i: Intent): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    private fun toastMissing(label: String) =
        android.widget.Toast.makeText(this, "Couldn't open $label", android.widget.Toast.LENGTH_SHORT).show()
}

/**
 * Lays the UI out on a fixed 1024×600 dp design canvas and scales it to the real screen,
 * like a vehicle display. Extra aspect-ratio space widens/heightens the canvas rather than letterboxing.
 */
@Composable
fun ScaledCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val wDp = maxWidth.value; val hDp = maxHeight.value
        val scale = min(wDp / CANVAS_W, hDp / CANVAS_H)
        val w = wDp / scale; val h = hDp / scale
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, fontScale = 1f)) {
            androidx.compose.foundation.layout.Box(Modifier.requiredSize(w.dp, h.dp)) { content() }
        }
    }
}

@Composable
private fun <T> remember(vararg keys: Any?, calc: () -> T): T = androidx.compose.runtime.remember(*keys) { calc() }
