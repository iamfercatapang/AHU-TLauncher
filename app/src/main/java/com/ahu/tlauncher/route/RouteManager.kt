package com.ahu.tlauncher.route

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RoutePhase { PREVIEW, ACTIVE }

data class RouteState(
    val destination: Place,
    val phase: RoutePhase,
    /** The chosen route (one of [alternatives] while previewing). */
    val route: RouteData? = null,
    /** Every route found for the preview, recommended first. Empty once the trip starts or is rerouted. */
    val alternatives: List<RouteData> = emptyList(),
    /** Index of [route] in [alternatives]. */
    val selected: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    /** Segment the car is on (points before it are already driven). */
    val progressSeg: Int = 0,
    /** Car position snapped near the route, where the drawn line starts. */
    val car: LatLon? = null,
    val remainingM: Int? = null,
    val remainingS: Int? = null,
    /** Free map: TLauncher guides the trip itself (next turn shown and spoken). */
    val guided: Boolean = false,
    val turn: Turn? = null,
)

data class SearchState(
    val query: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

interface PlacesStore {
    suspend fun recents(): List<Place>
    suspend fun favourites(): List<Place>
    suspend fun saveRecents(list: List<Place>)
    suspend fun saveFavourites(list: List<Place>)
}

/**
 * Destination search (Places API New) and routes (Routes API) for the dashboard map.
 * Voice guidance stays with Google Maps / Waze; this only draws and tracks the same trip.
 */
class RouteManager(
    private val scope: CoroutineScope,
    private val web: GoogleWeb,
    private val location: StateFlow<Location?>,
    private val store: PlacesStore,
    /** True when the free (OpenStreetMap) map is in use: free search/routing and TLauncher's own guidance. */
    private val freeMode: () -> Boolean = { false },
    private val speak: (String) -> Unit = {},
    private val stopSpeaking: () -> Unit = {},
    private val mph: () -> Boolean = { false },
    /** A trip started (remember it in case the car is switched off before arriving). */
    private val onTripStarted: (Place) -> Unit = {},
    /** The trip is finished or was ended by the user: forget it. */
    private val onTripFinished: () -> Unit = {},
) {
    private val _route = MutableStateFlow<RouteState?>(null)
    val route: StateFlow<RouteState?> = _route.asStateFlow()
    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private val _recents = MutableStateFlow<List<Place>>(emptyList())
    val recents: StateFlow<List<Place>> = _recents.asStateFlow()
    private val _favourites = MutableStateFlow<List<Place>>(emptyList())
    val favourites: StateFlow<List<Place>> = _favourites.asStateFlow()
    /** Drive minutes to saved addresses (Home / Work), keyed by address. */
    private val _etaMin = MutableStateFlow<Map<String, Int>>(emptyMap())
    val etaMin: StateFlow<Map<String, Int>> = _etaMin.asStateFlow()
    /** Spot long-pressed on the map, waiting for Directions. Its name fills in once the address is found. */
    private val _pin = MutableStateFlow<Place?>(null)
    val pin: StateFlow<Place?> = _pin.asStateFlow()

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var pinJob: Job? = null
    private var session = UUID.randomUUID().toString()
    private var offRouteSince = 0L
    private var lastReroute = 0L
    private var arriving = false
    private val etaFetchedAt = HashMap<String, Long>()

    /** Google search/routes need the key; free mode needs nothing. */
    val available get() = freeMode() || web.hasKey
    private val spoken = HashMap<Int, Int>() // step pointIndex → announcement stage spoken

    init {
        scope.launch {
            _recents.value = store.recents(); _favourites.value = store.favourites()
            location.collect { it?.let(::onLocation) }
        }
    }

    // ---------------------------------------------------------------- search

    fun search(query: String) {
        _search.value = _search.value.copy(query = query, error = null)
        searchJob?.cancel()
        if (query.isBlank()) { _search.value = SearchState(); return }
        searchJob = scope.launch {
            delay(300) // wait for typing to pause
            _search.value = _search.value.copy(loading = true)
            if (freeMode()) {
                _search.value = runCatching {
                    _search.value.copy(suggestions = RouteMath.nearestFirst(FreeNav.parseSearch(FreeNav.get(FreeNav.searchUrl(query, here())), here())), loading = false)
                }.getOrElse { _search.value.copy(loading = false, error = it.message) }
                return@launch
            }
            val body = JSONObject().put("input", query).put("sessionToken", session)
            here()?.let { h ->
                val c = JSONObject().put("latitude", h.lat).put("longitude", h.lon)
                body.put("origin", c)
                body.put("locationBias", JSONObject().put("circle", JSONObject().put("center", c).put("radius", 50_000.0)))
            }
            _search.value = runCatching {
                val json = web.post("https://places.googleapis.com/v1/places:autocomplete", body.toString(),
                    "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat,suggestions.placePrediction.distanceMeters", "Places API (New)")
                _search.value.copy(suggestions = RouteMath.nearestFirst(RouteMath.parseSuggestions(json).take(6)), loading = false)
            }.getOrElse { _search.value.copy(loading = false, error = it.message) }
        }
    }

    fun clearSearch() { searchJob?.cancel(); _search.value = SearchState(); session = UUID.randomUUID().toString() }

    /** Picked a suggestion: look up its position, then preview the route. */
    fun pick(s: Suggestion) {
        val tok = session
        clearSearch()
        preview(Place(s.title, s.subtitle, s.lat, s.lon, placeId = s.placeId), detailsSession = tok)
    }

    /** Drive times for the Home / Work shortcuts, refreshed at most every 10 minutes. */
    fun refreshEtas(addresses: List<String>) {
        val origin = here() ?: return
        addresses.filter { it.isNotBlank() }.forEach { a ->
            if (System.currentTimeMillis() - (etaFetchedAt[a] ?: 0) < 10 * 60_000) return@forEach
            etaFetchedAt[a] = System.currentTimeMillis()
            scope.launch {
                runCatching { computeRoute(origin, Place(a, a), null) }.getOrNull()?.let { r ->
                    _etaMin.value = _etaMin.value + (a to (r.durationS + 30) / 60)
                }
            }
        }
    }

    // ---------------------------------------------------------------- dropped pin

    /** Long-press on the map: show a pin there right away, then look up its address. Ignored while a route is shown. */
    fun dropPin(lat: Double, lon: Double) {
        if (_route.value != null) return
        pinJob?.cancel()
        _pin.value = Place("Dropped pin", RouteMath.coordText(lat, lon), lat, lon)
        pinJob = scope.launch {
            val named = runCatching { reverseGeocode(lat, lon) }.getOrNull() ?: return@launch
            if (_pin.value?.let { it.lat == lat && it.lon == lon } == true) _pin.value = named
        }
    }

    fun clearPin() { pinJob?.cancel(); _pin.value = null }

    /** Directions on the pin card: the usual route preview, to the exact spot pressed. */
    fun pinDirections() { val p = _pin.value ?: return; preview(p) }

    /** Google's address on the Google map (Geocoding API); OpenStreetMap's (Photon) on the free map or if Google fails. */
    private suspend fun reverseGeocode(lat: Double, lon: Double): Place? {
        if (!freeMode() && web.hasKey) {
            for (v in listOf("v4", "v4beta")) {
                runCatching {
                    RouteMath.parseReverse(web.get(String.format(java.util.Locale.US, "https://geocode.googleapis.com/%s/geocode/location/%.6f,%.6f", v, lat, lon), "*", "Geocoding API"), lat, lon)
                }.getOrNull()?.let { return it }
            }
        }
        return FreeNav.parseReverse(FreeNav.get(FreeNav.reverseUrl(lat, lon)), lat, lon)
    }

    // ---------------------------------------------------------------- route

    fun preview(place: Place, detailsSession: String? = null) {
        routeJob?.cancel()
        clearPin()
        _route.value = RouteState(place, RoutePhase.PREVIEW)
        routeJob = scope.launch {
            val origin = here() ?: run { _route.value = _route.value?.copy(loading = false, error = "Waiting for GPS position…"); return@launch }
            runCatching {
                var dest = place
                if (freeMode()) dest = freePlace(dest)
                else if (dest.latLon == null && dest.placeId != null && !dest.placeId!!.startsWith("osm:")) {
                    val q = if (detailsSession != null) "?sessionToken=$detailsSession" else ""
                    val json = web.get("https://places.googleapis.com/v1/places/${dest.placeId}$q", "id,displayName,formattedAddress,location", "Places API (New)")
                    dest = RouteMath.parsePlace(json, dest.placeId!!)?.let { it.copy(name = it.name.ifBlank { place.name }) } ?: dest
                }
                dest to computeRoutes(origin, dest, location.value?.takeIf { it.hasBearing() }?.bearing, alternatives = true)
            }.onSuccess { (dest, all) ->
                val r = all.first()
                _route.value = RouteState(dest, RoutePhase.PREVIEW, r, alternatives = all, loading = false, car = origin,
                    remainingM = r.distanceM, remainingS = r.durationS, guided = freeMode())
            }.onFailure { e ->
                _route.value = _route.value?.copy(loading = false, error = e.message)
            }
        }
    }

    /** Route tile tapped in the preview. */
    fun selectRoute(index: Int) {
        val s = _route.value ?: return
        if (s.phase != RoutePhase.PREVIEW) return
        val r = s.alternatives.getOrNull(index) ?: return
        _route.value = s.copy(route = r, selected = index, remainingM = r.distanceM, remainingS = r.durationS)
    }

    /** Start pressed: track progress, remember the place. */
    fun start(): Place? {
        val s = _route.value ?: return null
        if (s.route == null) return null
        _route.value = s.copy(phase = RoutePhase.ACTIVE, alternatives = emptyList(), selected = 0)
        offRouteSince = 0; lastReroute = System.currentTimeMillis(); arriving = false
        spoken.clear()
        if (s.guided) {
            val first = s.route.steps.firstOrNull { it.type != "depart" }
            speak("Starting route to ${s.destination.name}." + (first?.let { " " + FreeNav.instruction(it) + "." } ?: ""))
        }
        val p = s.destination
        onTripStarted(p)
        val list = (listOf(p) + _recents.value.filterNot { it.sameAs(p) }).take(8)
        _recents.value = list
        scope.launch { store.saveRecents(list) }
        return p
    }

    /**
     * Clears the route. [byUser] is false when it ends because Google Maps / Waze stopped navigating, which is
     * often just the car being switched off: then the trip is kept to offer again, unless the car is already there.
     */
    fun end(byUser: Boolean = true) {
        val s = _route.value
        if (s?.phase == RoutePhase.ACTIVE) {
            val left = s.car?.let { car -> s.route?.points?.lastOrNull()?.let { RouteMath.distanceM(car, it) } }
            if (com.ahu.tlauncher.system.DriveStartRules.forgetOnEnd(byUser, left)) onTripFinished()
        }
        routeJob?.cancel(); if (s?.guided == true) stopSpeaking(); _route.value = null
    }

    fun toggleFavourite(p: Place) {
        val list = if (_favourites.value.any { it.sameAs(p) }) _favourites.value.filterNot { it.sameAs(p) } else (_favourites.value + p).take(12)
        _favourites.value = list
        scope.launch { store.saveFavourites(list) }
    }

    fun isFavourite(p: Place) = _favourites.value.any { it.sameAs(p) }

    private fun onLocation(loc: Location) {
        val s = _route.value ?: return
        val r = s.route ?: return
        if (s.phase != RoutePhase.ACTIVE) return
        val p = LatLon(loc.latitude, loc.longitude)
        val (seg, off) = RouteMath.progress(r.points, p, s.progressSeg)
        val left = RouteMath.remainingM(r.points, seg, p)
        val leftS = if (r.distanceM > 0) (r.durationS * left / r.distanceM).toInt() else r.durationS
        val turn = if (s.guided) FreeNav.nextTurn(r, seg, p) else null
        _route.value = s.copy(progressSeg = seg, car = p, remainingM = left.toInt(), remainingS = leftS, turn = turn)
        val now = System.currentTimeMillis()
        // Free map: speak the next turn ahead of time, again when close, and at the turn.
        if (turn != null) {
            val kmh = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
            val done = spoken[turn.step.pointIndex] ?: 0
            val stage = FreeNav.dueStage(turn.distanceM, kmh, done)
            if (stage > 0) {
                spoken[turn.step.pointIndex] = stage
                val what = FreeNav.instruction(turn.step)
                speak(if (stage == 3) what else "In ${FreeNav.spokenDistance(turn.distanceM, mph())}, ${what.replaceFirstChar { it.lowercase() }}")
            }
        }
        // Arrived.
        if (RouteMath.distanceM(p, r.points.last()) < 40) {
            if (!arriving) { arriving = true; scope.launch { delay(8_000); if (_route.value?.destination == s.destination) end() } }
            return
        }
        // Off the line for 15 s (e.g. Google Maps chose another road): recalculate, at most every 30 s.
        // Off the line (e.g. a missed turn): recalculate. TLauncher guides free routes, so it reacts faster there.
        val wait = if (s.guided) 8_000 else 15_000
        val gap = if (s.guided) 15_000 else 30_000
        if (off > 60) {
            if (offRouteSince == 0L) offRouteSince = now
            if (now - offRouteSince > wait && now - lastReroute > gap) {
                lastReroute = now; offRouteSince = 0
                reroute(loc)
            }
        } else offRouteSince = 0
    }

    private fun reroute(loc: Location) {
        val s = _route.value ?: return
        routeJob?.cancel()
        routeJob = scope.launch {
            runCatching { computeRoute(LatLon(loc.latitude, loc.longitude), s.destination, loc.takeIf { it.hasBearing() }?.bearing) }
                .onSuccess { r ->
                    spoken.clear()
                    if (s.guided) speak("Route recalculated.")
                    _route.value = _route.value?.copy(route = r, progressSeg = 0, remainingM = r.distanceM, remainingS = r.durationS, error = null)
                }
        }
    }

    /**
     * A destination usable by the free router: OpenStreetMap places already have a position; Home/Work
     * addresses and places found with Google are looked up again on OpenStreetMap (Google's data stays on Google maps).
     */
    private suspend fun freePlace(p: Place): Place {
        if (p.latLon != null && (p.placeId == null || p.placeId.startsWith("osm:"))) return p
        val q = listOf(p.name, p.address).filter { it.isNotBlank() }.distinct().joinToString(", ")
        val hit = FreeNav.parseSearch(FreeNav.get(FreeNav.searchUrl(q, here())), here()).firstOrNull()
            ?: FreeNav.parseSearch(FreeNav.get(FreeNav.searchUrl(p.address.ifBlank { p.name }, here())), here()).firstOrNull()
            ?: throw ApiException("Couldn't find \"${p.name}\" on the free map")
        return p.copy(lat = hit.lat, lon = hit.lon, placeId = hit.placeId)
    }

    private suspend fun computeRoute(origin: LatLon, dest: Place, heading: Float?): RouteData =
        computeRoutes(origin, dest, heading, alternatives = false).first()

    /** The recommended route first; with [alternatives], up to two more. Never empty (throws instead). */
    private suspend fun computeRoutes(origin: LatLon, dest: Place, heading: Float?, alternatives: Boolean): List<RouteData> {
        if (freeMode()) {
            val d = freePlace(dest).latLon!!
            return FreeNav.parseRoutes(FreeNav.get(FreeNav.routeUrl(origin, d, alternatives))).take(3).ifEmpty { throw ApiException("No route found") }
        }
        fun ll(p: LatLon) = JSONObject().put("latLng", JSONObject().put("latitude", p.lat).put("longitude", p.lon))
        val o = ll(origin).apply { heading?.let { put("heading", it.toInt()) } }
        val d = dest.latLon?.let { JSONObject().put("location", ll(it)) }
            ?: dest.placeId?.let { JSONObject().put("placeId", it) }
            ?: JSONObject().put("address", dest.address)
        val body = JSONObject()
            .put("origin", JSONObject().put("location", o)).put("destination", d)
            .put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_AWARE")
            .put("extraComputations", JSONArray().put("TRAFFIC_ON_POLYLINE"))
            .put("computeAlternativeRoutes", alternatives)
        val json = web.post("https://routes.googleapis.com/directions/v2:computeRoutes", body.toString(),
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.description,routes.travelAdvisory.speedReadingIntervals", "Routes API")
        return RouteMath.parseRoutes(json).take(3).ifEmpty { throw ApiException("No route found") }
    }

    private fun here(): LatLon? = location.value?.let { LatLon(it.latitude, it.longitude) }
}
