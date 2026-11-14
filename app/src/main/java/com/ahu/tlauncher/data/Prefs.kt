package com.ahu.tlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahu.tlauncher.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("launcher_prefs")

enum class SpeedUnit { KMH, MPH }
enum class TempUnit { C, F }
enum class ObdSourceType { DEMO, BLUETOOTH }
enum class NavApp { MAPS, WAZE, ASK }
/** Which side the car panel sits on in the car + map split view. */
enum class CarSide { LEFT, RIGHT }
enum class MapEngine { GOOGLE, OPENFREEMAP }
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
    /** Views shown on the dashboard (tap the car to cycle); at least one. */
    val carViews: Set<CarView> = CarView.entries.toSet(),
    val navApp: NavApp = NavApp.MAPS,
    val carSide: CarSide = CarSide.LEFT,
    /** How the head unit reports its ILL (lights-on) wire, see LightsSource.encode(). */
    val lightsSource: String = "off",
    /** Look for the phone's Android Auto head unit server on Wi-Fi. */
    val aaDetect: Boolean = true,
    /** Fuel tank capacity in litres (2018 Avanza: 43 L), for the range estimate. */
    val tankL: Int = 43,
    /** Google is used when available (key + Play services); OpenFreeMap otherwise. */
    val mapEngine: MapEngine = MapEngine.GOOGLE,
    val mapTraffic: Boolean = true,
    /** Engine size in cc (1.3 = 1329, 1.5 = 1496); only used when the car has no MAF sensor. */
    val engineCc: Int = 1496,
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
        val carViews = stringPreferencesKey("car_views")
        val navApp = stringPreferencesKey("nav_app")
        val carSide = stringPreferencesKey("car_side")
        val lights = stringPreferencesKey("lights_source")
        val aaDetect = booleanPreferencesKey("aa_detect")
        val mapEngine = stringPreferencesKey("map_engine")
        val mapTraffic = booleanPreferencesKey("map_traffic")
        val recents = stringPreferencesKey("recent_places")
        val favourites = stringPreferencesKey("favourite_places")
        val tankL = androidx.datastore.preferences.core.intPreferencesKey("tank_l")
        val engineCc = androidx.datastore.preferences.core.intPreferencesKey("engine_cc")
        val fuelKm = androidx.datastore.preferences.core.doublePreferencesKey("fuel_long_km")
        val fuelL = androidx.datastore.preferences.core.doublePreferencesKey("fuel_long_l")
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
        carViews = this[K.carViews]?.split(',')?.mapNotNull { n -> CarView.entries.firstOrNull { it.name == n } }?.toSet()
            ?.ifEmpty { null } ?: CarView.entries.toSet(),
        navApp = enumOr(this[K.navApp], NavApp.MAPS),
        carSide = enumOr(this[K.carSide], CarSide.LEFT),
        lightsSource = this[K.lights] ?: "off",
        aaDetect = this[K.aaDetect] ?: true,
        tankL = this[K.tankL] ?: 43,
        mapEngine = enumOr(this[K.mapEngine], MapEngine.GOOGLE),
        mapTraffic = this[K.mapTraffic] ?: true,
        engineCc = this[K.engineCc] ?: 1496,
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
    suspend fun setCarViews(views: Set<CarView>) = context.store.edit { it[K.carViews] = views.joinToString(",") { v -> v.name } }
    suspend fun setLightsSource(s: String) = context.store.edit { it[K.lights] = s }
    suspend fun setAaDetect(on: Boolean) = context.store.edit { it[K.aaDetect] = on }
    /** Recent and favourite destinations for the route search. */
    val placesStore = object : com.ahu.tlauncher.route.PlacesStore {
        override suspend fun recents() = com.ahu.tlauncher.route.RouteMath.decodePlaces(context.store.data.first()[K.recents])
        override suspend fun favourites() = com.ahu.tlauncher.route.RouteMath.decodePlaces(context.store.data.first()[K.favourites])
        override suspend fun saveRecents(list: List<com.ahu.tlauncher.route.Place>) { context.store.edit { it[K.recents] = com.ahu.tlauncher.route.RouteMath.encodePlaces(list) } }
        override suspend fun saveFavourites(list: List<com.ahu.tlauncher.route.Place>) { context.store.edit { it[K.favourites] = com.ahu.tlauncher.route.RouteMath.encodePlaces(list) } }
    }
    suspend fun setMapEngine(e: MapEngine) = context.store.edit { it[K.mapEngine] = e.name }
    suspend fun setMapTraffic(on: Boolean) = context.store.edit { it[K.mapTraffic] = on }
    suspend fun setTankL(l: Int) = context.store.edit { it[K.tankL] = l.coerceIn(20, 120) }
    suspend fun setEngineCc(cc: Int) = context.store.edit { it[K.engineCc] = cc }
    /** Long-term fuel totals (km, litres). */
    val fuelStore = object : com.ahu.tlauncher.obd.FuelStore {
        override suspend fun load(): Pair<Double, Double> = context.store.data.first().let { (it[K.fuelKm] ?: 0.0) to (it[K.fuelL] ?: 0.0) }
        override suspend fun save(km: Double, litres: Double) { context.store.edit { it[K.fuelKm] = km; it[K.fuelL] = litres } }
    }
    suspend fun setCarSide(side: CarSide) = context.store.edit { it[K.carSide] = side.name }
    suspend fun setNavApp(a: NavApp) = context.store.edit { it[K.navApp] = a.name }
    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }

    private inline fun <reified E : Enum<E>> enumOr(v: String?, def: E): E =
        v?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def
}
