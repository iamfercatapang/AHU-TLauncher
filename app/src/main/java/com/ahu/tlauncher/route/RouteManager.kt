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

    /** Home / Work being chosen on the map: the next pin offers "Set as Home" instead of Directions. */
    private val _picking = MutableStateFlow<com.ahu.tlauncher.data.SavedSpot?>(null)
    val picking: StateFlow<com.ahu.tlauncher.data.SavedSpot?> = _picking.asStateFlow()

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
                _search.value = runCancellable {
                    _search.value.copy(suggestions = RouteMath.nearestFirst(FreeNav.parseSearch(FreeNav.get(FreeNav.searchUrl(query, here())), here())), loading = false)
                }.getOrElse { _search.value.copy(loading = false, error = userMessage(it, "Couldn't search")) }
                return@launch
            }
            val body = JSONObject().put("input", query).put("sessionToken", session)
            here()?.let { h ->
                val c = JSONObject().put("latitude", h.lat).put("longitude", h.lon)
                body.put("origin", c)
                body.put("locationBias", JSONObject().put("circle", JSONObject().put("center", c).put("radius", 50_000.0)))
            }
            _search.value = runCancellable {
                val json = web.post("https://places.googleapis.com/v1/places:autocomplete", body.toString(),
                    "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat,suggestions.placePrediction.distanceMeters", "Places API (New)")
                _search.value.copy(suggestions = RouteMath.nearestFirst(RouteMath.parseSuggestions(json).take(6)), loading = false)
            }.getOrElse { _search.value.copy(loading = false, error = userMessage(it, "Couldn't search")) }
        }
    }

    fun clearSearch() { searchJob?.cancel(); _search.value = SearchState(); session = UUID.randomUUID().toString() }

    /** Picked a suggestion: look up its position, then preview the route. */
    fun pick(s: Suggestion) {
        val tok = session
        clearSearch()
        preview(Place(s.title, s.subtitle, s.lat, s.lon, placeId = s.placeId), detailsSession = tok)
    }

    /** Drive times for the Home / Work shortcuts (keyed by address), refreshed at most every 10 minutes. */
    fun refreshEtas(places: List<Place>) {
        val origin = here() ?: return
        places.filter { it.address.isNotBlank() }.forEach { p ->
            val key = p.address + "|" + p.latLon
            if (System.currentTimeMillis() - (etaFetchedAt[key] ?: 0) < 10 * 60_000) return@forEach
            etaFetchedAt[key] = System.currentTimeMillis()
            scope.launch {
                runCancellable { computeRoute(origin, p, null) }.getOrNull()?.let { r ->
                    _etaMin.value = _etaMin.value + (p.address to (r.durationS + 30) / 60)
                }
            }
        }
    }

    // ---------------------------------------------------------------- dropped pin

    /** Long-press on the map: show a pin there right away, then look up its address. Ignored while a route is shown. */
    fun dropPin(lat: Double, lon: Double) {
        if (_route.value != null) return
        pinJob?.cancel()
        _pin.value = Place(RouteMath.DROPPED_PIN, RouteMath.coordText(lat, lon), lat, lon)
        pinJob = scope.launch {
            val named = runCancellable { reverseGeocode(lat, lon) }.getOrNull() ?: return@launch
            if (_pin.value?.let { it.lat == lat && it.lon == lon } == true) _pin.value = named
        }
    }

    fun clearPin() { pinJob?.cancel(); _pin.value = null }

    /**
     * "Pin on map" for Home / Work in Settings: the next long-press on the map is offered as that place.
     * Not while a trip is running (returns false); a route preview is closed.
     */
    fun startPicking(spot: com.ahu.tlauncher.data.SavedSpot): Boolean {
        if (_route.value?.phase == RoutePhase.ACTIVE) return false
        if (_route.value != null) end()
        clearPin()
        _picking.value = spot
        return true
    }

    fun cancelPicking() { _picking.value = null; clearPin() }

    /** The pin to save as [spot], ending picking mode. Null when there's no pin. */
    fun takePinFor(spot: com.ahu.tlauncher.data.SavedSpot): Place? {
        val p = _pin.value?.takeIf { it.latLon != null } ?: return null
        _picking.value = null
        clearPin()
        return p
    }

    /** Directions on the pin card: the usual route preview, to the exact spot pressed. */
    fun pinDirections() { val p = _pin.value ?: return; preview(p) }

    /** Google's address on the Google map (Geocoding API); OpenStreetMap's (Photon) on the free map or if Google fails. */
    private suspend fun reverseGeocode(lat: Double, lon: Double): Place? {
        if (!freeMode() && web.hasKey) {
            for (v in listOf("v4", "v4beta")) {
                runCancellable {
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
            runCancellable {
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
                _route.value = _route.value?.copy(loading = false, error = userMessage(e, "Couldn't find a route"))
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
        offRouteSince = 0; lastReroute = 0; arriving = false
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
        val kmh = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
        val (found, off) = RouteMath.progress(r.points, p, s.progressSeg)
        val segLen = if (found + 1 < r.points.size) RouteMath.distanceM(r.points[found], r.points[found + 1]) else 0.0
        val segBearing = if (found + 1 < r.points.size) RouteMath.bearingDeg(r.points[found], r.points[found + 1]) else null
        val wrongWay = OffRoute.wrongWay(loc.takeIf { it.hasBearing() }?.bearing, kmh, segBearing, segLen)
        val isOff = OffRoute.isOff(off, loc.takeIf { it.hasAccuracy() }?.accuracy, wrongWay)
        // Off the route, progress stays where the car left it: a later part of the route that happens to pass nearby
        // mustn't count as driven (it would trim the line and hide that the car is off route).
        val seg = if (isOff) s.progressSeg else found
        val left = RouteMath.remainingM(r.points, seg, p)
        val leftS = if (r.distanceM > 0) (r.durationS * left / r.distanceM).toInt() else r.durationS
        // No turn instructions from a route the car has left.
        val turn = if (s.guided && !isOff) FreeNav.nextTurn(r, seg, p) else null
        _route.value = s.copy(progressSeg = seg, car = p, remainingM = left.toInt(), remainingS = leftS, turn = turn)
        val now = System.currentTimeMillis()
        // Free map: speak the next turn ahead of time, again when close, and at the turn.
        if (turn != null) {
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
        // Off the line (a missed turn, the wrong way, or Google Maps chose another road): recalculate after a few
        // seconds. TLauncher guides free routes itself, so it reacts faster there.
        if (isOff) {
            if (offRouteSince == 0L) offRouteSince = now
            if (OffRoute.due(s.guided, offRouteSince, lastReroute, now) && routeJob?.isActive != true) {
                lastReroute = now; offRouteSince = 0
                reroute(loc, kmh)
            }
        } else offRouteSince = 0
    }

    private fun reroute(loc: Location, kmh: Int) {
        val s = _route.value ?: return
        routeJob?.cancel()
        // The heading makes the new route start the way the car is going (no "make a U-turn" when moving).
        val heading = loc.takeIf { it.hasBearing() && kmh >= 10 }?.bearing
        routeJob = scope.launch {
            runCancellable { computeRoute(LatLon(loc.latitude, loc.longitude), s.destination, heading) }
                .onSuccess { r ->
                    spoken.clear()
                    if (s.guided) speak("Route recalculated.")
                    _route.value = _route.value?.copy(route = r, progressSeg = 0, remainingM = r.distanceM, remainingS = r.durationS, error = null)
                }
                // Couldn't get one (no signal, service busy): try again soon instead of after the full wait.
                .onFailure { lastReroute = System.currentTimeMillis() - OffRoute.gapMs(s.guided) + OffRoute.RETRY_MS }
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
            return FreeNav.parseRoutes(FreeNav.get(FreeNav.routeUrl(origin, d, alternatives, heading))).take(3).ifEmpty { throw ApiException("No route found") }
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
