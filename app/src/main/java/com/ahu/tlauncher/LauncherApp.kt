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
    lateinit var androidAuto: com.ahu.tlauncher.system.AndroidAutoDetector; private set
    lateinit var fuel: com.ahu.tlauncher.obd.FuelTracker; private set
    @Volatile private var fuelSettings = com.ahu.tlauncher.data.Settings()
    lateinit var routes: com.ahu.tlauncher.route.RouteManager; private set
    lateinit var lastMedia: com.ahu.tlauncher.notify.LastMedia; private set
    lateinit var updater: com.ahu.tlauncher.update.Updater; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        apps = AppRepository(this, appScope)
        obd = ObdManager(appScope, prefs.settings)
        location = LocationTracker(this)
        volume = VolumeController(this)
        weather = com.ahu.tlauncher.system.WeatherRepository(appScope, location.location)
        lights = com.ahu.tlauncher.system.LightsMonitor(this)
        calls = com.ahu.tlauncher.system.CallControl(this)
        lastMedia = com.ahu.tlauncher.notify.LastMedia(this, appScope)
        routes = com.ahu.tlauncher.route.RouteManager(appScope, com.ahu.tlauncher.route.GoogleWeb(this), location.location, prefs.placesStore)
        androidAuto = com.ahu.tlauncher.system.AndroidAutoDetector(this, appScope)
        fuel = com.ahu.tlauncher.obd.FuelTracker(
            appScope, obd.state, tankL = { fuelSettings.tankL }, displacementL = { fuelSettings.engineCc / 1000.0 }, store = prefs.fuelStore,
        )
        appScope.launch {
            prefs.settings.collect { s ->
                fuelSettings = s
                lights.setSource(com.ahu.tlauncher.system.LightsSource.decode(s.lightsSource))
                androidAuto.enabled = s.aaDetect
            }
        }
        updater = com.ahu.tlauncher.update.Updater(this, appScope).also { it.startPeriodicChecks() }
    }
}
