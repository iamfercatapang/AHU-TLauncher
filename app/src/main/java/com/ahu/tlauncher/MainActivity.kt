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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ahu.tlauncher.data.CarView
import com.ahu.tlauncher.data.NavApp
import com.ahu.tlauncher.data.ObdSourceType
import com.ahu.tlauncher.data.SpeedUnit
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.data.savedPlace
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.notify.NavParser
import com.ahu.tlauncher.notify.NotificationHub
import com.ahu.tlauncher.ui.home.CANVAS_H
import com.ahu.tlauncher.ui.home.CANVAS_W
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeScreen
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.home.LiveState
import com.ahu.tlauncher.ui.theme.DayNight
import com.ahu.tlauncher.ui.theme.LocalLowPerf
import com.ahu.tlauncher.ui.theme.TLauncherTheme
import com.ahu.tlauncher.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val phonePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumeTick++ }
    private var lightsOffSnapshot: com.ahu.tlauncher.system.LightsSnapshot? = null
    private var callMuted by mutableStateOf(false)
    private var callAnsweredAt by mutableStateOf<Long?>(null)
    private var navSeenDuringTrip = false
    private var brightnessNow by mutableStateOf(50)
    private var brightnessIsNight = false
    private var brightSaveJob: kotlinx.coroutines.Job? = null
    private var aaDismissedOn by mutableStateOf<String?>(null)
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
        app.dashboardVisible = true
        hideSystemBars()
        NotificationHub.refresh(this)
        app.location.start()
        app.location.setFast(true)
        app.updater.checkIfStale()
        app.lights.refresh()
        app.androidAuto.setActive(true)
        resumeTick++
        // Back from the "Display over other apps" page: carry on with the trip.
        if (pendingRouteStart) { pendingRouteStart = false; launchRoute() }
    }

    override fun onPause() {
        super.onPause()
        app.dashboardVisible = false
        app.location.setFast(false)
        app.androidAuto.setActive(false)
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

    /**
     * Builds the home screen state. Only slow-changing values go into [HomeUiState] (Root re-runs at most
     * about once a minute while nothing is touched). Engine data, GPS and trip fuel go into [LiveState],
     * which only the widgets that show them read. All collection pauses while the launcher is hidden.
     */
    @Composable
    private fun Root() {
        val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = com.ahu.tlauncher.data.Settings())
        val media by NotificationHub.media.collectAsStateWithLifecycle()
        val nav by NotificationHub.nav.collectAsStateWithLifecycle()
        val access by NotificationHub.accessGranted.collectAsStateWithLifecycle()
        val apps by app.apps.apps.collectAsStateWithLifecycle()
        val volume by app.volume.state.collectAsStateWithLifecycle()
        val update by app.updater.state.collectAsStateWithLifecycle()
        val weather by app.weather.weather.collectAsStateWithLifecycle()
        val diag by app.obd.diag.collectAsStateWithLifecycle()
        val lights by app.lights.state.collectAsStateWithLifecycle()
        val notifCall by NotificationHub.call.collectAsStateWithLifecycle()
        val aa by app.androidAuto.state.collectAsStateWithLifecycle()
        val route by app.routes.route.collectAsStateWithLifecycle()
        val search by app.routes.search.collectAsStateWithLifecycle()
        val recents by app.routes.recents.collectAsStateWithLifecycle()
        val favourites by app.routes.favourites.collectAsStateWithLifecycle()
        val pin by app.routes.pin.collectAsStateWithLifecycle()
        val picking by app.routes.picking.collectAsStateWithLifecycle()
        val etaMin by app.routes.etaMin.collectAsStateWithLifecycle()
        val lastMedia by app.lastMedia.last.collectAsStateWithLifecycle()
        val resumingMedia by app.lastMedia.resuming.collectAsStateWithLifecycle()
        val inbox by NotificationHub.inbox.collectAsStateWithLifecycle()
        val tripOffer by app.resumeOnStart.offer.collectAsStateWithLifecycle()
        val offlineAreas by app.offlineMaps.areas.collectAsStateWithLifecycle()
        // "Continue" on the trip offer: start as soon as the route is found.
        LaunchedEffect(route, autoStartTrip) {
            val r = route
            if (!autoStartTrip || r == null || r.phase != com.ahu.tlauncher.route.RoutePhase.PREVIEW || r.loading) return@LaunchedEffect
            autoStartTrip = false
            if (r.route != null) startRoute()
        }

        // Low-performance mode: set in Settings, or Auto from this unit's RAM and cores.
        val spec = remember { com.ahu.tlauncher.system.DeviceSpec.read(this) }
        val lowPerf = com.ahu.tlauncher.system.PerfPolicy.active(settings.perfMode, spec)
        val live = remember { LiveState() }
        LaunchedEffect(live, lowPerf) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { feedLive(live, lowPerf) } }

        // The trip ends when Google Maps / Waze stops navigating (their turn notification disappears for 20 s).
        val navOn = nav != null
        LaunchedEffect(navOn, route?.phase) {
            if (navOn || !access || route?.phase != com.ahu.tlauncher.route.RoutePhase.ACTIVE || route?.guided == true) return@LaunchedEffect
            delay(if (navSeenDuringTrip) 20_000 else 120_000)
            if (NotificationHub.nav.value == null) app.routes.end(byUser = false)
        }
        LaunchedEffect(navOn) { if (navOn && route?.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE) navSeenDuringTrip = true }
        LaunchedEffect(route == null) { if (route == null) navSeenDuringTrip = false }
        val dismissedCode by app.updater.dismissedCode.collectAsStateWithLifecycle()
        // The clock only shows minutes, so it ticks on the minute.
        val now by produceState(LocalDateTime.now()) {
            while (true) { value = LocalDateTime.now(); delay(60_000 - (System.currentTimeMillis() % 60_000)) }
        }
        val tick = resumeTick
        // Status bar indicators, read off the main thread while the launcher is on screen.
        // Bluetooth: re-read on every Bluetooth broadcast (adapter, device, audio/call profile), and every 15 s in case one is missed.
        val bt by produceState(com.ahu.tlauncher.system.BtStatus.OFF, tick) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.merge(
                    com.ahu.tlauncher.system.Connectivity.bluetoothChanges(this@MainActivity),
                    kotlinx.coroutines.flow.flow { while (true) { delay(15_000); emit(Unit) } },
                ).collectLatest {
                    // Profiles connect a moment after the device itself: read now and once more shortly after.
                    value = withContext(Dispatchers.IO) { com.ahu.tlauncher.system.Connectivity.bluetoothStatus(this@MainActivity) }
                    delay(1_500)
                    value = withContext(Dispatchers.IO) { com.ahu.tlauncher.system.Connectivity.bluetoothStatus(this@MainActivity) }
                }
            }
        }
        val btOn = bt != com.ahu.tlauncher.system.BtStatus.OFF
        val wifi by polled(com.ahu.tlauncher.system.WifiStatus(false, false, 0), tick, 5_000) { com.ahu.tlauncher.system.Connectivity.wifi(this) }
        val cell by polled<com.ahu.tlauncher.system.CellStatus?>(null, tick, 10_000) { com.ahu.tlauncher.system.Connectivity.cell(this) }
        val isDefault = remember(tick) { isDefaultLauncher() }
        val locPerm = remember(tick) { app.location.hasPermission() }

        // Day/night only needs a rough position (0.1° ≈ 11 km), so GPS fixes don't redraw the screen.
        val roughPlace by remember {
            app.location.location.map { l -> l?.let { Math.round(it.latitude * 10) to Math.round(it.longitude * 10) } }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(null)
        val lat = roughPlace?.first?.div(10.0)
        val lon = roughPlace?.second?.div(10.0)
        val dark = remember(now, settings.themeMode, roughPlace) { DayNight.isDark(settings.themeMode, ZonedDateTime.now(), lat, lon) }
        LaunchedEffect(dark) {
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !dark
        }

        // Calls: the phone app's notification if there is one, else Android's call system (polled each second).
        val telecom by polled(com.ahu.tlauncher.system.TelecomCall.NONE, tick, 1_000) { app.calls.telecomState() }
        val n = notifCall
        val ringing = n?.info?.phase == com.ahu.tlauncher.notify.CallPhase.RINGING || (n == null && telecom == com.ahu.tlauncher.system.TelecomCall.RINGING)
        val callActive = n != null || telecom != com.ahu.tlauncher.system.TelecomCall.NONE
        LaunchedEffect(callActive, ringing) {
            if (!callActive) { callAnsweredAt = null; callMuted = false }
            else if (!ringing && callAnsweredAt == null) callAnsweredAt = n?.info?.chronometerBaseMs ?: System.currentTimeMillis()
        }
        val call = if (!callActive) null else com.ahu.tlauncher.ui.home.CallUi(
            ringing = ringing,
            caller = n?.info?.caller ?: if (ringing) "Incoming call" else "Call in progress",
            startedAtMs = if (ringing) null else callAnsweredAt ?: n?.info?.chronometerBaseMs,
            muted = callMuted,
            canSwitchAudio = n?.info?.actions?.containsKey(com.ahu.tlauncher.notify.CallAction.AUDIO_SWITCH) == true,
        )
        val aaReceiver = remember(tick) { app.androidAuto.receiverPackage()?.let { app.androidAuto.receiverLabel(it) } }
        val callPerm = remember(tick) { app.calls.hasPermissions() }
        val returnPerm = remember(tick) { canReturnToFront() }
        // Brightness: one level for day, one for night (sunset to sunrise, same as the auto dark theme).
        val brightAllowed = remember(tick) { app.brightness.canWrite() }
        val night = remember(now, roughPlace) { DayNight.isDark(ThemeMode.AUTO, ZonedDateTime.now(), lat, lon) }
        LaunchedEffect(night, brightAllowed, settings.brightDay, settings.brightNight) {
            val level = if (night) settings.brightNight else settings.brightDay
            if (brightAllowed && level > 0 && level != brightnessNow) { app.brightness.set(level); brightnessNow = level }
        }
        LaunchedEffect(tick) { brightnessNow = app.brightness.get() }
        brightnessIsNight = night
        val freeMap = !(googleMapsAvailable && settings.mapEngine == com.ahu.tlauncher.data.MapEngine.GOOGLE)
        app.freeMap = freeMap
        val state = HomeUiState(
            settings = settings, live = live, media = media, nav = nav, notificationAccess = access,
            apps = apps, volume = volume, now = now,
            googleMapsAvailable = googleMapsAvailable, locationPermission = locPerm,
            bluetoothOn = btOn, bluetooth = bt, wifi = wifi, isDefaultLauncher = isDefault,
            update = update, updateDismissedCode = dismissedCode, appVersion = BuildConfig.VERSION_NAME,
            weather = weather?.takeIf { System.currentTimeMillis() - it.fetchedAtMs < com.ahu.tlauncher.system.WeatherParser.MAX_AGE_MS },
            diag = diag,
            lastMedia = if (media == null) lastMedia else null, resumingMedia = resumingMedia,
            route = route, search = search, freeNav = freeMap, cell = cell, inbox = inbox, recents = recents, favourites = favourites, pin = pin, picking = picking, etaMin = etaMin,
            lights = lights,
            call = call, callPermission = callPerm, returnPermission = returnPerm,
            brightness = brightnessNow, brightnessNight = night, brightnessAllowed = brightAllowed, callSource = remember(notifCall) { NotificationHub.lastCallPackage },
            aa = aa, aaReceiver = aaReceiver,
            aaDismissed = (aa as? com.ahu.tlauncher.system.AaState.Ready)?.gateway?.let { it == aaDismissedOn } == true,
            lowPerf = lowPerf, deviceSpec = spec,
            tripOffer = tripOffer, offlineAreas = offlineAreas,
        )
        CompositionLocalProvider(LocalLowPerf provides lowPerf) {
            TLauncherTheme(dark = dark) { ScaledCanvas { HomeScreen(state, this@MainActivity) } }
        }
    }

    /**
     * Copies engine data, trip fuel and GPS into [live] while the launcher is visible. OBD readings that
     * change nothing on screen are skipped; low-performance mode passes at most two a second.
     */
    private suspend fun feedLive(live: LiveState, lowPerf: Boolean): Unit = coroutineScope {
        val gap = if (lowPerf) com.ahu.tlauncher.system.PerfPolicy.LOW_PERF_UI_MS else 0L
        launch {
            app.obd.state.collect { s ->
                if (com.ahu.tlauncher.obd.ObdDisplay.visiblyChanged(live.obd, s)) { live.obd = s; if (gap > 0) delay(gap) }
            }
        }
        // Trip totals and range: once a second is plenty.
        launch { app.fuel.stats.collect { live.fuel = it; delay(maxOf(gap, 1_000L)) } }
        launch {
            app.location.location.collectLatest { l ->
                live.locationLat = l?.latitude
                live.locationLon = l?.longitude
                live.locationBearing = l?.takeIf { it.hasBearing() }?.bearing
                live.gpsSpeedKmh = l?.let(::gpsKmh)
                // No new fix: the GPS speed goes stale (and blank) after 10 s.
                if (l != null) { delay(10_500); live.gpsSpeedKmh = gpsKmh(l) }
            }
        }
        Unit
    }

    private fun gpsKmh(l: android.location.Location): Int? {
        val age = (android.os.SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000
        return com.ahu.tlauncher.obd.GpsSpeed.kmh(l.hasSpeed(), l.speed, age)
    }

    /** A value read every [everyMs] on a background thread while the launcher is on screen; re-read when [key] changes. */
    @Composable
    private fun <T> polled(initial: T, key: Any?, everyMs: Long, read: () -> T): State<T> {
        val r = rememberUpdatedState(read)
        return produceState(initial, key) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = withContext(Dispatchers.IO) { runCatching { r.value() }.getOrDefault(value) }
                    delay(everyMs)
                }
            }
        }
    }

    // ---------------- HomeActions ----------------

    override fun launch(app: AppEntry) {
        if (!this.app.apps.launch(app)) toastMissing(app.label)
    }


    override fun setBrightness(percent: Int) {
        if (!app.brightness.canWrite()) { requestBrightnessPermission(); return }
        brightnessNow = percent
        app.brightness.set(percent)
        // Remember it as the level for the current time of day (saved once the slider settles).
        val night = brightnessIsNight
        brightSaveJob?.cancel()
        brightSaveJob = lifecycleScope.launch { delay(600); app.prefs.setBrightness(night, percent) }
    }

    override fun requestBrightnessPermission() {
        android.widget.Toast.makeText(this, "Turn on \"Modify system settings\" so TLauncher can change the brightness", android.widget.Toast.LENGTH_LONG).show()
        startSafely(Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun appInfo(app: AppEntry) {
        startSafely(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
    }

    /** Android shows its own "Do you want to uninstall this app?" confirmation. */
    override fun uninstallApp(app: AppEntry) {
        @Suppress("DEPRECATION")
        val i = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.packageName}"))
        if (!startSafely(i)) startSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
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

    override fun openInbox(item: com.ahu.tlauncher.notify.InboxItem) = NotificationHub.openInbox(item)
    override fun dismissInbox(item: com.ahu.tlauncher.notify.InboxItem) = NotificationHub.dismissInbox(item)
    override fun dismissAllInbox() = NotificationHub.dismissAllInbox()

    override fun playPause() {
        // Nothing running (e.g. after the engine was off): wake the last music app up.
        if (NotificationHub.media.value == null) app.lastMedia.resume { pkg -> if (!app.apps.launchPackage(pkg)) toastMissing(pkg) }
        else NotificationHub.playPause()
    }
    override fun next() = NotificationHub.next()
    override fun previous() = NotificationHub.previous()
    override fun seekTo(ms: Long) = NotificationHub.seekTo(ms)
    override fun toggleShuffle() = NotificationHub.toggleShuffle()
    override fun cycleRepeat() = NotificationHub.cycleRepeat()
    override fun playQueueItem(id: Long) = NotificationHub.playQueueItem(id)

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

    override fun navigateHome() = navigateTo(com.ahu.tlauncher.data.SavedSpot.HOME)
    override fun navigateWork() = navigateTo(com.ahu.tlauncher.data.SavedSpot.WORK)

    @Suppress("UNUSED_PARAMETER")
    private fun routesOn(s: com.ahu.tlauncher.data.Settings) = app.routes.available

    private fun navigateTo(spot: com.ahu.tlauncher.data.SavedSpot) {
        lifecycleScope.launch {
            val s = app.prefs.settings.first()
            val place = s.savedPlace(spot)
            if (place == null) {
                android.widget.Toast.makeText(this@MainActivity, "Set the address in Settings → Navigation", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            // With the Google map: show the route first; Start hands it to Google Maps / Waze.
            if (routesOn(s)) { app.routes.preview(place); return@launch }
            // A spot pinned on the map goes by its coordinates, a typed address by its text.
            val at = place.latLon?.let { String.format(java.util.Locale.US, "%.6f,%.6f", it.lat, it.lon) }
            val q = Uri.encode(at ?: place.address)
            val intent = when (s.navApp) {
                NavApp.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(NavParser.MAPS)
                NavApp.WAZE -> Intent(Intent.ACTION_VIEW, Uri.parse(if (at != null) "https://waze.com/ul?ll=$q&navigate=yes" else "https://waze.com/ul?q=$q&navigate=yes")).setPackage(NavParser.WAZE)
                NavApp.ASK -> Intent(Intent.ACTION_VIEW, Uri.parse(if (at != null) "geo:$at?q=$q(${Uri.encode(spot.label)})" else "geo:0,0?q=$q"))
            }
            if (!startSafely(intent)) startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
        }
    }

    // ---- dashboard route
    override fun searchPlaces(query: String) = app.routes.search(query)
    override fun clearSearch() = app.routes.clearSearch()
    override fun pickSuggestion(s: com.ahu.tlauncher.route.Suggestion) = app.routes.pick(s)
    override fun previewPlace(p: com.ahu.tlauncher.route.Place) = app.routes.preview(p)
    /** End: clear the dashboard route and stop Google Maps / Waze too (via its "Exit navigation" button). */
    override fun endRoute() {
        val wasActive = app.routes.route.value?.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        app.routes.end()
        if (!wasActive) return // Cancel on the preview: nothing is navigating yet
        val name = NotificationHub.nav.value?.info?.appLabel ?: "Google Maps"
        if (NotificationHub.nav.value != null && !NotificationHub.exitNavigation()) {
            android.widget.Toast.makeText(this, "Stop navigation in $name too", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun toggleFavourite(p: com.ahu.tlauncher.route.Place) = app.routes.toggleFavourite(p)
    override fun dropPin(lat: Double, lon: Double) = app.routes.dropPin(lat, lon)
    override fun clearPin() = app.routes.clearPin()
    override fun pinDirections() = app.routes.pinDirections()
    override fun selectRoute(index: Int) = app.routes.selectRoute(index)
    override fun refreshEtas() {
        lifecycleScope.launch {
            val s = app.prefs.settings.first()
            app.routes.refreshEtas(com.ahu.tlauncher.data.SavedSpot.entries.mapNotNull { s.savedPlace(it) })
        }
    }
    override fun pickOnMap(spot: com.ahu.tlauncher.data.SavedSpot): Boolean {
        if (app.routes.startPicking(spot)) return true
        android.widget.Toast.makeText(this, "End the trip first, then pin ${spot.label}", android.widget.Toast.LENGTH_LONG).show()
        return false
    }
    override fun cancelPicking() = app.routes.cancelPicking()
    override fun savePinAs(spot: com.ahu.tlauncher.data.SavedSpot) {
        val p = app.routes.takePinFor(spot) ?: return
        lifecycleScope.launch {
            app.prefs.setSavedSpot(spot, com.ahu.tlauncher.route.RouteMath.savedAddress(p), p.lat!!, p.lon!!)
            android.widget.Toast.makeText(this@MainActivity, "${spot.label} saved", android.widget.Toast.LENGTH_SHORT).show()
            refreshEtas()
        }
    }

    /** Start: voice guidance in Google Maps / Waze, then bring the dashboard (with the route) back to the front. */
    override fun startRoute() {
        // Android only lets an app return to the front from the background with "Display over other apps".
        // Ask once; if the user declines, the trip still starts and they press Home to come back.
        if (!app.freeMap && !canReturnToFront() && !overlayAsked) {
            overlayAsked = true
            pendingRouteStart = true
            android.widget.Toast.makeText(this, "Turn on \"Display over other apps\" so TLauncher can come back after Google Maps starts", android.widget.Toast.LENGTH_LONG).show()
            if (startSafely(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))) return
            pendingRouteStart = false
        }
        launchRoute()
    }

    private var overlayAsked = false
    private var pendingRouteStart = false
    private fun canReturnToFront() = AndroidSettings.canDrawOverlays(this)

    override fun requestReturnPermission() {
        startSafely(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun launchRoute() {
        // Free map: TLauncher guides the trip itself; nothing to hand over.
        if (app.freeMap) { app.routes.start(); return }
        val p = app.routes.start() ?: return
        lifecycleScope.launch {
            val nav = app.prefs.settings.first().navApp
            val dest = p.latLon?.let { "${it.lat},${it.lon}" } ?: Uri.encode(p.address)
            val intent = when (nav) {
                NavApp.WAZE -> Intent(Intent.ACTION_VIEW, Uri.parse(p.latLon?.let { "https://waze.com/ul?ll=$dest&navigate=yes" } ?: "https://waze.com/ul?q=$dest&navigate=yes")).setPackage(NavParser.WAZE)
                else -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$dest")).setPackage(NavParser.MAPS)
            }
            if (!startSafely(intent)) {
                android.widget.Toast.makeText(this@MainActivity, "Couldn't open ${if (nav == NavApp.WAZE) "Waze" else "Google Maps"} for voice guidance", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            if (!canReturnToFront()) {
                android.widget.Toast.makeText(this@MainActivity, "Press Home to see the route on the dashboard", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            // Give the navigation app time to start guiding, then return (it keeps talking in the background).
            delay(2_500)
            runCatching {
                startActivity(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
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

    override suspend fun bluetoothDetails(): com.ahu.tlauncher.system.BtDetails {
        val d = com.ahu.tlauncher.system.Connectivity.bluetoothDetails(this)
        // Android 12+: listing paired devices needs "Nearby devices". Ask once while the launcher runs.
        if (!d.permitted && d.status != com.ahu.tlauncher.system.BtStatus.OFF && !askedBtForPopup) {
            askedBtForPopup = true
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return d
    }
    private var askedBtForPopup = false
    override fun wifiDetails(scan: Boolean) = com.ahu.tlauncher.system.Connectivity.wifiDetails(this, scan)
    override fun cellDetails() = com.ahu.tlauncher.system.Connectivity.cellDetails(this)
    override fun openBluetoothSettings() { openFirst(AndroidSettings.ACTION_BLUETOOTH_SETTINGS) }
    override fun openWifiSettings() { openFirst(AndroidSettings.ACTION_WIFI_SETTINGS) }
    override fun openMobileSettings() {
        openFirst(AndroidSettings.ACTION_NETWORK_OPERATOR_SETTINGS, AndroidSettings.ACTION_DATA_ROAMING_SETTINGS, AndroidSettings.ACTION_WIRELESS_SETTINGS)
    }

    /** Opens the first settings page this head unit has, else Android's main settings. */
    private fun openFirst(vararg actions: String) {
        if (actions.none { startSafely(Intent(it)) } && !startSafely(Intent(AndroidSettings.ACTION_SETTINGS))) toastMissing("Settings")
    }
    override fun startUpdate() = app.updater.install()
    override fun dismissUpdate() = app.updater.dismiss()
    override fun checkForUpdates() { lifecycleScope.launch { app.updater.check(manual = true) } }

    override fun setTheme(mode: ThemeMode) { lifecycleScope.launch { app.prefs.setTheme(mode) } }
    override fun setSpeedUnit(u: SpeedUnit) { lifecycleScope.launch { app.prefs.setSpeedUnit(u) } }
    override fun setTempUnit(u: TempUnit) { lifecycleScope.launch { app.prefs.setTempUnit(u) } }
    override fun setNavApp(a: NavApp) { lifecycleScope.launch { app.prefs.setNavApp(a) } }
    override fun setCarSide(side: com.ahu.tlauncher.data.CarSide) { lifecycleScope.launch { app.prefs.setCarSide(side) } }
    override fun setPerfMode(m: com.ahu.tlauncher.data.PerfMode) { lifecycleScope.launch { app.prefs.setPerfMode(m) } }
    override fun setCar3d(on: Boolean) {
        if (on) com.ahu.tlauncher.ui.home.Car3d.resetCrashGuard(this)
        lifecycleScope.launch { app.prefs.setCar3d(on) }
    }
    override fun setCarPaint(p: com.ahu.tlauncher.data.CarPaint) { lifecycleScope.launch { app.prefs.setCarPaint(p) } }
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

    override fun setCarViewShown(view: CarView, shown: Boolean) {
        lifecycleScope.launch {
            val cur = app.prefs.settings.first().carViews
            val next = if (shown) cur + view else cur - view
            if (next.isNotEmpty()) app.prefs.setCarViews(next)
        }
    }
    // ---- headlights (ILL)
    override fun lightsLearnOff() { lightsOffSnapshot = app.lights.snapshot() }
    override fun lightsLearnOn(): String {
        val off = lightsOffSnapshot ?: return "Start again: switch the lights off first."
        val src = com.ahu.tlauncher.system.LightsLogic.learn(off, app.lights.snapshot())
        lightsOffSnapshot = null
        if (src == null) return "No signal changed. Your head unit doesn't seem to share the ILL wire with apps."
        lifecycleScope.launch { app.prefs.setLightsSource(src.encode()) }
        return "Found: ${src.label()}"
    }
    override fun lightsDisable() { lifecycleScope.launch { app.prefs.setLightsSource("off") } }

    // ---- calls
    override fun answerCall() = app.calls.answer()
    override fun endCall() = app.calls.end(ringing = NotificationHub.call.value?.info?.phase == com.ahu.tlauncher.notify.CallPhase.RINGING ||
        (NotificationHub.call.value == null && app.calls.telecomState() == com.ahu.tlauncher.system.TelecomCall.RINGING))
    override fun toggleCallMute() { callMuted = app.calls.toggleMute(callMuted) }
    override fun switchCallAudio() { app.calls.switchAudio() }
    override fun requestCallPermission() {
        phonePermission.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS))
    }

    // ---- Android Auto (Headunit Reloaded)
    override fun openAndroidAuto() {
        val pkg = app.androidAuto.receiverPackage() ?: return getHeadunitReloaded()
        if (app.apps.launchPackage(pkg)) app.androidAuto.reset()
    }
    override fun dismissAndroidAuto() { aaDismissedOn = (app.androidAuto.state.value as? com.ahu.tlauncher.system.AaState.Ready)?.gateway }
    override fun setAaDetect(on: Boolean) { lifecycleScope.launch { app.prefs.setAaDetect(on) } }
    override fun getHeadunitReloaded() {
        if (!startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=gb.xxy.hr")))) {
            startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=gb.xxy.hr")))
        }
    }

    override fun setObdExtended(on: Boolean) = app.obd.setExtended(on)
    override fun readObdCodes() = app.obd.readCodes()
    override fun clearObdCodes() = app.obd.clearCodes()
    override fun resetTrip() = app.fuel.resetTrip()
    private val googleMapsAvailable by lazy {
        BuildConfig.HAS_MAPS_KEY && runCatching {
            com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) ==
                com.google.android.gms.common.ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
    override fun setMapEngine(e: com.ahu.tlauncher.data.MapEngine) { lifecycleScope.launch { app.prefs.setMapEngine(e) } }
    override fun setMapTraffic(on: Boolean) { lifecycleScope.launch { app.prefs.setMapTraffic(on) } }
    override fun setResumeMusic(on: Boolean) { lifecycleScope.launch { app.prefs.setResumeMusic(on) } }
    override fun setOfferTrip(on: Boolean) { lifecycleScope.launch { app.prefs.setOfferTrip(on) } }
    override fun setOpenOnStart(on: Boolean) { lifecycleScope.launch { app.prefs.setOpenOnStart(on) } }
    override fun loadOfflineAreas() = app.offlineMaps.load()
    override fun saveOfflineArea(name: String, lat: Double, lon: Double, radiusKm: Int) = app.offlineMaps.save(name, lat, lon, radiusKm)
    override fun updateOfflineArea(id: Long) = app.offlineMaps.update(id)
    override fun deleteOfflineArea(id: Long) = app.offlineMaps.delete(id)
    private var autoStartTrip by mutableStateOf(false)
    override fun continueTrip() {
        val t = app.resumeOnStart.take() ?: return
        lifecycleScope.launch {
            // Right after the car starts there may be no GPS fix yet; the route needs one.
            kotlinx.coroutines.withTimeoutOrNull(20_000) { app.location.location.first { it != null } }
            autoStartTrip = true
            app.routes.preview(t.destination)
        }
    }
    override fun dismissTrip() = app.resumeOnStart.dismiss()
    override fun setTankL(l: Int) { lifecycleScope.launch { app.prefs.setTankL(l) } }
    override fun setEngineCc(cc: Int) { lifecycleScope.launch { app.prefs.setEngineCc(cc) } }
    override fun resetCarImage(view: CarView) { lifecycleScope.launch { app.prefs.setCarImage(view, "") } }

    // ---------------- helpers ----------------

    @android.annotation.SuppressLint("MissingPermission")
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
