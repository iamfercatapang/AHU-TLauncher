package com.ahu.tlauncher.route

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** A destination. [placeId] is Google's id when it came from search; Home/Work use [address] only. */
data class Place(val name: String, val address: String, val lat: Double? = null, val lon: Double? = null, val placeId: String? = null) {
    val latLon: LatLon? get() = if (lat != null && lon != null) LatLon(lat, lon) else null
    /** Same place, ignoring how it was found. */
    fun sameAs(o: Place) = (placeId != null && placeId == o.placeId) || (name == o.name && address == o.address)
}

/** A search suggestion. Google ones carry [placeId]; free (OpenStreetMap/Photon) ones carry their position. */
data class Suggestion(val placeId: String, val title: String, val subtitle: String, val distanceM: Int?, val lat: Double? = null, val lon: Double? = null)

enum class Traffic { NORMAL, SLOW, JAM }

/** Traffic for points [from, to] of the polyline. */
data class TrafficSpan(val from: Int, val to: Int, val speed: Traffic)

data class RouteData(
    val points: List<LatLon>,
    val durationS: Int,
    val distanceM: Int,
    /** Main road, e.g. "EDSA". */
    val via: String,
    val traffic: List<TrafficSpan>,
    /** Turn-by-turn steps (free routes only; Google Maps guides Google routes). */
    val steps: List<Step> = emptyList(),
)

/** One manoeuvre of a free route (OSRM). [pointIndex] = where it happens on the polyline. */
data class Step(
    val type: String,
    val modifier: String?,
    val road: String,
    val pointIndex: Int,
    val exit: Int? = null,
)

/** What's next while guiding: the step, and metres to it along the route. */
data class Turn(val step: Step, val distanceM: Int)

/** Pure parsing and geometry, unit tested. */
/**
 * When the car counts as off the route, and how soon a new route is asked for. Pure, unit tested.
 * "Guided" is TLauncher's own turn-by-turn (free map); otherwise Google Maps / Waze guide and reroute
 * themselves, and the dashboard line only has to catch up.
 */
object OffRoute {
    /** Further than this from the line (or [ACCURACY_FACTOR] × the GPS accuracy, up to [MAX_M]) is off route. */
    const val MIN_M = 45.0
    const val MAX_M = 100.0
    const val ACCURACY_FACTOR = 1.5
    /** Driving against the route's direction (a missed exit onto the other carriageway, a wrong turn back). */
    const val WRONG_WAY_DEG = 120.0
    const val WRONG_WAY_MIN_KMH = 15
    const val WRONG_WAY_MIN_SEG_M = 15.0
    /** Off route this long before asking for a new route. */
    fun waitMs(guided: Boolean): Long = if (guided) 3_000 else 8_000
    /** At least this long between new routes. */
    fun gapMs(guided: Boolean): Long = if (guided) 8_000 else 20_000
    /** After a failed request, try again this soon. */
    const val RETRY_MS = 5_000L

    fun limitM(accuracyM: Float?): Double = ((accuracyM ?: 0f) * ACCURACY_FACTOR).coerceIn(MIN_M, MAX_M)

    fun wrongWay(carBearing: Float?, speedKmh: Int, segBearing: Double?, segLengthM: Double): Boolean =
        carBearing != null && segBearing != null && speedKmh >= WRONG_WAY_MIN_KMH && segLengthM >= WRONG_WAY_MIN_SEG_M &&
            RouteMath.angleDiff(carBearing.toDouble(), segBearing) > WRONG_WAY_DEG

    fun isOff(offM: Double, accuracyM: Float?, wrongWay: Boolean): Boolean = wrongWay || offM > limitM(accuracyM)

    /** Time to ask for a new route: off for [waitMs] since [offSince], and [gapMs] since the last request (0 = none). */
    fun due(guided: Boolean, offSince: Long, lastRequest: Long, now: Long): Boolean =
        offSince > 0 && now - offSince >= waitMs(guided) && (lastRequest == 0L || now - lastRequest >= gapMs(guided))
}

