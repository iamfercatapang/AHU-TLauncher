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
/** Low-performance mode: Auto turns it on for low-end head units. */
enum class PerfMode { AUTO, ON, OFF }
/** Saved places with their own shortcut: Home and Work. */
enum class SavedSpot(val label: String) { HOME("Home"), WORK("Work") }
enum class CarView(val label: String) { SIDE("Side"), FRONT("Front"), REAR("Rear") }

/**
 * Paint of the 3D car's body. [rgb] is the colour as seen (sRGB); [metallic] and [roughness] shape the
 * finish under the clear coat (low roughness = glossier).
 */
enum class CarPaint(val label: String, val rgb: Long, val metallic: Float, val roughness: Float) {
    WHITE("Metallic white", 0xE8E9E6, 0.35f, 0.32f),
    SILVER("Silver", 0xB4B7BA, 0.9f, 0.34f),
    GREY("Grey", 0x575B60, 0.85f, 0.36f),
    BLACK("Black", 0x111214, 0.6f, 0.3f),
    RED("Red", 0x8E141B, 0.55f, 0.34f),
    BLUE("Blue", 0x14306E, 0.6f, 0.34f),
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val tempUnit: TempUnit = TempUnit.C,
    val pinnedApps: List<String> = emptyList(),
    val homeAddress: String = "",
    val workAddress: String = "",
    /** Exact spot pinned on the map for Home / Work; null = find the typed address. */
    val homePos: com.ahu.tlauncher.route.LatLon? = null,
    val workPos: com.ahu.tlauncher.route.LatLon? = null,
    val obdSource: ObdSourceType = ObdSourceType.BLUETOOTH,
    val obdDeviceAddress: String = "",
    val obdDeviceName: String = "",
    /** User-chosen photo per view (content:// URI); empty = built-in image. */
    val carImages: Map<CarView, String> = emptyMap(),
    /** 3D model of the car in the car panel; false = photos (also the fallback without OpenGL ES 3). */
    val car3d: Boolean = true,
    val carPaint: CarPaint = CarPaint.WHITE,
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
    /** Screen brightness % for day and night (sunset to sunrise); -1 = not set yet. */
    val brightDay: Int = -1,
    val brightNight: Int = -1,
    /** Sound equalizer settings (EqMath.encode). */
    val eq: String = "",
    /** Package of the head unit's own EQ / DSP app, chosen once in Settings → Sound. */
    val unitEqApp: String = "",
    /** Engine size in cc (1.3 = 1329, 1.5 = 1496); only used when the car has no MAF sensor. */
    val engineCc: Int = 1496,
    val onboardingDone: Boolean = false,
    val pinnedSeeded: Boolean = false,
    val perfMode: PerfMode = PerfMode.AUTO,
    /** When the car starts: play the last song again if it was playing when the car was switched off. */
    val resumeMusic: Boolean = true,
    /** When the car starts: offer to continue a trip that wasn't finished. */
    val offerTrip: Boolean = true,
)

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val speedUnit = stringPreferencesKey("speed_unit")
        val tempUnit = stringPreferencesKey("temp_unit")
        val pinned = stringPreferencesKey("pinned")
        val home = stringPreferencesKey("home_addr")
        val work = stringPreferencesKey("work_addr")
        val homePos = stringPreferencesKey("home_pos")
        val workPos = stringPreferencesKey("work_pos")
        val obdSource = stringPreferencesKey("obd_source")
        val obdAddr = stringPreferencesKey("obd_addr")
        val obdName = stringPreferencesKey("obd_name")
        fun carImage(v: CarView) = stringPreferencesKey("car_image_" + v.name.lowercase())
        val carViews = stringPreferencesKey("car_views")
        val car3d = booleanPreferencesKey("car_3d")
        val carPaint = stringPreferencesKey("car_paint")
        val navApp = stringPreferencesKey("nav_app")
        val carSide = stringPreferencesKey("car_side")
        val lights = stringPreferencesKey("lights_source")
        val aaDetect = booleanPreferencesKey("aa_detect")
        val mapEngine = stringPreferencesKey("map_engine")
        val mapTraffic = booleanPreferencesKey("map_traffic")
        val recents = stringPreferencesKey("recent_places")
        val eq = stringPreferencesKey("eq")
        val unitEqApp = stringPreferencesKey("unit_eq_app")
        val brightDay = androidx.datastore.preferences.core.intPreferencesKey("bright_day")
        val brightNight = androidx.datastore.preferences.core.intPreferencesKey("bright_night")
        val favourites = stringPreferencesKey("favourite_places")
        val tankL = androidx.datastore.preferences.core.intPreferencesKey("tank_l")
        val engineCc = androidx.datastore.preferences.core.intPreferencesKey("engine_cc")
        val fuelKm = androidx.datastore.preferences.core.doublePreferencesKey("fuel_long_km")
        val fuelL = androidx.datastore.preferences.core.doublePreferencesKey("fuel_long_l")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val pinnedSeeded = booleanPreferencesKey("pinned_seeded")
        val perfMode = stringPreferencesKey("perf_mode")
        val resumeMusic = booleanPreferencesKey("resume_music")
        val offerTrip = booleanPreferencesKey("offer_trip")
        val tripPlace = stringPreferencesKey("trip_place")
        val tripStarted = androidx.datastore.preferences.core.longPreferencesKey("trip_started")
    }

    val settings: Flow<Settings> = context.store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        themeMode = enumOr(this[K.theme], ThemeMode.AUTO),
        speedUnit = enumOr(this[K.speedUnit], SpeedUnit.KMH),
        tempUnit = enumOr(this[K.tempUnit], TempUnit.C),
        pinnedApps = this[K.pinned]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        homeAddress = this[K.home] ?: "",
        workAddress = this[K.work] ?: "",
        homePos = com.ahu.tlauncher.route.RouteMath.decodeLatLon(this[K.homePos]),
        workPos = com.ahu.tlauncher.route.RouteMath.decodeLatLon(this[K.workPos]),
        obdSource = enumOr(this[K.obdSource], ObdSourceType.BLUETOOTH),
        obdDeviceAddress = this[K.obdAddr] ?: "",
        obdDeviceName = this[K.obdName] ?: "",
        carImages = CarView.entries.mapNotNull { v -> this[K.carImage(v)]?.takeIf { it.isNotBlank() }?.let { v to it } }.toMap(),
        carViews = this[K.carViews]?.split(',')?.mapNotNull { n -> CarView.entries.firstOrNull { it.name == n } }?.toSet()
            ?.ifEmpty { null } ?: CarView.entries.toSet(),
        car3d = this[K.car3d] ?: true,
        carPaint = enumOr(this[K.carPaint], CarPaint.WHITE),
        navApp = enumOr(this[K.navApp], NavApp.MAPS),
        carSide = enumOr(this[K.carSide], CarSide.LEFT),
        lightsSource = this[K.lights] ?: "off",
        aaDetect = this[K.aaDetect] ?: true,
        tankL = this[K.tankL] ?: 43,
        mapEngine = enumOr(this[K.mapEngine], MapEngine.GOOGLE),
        mapTraffic = this[K.mapTraffic] ?: true,
        eq = this[K.eq] ?: "",
        unitEqApp = this[K.unitEqApp] ?: "",
        brightDay = this[K.brightDay] ?: -1,
        brightNight = this[K.brightNight] ?: -1,
        engineCc = this[K.engineCc] ?: 1496,
        onboardingDone = this[K.onboarding] ?: false,
        pinnedSeeded = this[K.pinnedSeeded] ?: false,
        perfMode = enumOr(this[K.perfMode], PerfMode.AUTO),
        resumeMusic = this[K.resumeMusic] ?: true,
        offerTrip = this[K.offerTrip] ?: true,
    )

    suspend fun setTheme(m: ThemeMode) = context.store.edit { it[K.theme] = m.name }
    suspend fun setSpeedUnit(u: SpeedUnit) = context.store.edit { it[K.speedUnit] = u.name }
    suspend fun setTempUnit(u: TempUnit) = context.store.edit { it[K.tempUnit] = u.name }
    suspend fun setPinned(list: List<String>) = context.store.edit {
        it[K.pinned] = list.joinToString("\n"); it[K.pinnedSeeded] = true
    }
    /** A typed address replaces any spot pinned on the map. */
    suspend fun setHome(a: String) = context.store.edit { it[K.home] = a; it.remove(K.homePos) }
    suspend fun setWork(a: String) = context.store.edit { it[K.work] = a; it.remove(K.workPos) }
    /** Home / Work set from a spot pinned on the map: its address for display, its position for routing. */
    suspend fun setSavedSpot(spot: SavedSpot, address: String, lat: Double, lon: Double) = context.store.edit {
        val pos = com.ahu.tlauncher.route.RouteMath.encodeLatLon(com.ahu.tlauncher.route.LatLon(lat, lon))
        when (spot) {
            SavedSpot.HOME -> { it[K.home] = address; it[K.homePos] = pos }
            SavedSpot.WORK -> { it[K.work] = address; it[K.workPos] = pos }
        }
    }
    suspend fun setObdSource(s: ObdSourceType) = context.store.edit { it[K.obdSource] = s.name }
    suspend fun setObdDevice(addr: String, name: String) = context.store.edit {
        it[K.obdAddr] = addr; it[K.obdName] = name
    }
    suspend fun setCarImage(view: CarView, uri: String) = context.store.edit { it[K.carImage(view)] = uri }
    suspend fun setCarViews(views: Set<CarView>) = context.store.edit { it[K.carViews] = views.joinToString(",") { v -> v.name } }
    suspend fun setCar3d(on: Boolean) = context.store.edit { it[K.car3d] = on }
    suspend fun setCarPaint(p: CarPaint) = context.store.edit { it[K.carPaint] = p.name }
    suspend fun setLightsSource(s: String) = context.store.edit { it[K.lights] = s }
    suspend fun setAaDetect(on: Boolean) = context.store.edit { it[K.aaDetect] = on }
    /** Recent and favourite destinations for the route search. */
    val placesStore = object : com.ahu.tlauncher.route.PlacesStore {
        override suspend fun recents() = com.ahu.tlauncher.route.RouteMath.decodePlaces(context.store.data.first()[K.recents])
        override suspend fun favourites() = com.ahu.tlauncher.route.RouteMath.decodePlaces(context.store.data.first()[K.favourites])
        override suspend fun saveRecents(list: List<com.ahu.tlauncher.route.Place>) { context.store.edit { it[K.recents] = com.ahu.tlauncher.route.RouteMath.encodePlaces(list) } }
        override suspend fun saveFavourites(list: List<com.ahu.tlauncher.route.Place>) { context.store.edit { it[K.favourites] = com.ahu.tlauncher.route.RouteMath.encodePlaces(list) } }
    }
    suspend fun setEq(json: String) = context.store.edit { it[K.eq] = json }
    suspend fun setUnitEqApp(pkg: String) = context.store.edit { it[K.unitEqApp] = pkg }
    suspend fun setBrightness(night: Boolean, percent: Int) = context.store.edit { if (night) it[K.brightNight] = percent else it[K.brightDay] = percent }
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
    suspend fun setPerfMode(m: PerfMode) = context.store.edit { it[K.perfMode] = m.name }
    suspend fun setResumeMusic(on: Boolean) = context.store.edit { it[K.resumeMusic] = on }
    suspend fun setOfferTrip(on: Boolean) = context.store.edit { it[K.offerTrip] = on }

    /** The trip in progress, kept so it can be offered again after the car is switched off mid-trip. */
    suspend fun savedTrip(): com.ahu.tlauncher.system.SavedTrip? = context.store.data.first().let { p ->
        val place = com.ahu.tlauncher.route.RouteMath.decodePlaces(p[K.tripPlace]).firstOrNull() ?: return null
        com.ahu.tlauncher.system.SavedTrip(place, p[K.tripStarted] ?: 0L)
    }
    suspend fun saveTrip(place: com.ahu.tlauncher.route.Place, atMs: Long) = context.store.edit {
        it[K.tripPlace] = com.ahu.tlauncher.route.RouteMath.encodePlaces(listOf(place)); it[K.tripStarted] = atMs
    }
    suspend fun clearTrip() = context.store.edit { it.remove(K.tripPlace); it.remove(K.tripStarted) }
    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }

    private inline fun <reified E : Enum<E>> enumOr(v: String?, def: E): E =
        v?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def
}
