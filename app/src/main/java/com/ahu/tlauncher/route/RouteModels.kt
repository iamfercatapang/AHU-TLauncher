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

data class Suggestion(val placeId: String, val title: String, val subtitle: String, val distanceM: Int?)

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
)

/** Pure parsing and geometry, unit tested. */
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

    /** Metres left from the car (on segment [seg]) to the end. */
    fun remainingM(points: List<LatLon>, seg: Int, p: LatLon): Double {
        if (points.size < 2) return 0.0
        var d = distanceM(p, points[(seg + 1).coerceAtMost(points.size - 1)])
        for (i in seg + 1 until points.size - 1) d += distanceM(points[i], points[i + 1])
        return d
    }

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

    fun parseRoute(json: String): RouteData? {
        val r = JSONObject(json).optJSONArray("routes")?.optJSONObject(0) ?: return null
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