object RouteMath {
    /** Google encoded polyline → points. */
    fun decode(encoded: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var i = 0; var lat = 0; var lng = 0
        while (i < encoded.length) {
            for (k in 0..1) {
                var shift = 0; var result = 0; var b: Int
                do { b = encoded[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20 && i < encoded.length)
                val d = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                if (k == 0) lat += d else lng += d
            }
            out += LatLon(lat / 1e5, lng / 1e5)
        }
        return out
    }

    fun distanceM(a: LatLon, b: LatLon): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Distance from [p] to segment a–b (flat-earth approximation, fine for < few km), and the projection fraction. */
    fun toSegment(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val kx = 111_320.0 * cos(Math.toRadians(p.lat)); val ky = 110_540.0
        val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
        val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * ky
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val x = ax + t * dx; val y = ay + t * dy
        return sqrt(x * x + y * y) to t
    }

    /**
     * Where the car is along the route: nearest segment at or after [fromIndex] (searching a window ahead,
     * so a loop in the road doesn't jump back). Returns (segment index, distance off the route in metres).
     */
    fun progress(points: List<LatLon>, p: LatLon, fromIndex: Int): Pair<Int, Double> {
        if (points.size < 2) return 0 to (points.firstOrNull()?.let { distanceM(it, p) } ?: 0.0)
        var best = fromIndex.coerceIn(0, points.size - 2); var bestD = Double.MAX_VALUE
        val end = minOf(points.size - 2, fromIndex + 200)
        for (i in fromIndex.coerceAtLeast(0)..end) {
            val d = toSegment(p, points[i], points[i + 1]).first
            if (d < bestD) { bestD = d; best = i }
        }
        return best to bestD
    }

    /** Compass bearing from [a] to [b], degrees 0–360 (flat-earth, fine for short segments). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val kx = cos(Math.toRadians(a.lat))
        val deg = Math.toDegrees(atan2((b.lon - a.lon) * kx, b.lat - a.lat))
        return (deg + 360) % 360
    }

    /** Smallest angle between two bearings, 0–180. */
    fun angleDiff(a: Double, b: Double): Double { val d = ((a - b) % 360 + 360) % 360; return if (d > 180) 360 - d else d }

    /** Metres left from the car (on segment [seg]) to the end. */
    fun remainingM(points: List<LatLon>, seg: Int, p: LatLon): Double {
        if (points.size < 2) return 0.0
        var d = distanceM(p, points[(seg + 1).coerceAtMost(points.size - 1)])
        for (i in seg + 1 until points.size - 1) d += distanceM(points[i], points[i + 1])
        return d
    }

    /** Search results nearest first (both maps). Ones without a distance keep their order, after the rest. */
    fun nearestFirst(list: List<Suggestion>): List<Suggestion> = list.sortedBy { it.distanceM ?: Int.MAX_VALUE }

    // ---------------------------------------------------------------- Google JSON

    fun parseSuggestions(json: String): List<Suggestion> {
        val arr = JSONObject(json).optJSONArray("suggestions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.getJSONObject(i).optJSONObject("placePrediction") ?: return@mapNotNull null
            val sf = p.optJSONObject("structuredFormat")
            Suggestion(
                placeId = p.optString("placeId").ifEmpty { return@mapNotNull null },
                title = sf?.optJSONObject("mainText")?.optString("text") ?: p.optJSONObject("text")?.optString("text").orEmpty(),
                subtitle = sf?.optJSONObject("secondaryText")?.optString("text").orEmpty(),
                distanceM = if (p.has("distanceMeters")) p.optInt("distanceMeters") else null,
            )
        }
    }

    fun parsePlace(json: String, placeId: String): Place? {
        val o = JSONObject(json)
        val loc = o.optJSONObject("location") ?: return null
        return Place(
            name = o.optJSONObject("displayName")?.optString("text").orEmpty(),
            address = o.optString("formattedAddress"),
            lat = loc.optDouble("latitude"), lon = loc.optDouble("longitude"), placeId = placeId,
        )
    }

    /** "14.59950, 120.98420": a dropped pin before (or without) its address. */
    fun coordText(lat: Double, lon: Double): String = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon)

