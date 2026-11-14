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
    val route: RouteData? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** Segment the car is on (points before it are already driven). */
    val progressSeg: Int = 0,
    /** Car position snapped near the route, where the drawn line starts. */
    val car: LatLon? = null,
    val remainingM: Int? = null,
    val remainingS: Int? = null,
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

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var session = UUID.randomUUID().toString()
    private var offRouteSince = 0L
    private var lastReroute = 0L
    private var arriving = false
    private val etaFetchedAt = HashMap<String, Long>()

    val available get() = web.hasKey

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
            val body = JSONObject().put("input", query).put("sessionToken", session)
            here()?.let { h ->
                val c = JSONObject().put("latitude", h.lat).put("longitude", h.lon)
                body.put("origin", c)
                body.put("locationBias", JSONObject().put("circle", JSONObject().put("center", c).put("radius", 50_000.0)))
            }
            _search.value = runCatching {
                val json = web.post("https://places.googleapis.com/v1/places:autocomplete", body.toString(),
                    "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat,suggestions.placePrediction.distanceMeters", "Places API (New)")
                _search.value.copy(suggestions = RouteMath.parseSuggestions(json).take(6), loading = false)
            }.getOrElse { _search.value.copy(loading = false, error = it.message) }
        }
    }

    fun clearSearch() { searchJob?.cancel(); _search.value = SearchState(); session = UUID.randomUUID().toString() }

    /** Picked a suggestion: look up its position, then preview the route. */
    fun pick(s: Suggestion) {
        val tok = session
        clearSearch()
        preview(Place(s.title, s.subtitle, placeId = s.placeId), detailsSession = tok)
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

    // ---------------------------------------------------------------- route

    fun preview(place: Place, detailsSession: String? = null) {
        routeJob?.cancel()
        _route.value = RouteState(place, RoutePhase.PREVIEW)
        routeJob = scope.launch {
            val origin = here() ?: run { _route.value = _route.value?.copy(loading = false, error = "Waiting for GPS position…"); return@launch }
            runCatching {
                var dest = place
                if (dest.latLon == null && dest.placeId != null) {
                    val q = if (detailsSession != null) "?sessionToken=$detailsSession" else ""
                    val json = web.get("https://places.googleapis.com/v1/places/${dest.placeId}$q", "id,displayName,formattedAddress,location", "Places API (New)")
                    dest = RouteMath.parsePlace(json, dest.placeId!!)?.let { it.copy(name = it.name.ifBlank { place.name }) } ?: dest
                }
                dest to computeRoute(origin, dest, location.value?.takeIf { it.hasBearing() }?.bearing)
            }.onSuccess { (dest, r) ->
                _route.value = RouteState(dest, RoutePhase.PREVIEW, r, loading = false, car = origin, remainingM = r.distanceM, remainingS = r.durationS)
            }.onFailure { e ->
                _route.value = _route.value?.copy(loading = false, error = e.message)
            }
        }
    }

    /** Start pressed: track progress, remember the place. */
    fun start(): Place? {
        val s = _route.value ?: return null
        if (s.route == null) return null
        _route.value = s.copy(phase = RoutePhase.ACTIVE)
        offRouteSince = 0; lastReroute = System.currentTimeMillis(); arriving = false
        val p = s.destination
        val list = (listOf(p) + _recents.value.filterNot { it.sameAs(p) }).take(8)
        _recents.value = list
        scope.launch { store.saveRecents(list) }
        return p
    }

    fun end() { routeJob?.cancel(); _route.value = null }

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
        _route.value = s.copy(progressSeg = seg, car = p, remainingM = left.toInt(), remainingS = leftS)
        val now = System.currentTimeMillis()
        // Arrived.
        if (RouteMath.distanceM(p, r.points.last()) < 40) {
            if (!arriving) { arriving = true; scope.launch { delay(8_000); if (_route.value?.destination == s.destination) end() } }
            return
        }
        // Off the line for 15 s (e.g. Google Maps chose another road): recalculate, at most every 30 s.
        if (off > 60) {
            if (offRouteSince == 0L) offRouteSince = now
            if (now - offRouteSince > 15_000 && now - lastReroute > 30_000) {
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
                .onSuccess { r -> _route.value = _route.value?.copy(route = r, progressSeg = 0, remainingM = r.distanceM, remainingS = r.durationS, error = null) }
        }
    }

    private suspend fun computeRoute(origin: LatLon, dest: Place, heading: Float?): RouteData {
        fun ll(p: LatLon) = JSONObject().put("latLng", JSONObject().put("latitude", p.lat).put("longitude", p.lon))
        val o = ll(origin).apply { heading?.let { put("heading", it.toInt()) } }
        val d = dest.latLon?.let { JSONObject().put("location", ll(it)) }
            ?: dest.placeId?.let { JSONObject().put("placeId", it) }
            ?: JSONObject().put("address", dest.address)
        val body = JSONObject()
            .put("origin", JSONObject().put("location", o)).put("destination", d)
            .put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_AWARE")
            .put("extraComputations", JSONArray().put("TRAFFIC_ON_POLYLINE"))
        val json = web.post("https://routes.googleapis.com/directions/v2:computeRoutes", body.toString(),
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.description,routes.travelAdvisory.speedReadingIntervals", "Routes API")
        return RouteMath.parseRoute(json) ?: throw ApiException("No route found")
    }

    private fun here(): LatLon? = location.value?.let { LatLon(it.latitude, it.longitude) }
}
