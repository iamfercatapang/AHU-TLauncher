package com.ahu.tlauncher

import android.app.Application
import com.ahu.tlauncher.data.Prefs
import com.ahu.tlauncher.launcher.AppRepository
import com.ahu.tlauncher.obd.ObdManager
import com.ahu.tlauncher.system.LocationTracker
import com.ahu.tlauncher.system.VolumeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LauncherApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var prefs: Prefs; private set
    lateinit var apps: AppRepository; private set
    lateinit var obd: ObdManager; private set
    lateinit var location: LocationTracker; private set
    lateinit var volume: VolumeController; private set
    lateinit var weather: com.ahu.tlauncher.system.WeatherRepository; private set
    lateinit var lights: com.ahu.tlauncher.system.LightsMonitor; private set
    lateinit var calls: com.ahu.tlauncher.system.CallControl; private set
    lateinit var fuel: com.ahu.tlauncher.obd.FuelTracker; private set
    @Volatile private var fuelSettings = com.ahu.tlauncher.data.Settings()
    lateinit var routes: com.ahu.tlauncher.route.RouteManager; private set
    lateinit var voice: com.ahu.tlauncher.system.Voice; private set
    /** Free map in use (chosen in Settings, or Google not available): free routing + TLauncher's own guidance. */
    @Volatile var freeMap = true
    /** The dashboard (MainActivity) is in the foreground. */
    @Volatile var dashboardVisible = false
    lateinit var lastMedia: com.ahu.tlauncher.notify.LastMedia; private set
    lateinit var brightness: com.ahu.tlauncher.system.BrightnessControl; private set
    lateinit var updater: com.ahu.tlauncher.update.Updater; private set
    lateinit var resumeOnStart: com.ahu.tlauncher.system.ResumeOnStart; private set
    lateinit var carCare: com.ahu.tlauncher.system.CarCare; private set
    lateinit var offlineMaps: com.ahu.tlauncher.map.OfflineMaps; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        apps = AppRepository(this, appScope)
        obd = ObdManager(appScope, prefs.settings)
        location = LocationTracker(this)
        // GPS from the start (the trip log runs even before the dashboard is first shown); 1 s updates throughout.
        location.start(); location.setFast(true)
        volume = VolumeController(this)
        weather = com.ahu.tlauncher.system.WeatherRepository(appScope, location.location)
        lights = com.ahu.tlauncher.system.LightsMonitor(this)
        calls = com.ahu.tlauncher.system.CallControl(this)
        brightness = com.ahu.tlauncher.system.BrightnessControl(this)
        lastMedia = com.ahu.tlauncher.notify.LastMedia(this, appScope)
        voice = com.ahu.tlauncher.system.Voice(this)
        routes = com.ahu.tlauncher.route.RouteManager(
            appScope, com.ahu.tlauncher.route.GoogleWeb(this), location.location, prefs.placesStore,
            freeMode = { freeMap },
            speak = { voice.speak(it) }, stopSpeaking = { voice.stop() },
            mph = { fuelSettings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH },
            onTripStarted = { p -> appScope.launch { prefs.saveTrip(p, System.currentTimeMillis()) } },
            onTripFinished = { appScope.launch { prefs.clearTrip() } },
            onStopReached = { p -> resumeOnStart.offerNow(p) },
        )
        fuel = com.ahu.tlauncher.obd.FuelTracker(
            appScope, obd.state, tankL = { fuelSettings.tankL }, displacementL = { fuelSettings.engineCc / 1000.0 }, store = prefs.fuelStore,
        )
        appScope.launch {
            prefs.settings.collect { s ->
                fuelSettings = s
                lights.setSource(com.ahu.tlauncher.system.LightsSource.decode(s.lightsSource))
            }
        }
        updater = com.ahu.tlauncher.update.Updater(this, appScope).also { it.startPeriodicChecks() }
        carCare = com.ahu.tlauncher.system.CarCare(this, appScope)
        resumeOnStart = com.ahu.tlauncher.system.ResumeOnStart(this, appScope)
        offlineMaps = com.ahu.tlauncher.map.OfflineMaps(this, appScope)
    }
}
