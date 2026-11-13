package com.ahu.tlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahu.tlauncher.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("launcher_prefs")

enum class SpeedUnit { KMH, MPH }
enum class TempUnit { C, F }
enum class ObdSourceType { DEMO, BLUETOOTH }
enum class NavApp { MAPS, WAZE, ASK }
enum class CarView(val label: String) { SIDE("Side"), FRONT("Front"), REAR("Rear") }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val tempUnit: TempUnit = TempUnit.C,
    val pinnedApps: List<String> = emptyList(),
    val homeAddress: String = "",
    val workAddress: String = "",
    val obdSource: ObdSourceType = ObdSourceType.BLUETOOTH,
    val obdDeviceAddress: String = "",
    val obdDeviceName: String = "",
    /** User-chosen photo per view (content:// URI); empty = built-in image. */
    val carImages: Map<CarView, String> = emptyMap(),
    val navApp: NavApp = NavApp.MAPS,
    val onboardingDone: Boolean = false,
    val pinnedSeeded: Boolean = false,
)

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val speedUnit = stringPreferencesKey("speed_unit")
        val tempUnit = stringPreferencesKey("temp_unit")
        val pinned = stringPreferencesKey("pinned")
        val home = stringPreferencesKey("home_addr")
        val work = stringPreferencesKey("work_addr")
        val obdSource = stringPreferencesKey("obd_source")
        val obdAddr = stringPreferencesKey("obd_addr")
        val obdName = stringPreferencesKey("obd_name")
        fun carImage(v: CarView) = stringPreferencesKey("car_image_" + v.name.lowercase())
        val navApp = stringPreferencesKey("nav_app")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val pinnedSeeded = booleanPreferencesKey("pinned_seeded")
    }

    val settings: Flow<Settings> = context.store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        themeMode = enumOr(this[K.theme], ThemeMode.AUTO),
        speedUnit = enumOr(this[K.speedUnit], SpeedUnit.KMH),
        tempUnit = enumOr(this[K.tempUnit], TempUnit.C),
        pinnedApps = this[K.pinned]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        homeAddress = this[K.home] ?: "",
        workAddress = this[K.work] ?: "",
        obdSource = enumOr(this[K.obdSource], ObdSourceType.BLUETOOTH),
        obdDeviceAddress = this[K.obdAddr] ?: "",
        obdDeviceName = this[K.obdName] ?: "",
        carImages = CarView.entries.mapNotNull { v -> this[K.carImage(v)]?.takeIf { it.isNotBlank() }?.let { v to it } }.toMap(),
        navApp = enumOr(this[K.navApp], NavApp.MAPS),
        onboardingDone = this[K.onboarding] ?: false,
        pinnedSeeded = this[K.pinnedSeeded] ?: false,
    )

    suspend fun setTheme(m: ThemeMode) = context.store.edit { it[K.theme] = m.name }
    suspend fun setSpeedUnit(u: SpeedUnit) = context.store.edit { it[K.speedUnit] = u.name }
    suspend fun setTempUnit(u: TempUnit) = context.store.edit { it[K.tempUnit] = u.name }
    suspend fun setPinned(list: List<String>) = context.store.edit {
        it[K.pinned] = list.joinToString("\n"); it[K.pinnedSeeded] = true
    }
    suspend fun setHome(a: String) = context.store.edit { it[K.home] = a }
    suspend fun setWork(a: String) = context.store.edit { it[K.work] = a }
    suspend fun setObdSource(s: ObdSourceType) = context.store.edit { it[K.obdSource] = s.name }
    suspend fun setObdDevice(addr: String, name: String) = context.store.edit {
        it[K.obdAddr] = addr; it[K.obdName] = name
    }
    suspend fun setCarImage(view: CarView, uri: String) = context.store.edit { it[K.carImage(view)] = uri }
    suspend fun setNavApp(a: NavApp) = context.store.edit { it[K.navApp] = a.name }
    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }

    private inline fun <reified E : Enum<E>> enumOr(v: String?, def: E): E =
        v?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def
}
