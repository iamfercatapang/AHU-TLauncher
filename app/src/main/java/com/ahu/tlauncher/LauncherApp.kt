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

class LauncherApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var prefs: Prefs; private set
    lateinit var apps: AppRepository; private set
    lateinit var obd: ObdManager; private set
    lateinit var location: LocationTracker; private set
    lateinit var volume: VolumeController; private set
    lateinit var weather: com.ahu.tlauncher.system.WeatherRepository; private set
    lateinit var updater: com.ahu.tlauncher.update.Updater; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        apps = AppRepository(this, appScope)
        obd = ObdManager(appScope, prefs.settings)
        location = LocationTracker(this)
        volume = VolumeController(this)
        weather = com.ahu.tlauncher.system.WeatherRepository(appScope, location.location)
        updater = com.ahu.tlauncher.update.Updater(this, appScope).also { it.startPeriodicChecks() }
    }
}