    /** Stored Home / Work position: "lat,lon". */
    fun encodeLatLon(p: LatLon): String = String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon)

    fun decodeLatLon(s: String?): LatLon? {
        val parts = s?.split(',')?.takeIf { it.size == 2 } ?: return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLon(lat, lon) else null
    }

    /** What a pin saved as Home / Work shows as its address: "Name, rest of address", or the coordinates if it has none. */
    fun savedAddress(pin: Place): String {
        val lat = pin.lat; val lon = pin.lon
        if (lat != null && lon != null && (pin.name.isBlank() || pin.name == DROPPED_PIN)) return coordText(lat, lon)
        return listOf(pin.name, pin.address).filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    /** Name of a pin whose address hasn't been found (yet). */
    const val DROPPED_PIN = "Dropped pin"

    /**
     * Google reverse geocode for a dropped pin: the first street address ("123 Main St" over "Makati, Metro Manila").
     * Reads the Geocoding v4 reply (formattedAddress) and the classic one (formatted_address). Plus codes are skipped.
     * The pin keeps the spot that was pressed, not the address's own position.
     */
    fun parseReverse(json: String, lat: Double, lon: Double): Place? {
        val arr = JSONObject(json).optJSONArray("results") ?: return null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val types = r.optJSONArray("types")?.let { t -> (0 until t.length()).map { t.optString(it) } }.orEmpty()
            if ("plus_code" in types) continue
            val formatted = r.optString("formattedAddress").ifBlank { r.optString("formatted_address") }.trim()
            if (formatted.isBlank()) continue
            val parts = formatted.split(", ")
            return Place(parts.first(), parts.drop(1).joinToString(", "), lat, lon)
        }
        return null
    }

    fun parseRoute(json: String): RouteData? = parseRoutes(json).firstOrNull()

    /** Every route in a Routes API reply: the recommended one first, then alternatives. */
    fun parseRoutes(json: String): List<RouteData> {
        val arr = JSONObject(json).optJSONArray("routes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseGoogleRoute(arr.getJSONObject(it)) }
    }

    private fun parseGoogleRoute(r: JSONObject): RouteData? {
        val points = decode(r.optJSONObject("polyline")?.optString("encodedPolyline") ?: return null)
        if (points.size < 2) return null
        val traffic = r.optJSONObject("travelAdvisory")?.optJSONArray("speedReadingIntervals")?.let { a ->
            (0 until a.length()).map { i ->
                val s = a.getJSONObject(i)
                // proto3 JSON leaves out zero values, so a missing start means point 0.
                TrafficSpan(s.optInt("startPolylinePointIndex", 0), s.optInt("endPolylinePointIndex", points.size - 1),
                    when (s.optString("speed")) { "SLOW" -> Traffic.SLOW; "TRAFFIC_JAM" -> Traffic.JAM; else -> Traffic.NORMAL })
            }
        }.orEmpty()
        return RouteData(
            points = points,
            durationS = r.optString("duration").removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0,
            distanceM = r.optInt("distanceMeters"),
            via = r.optString("description"),
            traffic = traffic,
        )
    }

    /** Readable error from a Google API reply, with a hint when the API isn't enabled on the key. */
    fun apiError(code: Int, body: String, api: String): String {
        val msg = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        return when {
            code == 403 && (msg.contains("not been used", true) || msg.contains("disabled", true) || msg.contains("blocked", true)) ->
                "$api isn't enabled on your Google API key yet"
            code == 403 -> "Google refused the request ($api): check the key's restrictions"
            else -> "$api error $code" + if (msg.isNotEmpty()) ": $msg" else ""
        }
    }

    // ---------------------------------------------------------------- saved places (recents / favourites)

    fun encodePlaces(list: List<Place>): String = JSONArray(list.map { p ->
        JSONObject().put("n", p.name).put("a", p.address).put("id", p.placeId ?: "")
            .apply { p.lat?.let { put("lat", it) }; p.lon?.let { put("lon", it) } }
    }).toString()

    fun decodePlaces(s: String?): List<Place> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Place(o.optString("n"), o.optString("a"), if (o.has("lat")) o.getDouble("lat") else null, if (o.has("lon")) o.getDouble("lon") else null, o.optString("id").ifEmpty { null })
        }
    }.getOrDefault(emptyList())
}
