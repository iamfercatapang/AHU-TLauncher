package com.ahu.tlauncher.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Free search and routing for the OpenFreeMap map: Photon (komoot) for search-as-you-type and the
 * OpenStreetMap routing servers run by FOSSGIS (OSRM) for routes with turn-by-turn steps.
 * Both are fair-use public services: one request at a time, identified as TLauncher.
 */
object FreeNav {
    private const val UA = "TLauncher/1.0 (Android head-unit launcher)"

    fun searchUrl(q: String, near: LatLon?): String {
        val bias = near?.let { String.format(Locale.US, "&lat=%.4f&lon=%.4f", it.lat, it.lon) } ?: ""
        return "https://photon.komoot.io/api/?q=" + URLEncoder.encode(q, "UTF-8") + "&limit=6&lang=en" + bias
    }

    fun reverseUrl(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f&lang=en", lat, lon)

    /** Photon reverse geocode for a dropped pin. The pin keeps the spot that was pressed. */
    fun parseReverse(json: String, lat: Double, lon: Double): Place? =
        parseSearch(json, null).firstOrNull()?.let { Place(it.title, it.subtitle, lat, lon) }

    /** With [alternatives], OSRM adds up to two other routes after the recommended one. */
    /** [heading]: the car's course; the route then leaves the start that way (OSRM bearings, ±60°). */
    fun routeUrl(from: LatLon, to: LatLon, alternatives: Boolean = false, heading: Float? = null): String = String.format(
        Locale.US, "https://routing.openstreetmap.de/routed-car/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline&steps=true",
        from.lon, from.lat, to.lon, to.lat,
    ) + (heading?.let { String.format(Locale.US, "&bearings=%d,60;", ((it.toInt() % 360) + 360) % 360) } ?: "") +
        if (alternatives) "&alternatives=2" else ""

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", UA)
            val code = c.responseCode
            if (code !in 200..299) throw ApiException(if (code == 429) "The free map service is busy, try again in a moment" else "Free map service error $code")
            c.inputStream.bufferedReader().readText()
        } catch (e: java.io.IOException) {
            throw ApiException("No internet connection")
        } finally { c.disconnect() }
    }

    // ---------------------------------------------------------------- parsing (pure, unit tested)

    fun parseSearch(json: String, near: LatLon?): List<Suggestion> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val lon = c.getDouble(0); val lat = c.getDouble(1)
            val p = f.optJSONObject("properties") ?: JSONObject()
            val street = listOf(p.optString("street"), p.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
            val title = p.optString("name").ifBlank { street }.ifBlank { return@mapNotNull null }
            val sub = listOf(street.takeIf { it != title }, p.optString("district"), p.optString("city"), p.optString("state"))
                .filter { !it.isNullOrBlank() }.distinct().joinToString(", ")
            Suggestion(
                placeId = "osm:" + p.optString("osm_type") + p.optLong("osm_id"), title = title, subtitle = sub,
                distanceM = near?.let { RouteMath.distanceM(it, LatLon(lat, lon)).toInt() }, lat = lat, lon = lon,
            )
        }
    }

    fun parseRoute(json: String): RouteData? = parseRoutes(json).firstOrNull()

    /** Every route in an OSRM reply: the recommended one first, then alternatives. */
    fun parseRoutes(json: String): List<RouteData> {
        val o = JSONObject(json)
        if (o.optString("code") != "Ok") return emptyList()
        val arr = o.optJSONArray("routes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseOsrmRoute(arr.getJSONObject(it)) }
    }

    private fun parseOsrmRoute(r: JSONObject): RouteData? {
        val points = RouteMath.decode(r.optString("geometry"))
        if (points.size < 2) return null
        val steps = ArrayList<Step>()
        var from = 0
        val legs = r.optJSONArray("legs")
        for (l in 0 until (legs?.length() ?: 0)) {
            val ss = legs!!.getJSONObject(l).optJSONArray("steps") ?: continue
            for (i in 0 until ss.length()) {
                val st = ss.getJSONObject(i)
                val m = st.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                val at = LatLon(loc.getDouble(1), loc.getDouble(0))
                val idx = nearestIndex(points, at, from)
                from = idx
                val road = st.optString("name").ifBlank { st.optString("ref") }
                steps += Step(m.optString("type"), m.optString("modifier").ifBlank { null }, road, idx, if (m.has("exit")) m.optInt("exit") else null)
            }
        }
        // the main road: the step whose stretch is longest
        val via = (0 until (legs?.length() ?: 0)).flatMap { l ->
            val ss = legs!!.getJSONObject(l).optJSONArray("steps") ?: return@flatMap emptyList()
            (0 until ss.length()).map { ss.getJSONObject(it).let { s -> s.optString("name") to s.optDouble("distance") } }
        }.filter { it.first.isNotBlank() }.maxByOrNull { it.second }?.first.orEmpty()
        return RouteData(points, r.optDouble("duration").toInt(), r.optDouble("distance").toInt(), via, emptyList(), steps)
    }

    private fun nearestIndex(points: List<LatLon>, p: LatLon, from: Int): Int {
        var best = from; var bestD = Double.MAX_VALUE
        for (i in from until minOf(points.size, from + 400)) {
            val d = RouteMath.distanceM(points[i], p)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    // ---------------------------------------------------------------- guidance

    /** The next manoeuvre after the car's position ([seg] = segment the car is on), with metres to it. */
    fun nextTurn(r: RouteData, seg: Int, car: LatLon): Turn? {
        val step = r.steps.firstOrNull { it.pointIndex > seg && it.type != "depart" } ?: return null
        var d = RouteMath.distanceM(car, r.points[(seg + 1).coerceAtMost(r.points.size - 1)])
        for (i in seg + 1 until step.pointIndex) d += RouteMath.distanceM(r.points[i], r.points[i + 1])
        return Turn(step, d.toInt())
    }

    /** Short spoken/shown instruction, e.g. "Turn right onto EDSA". */
    fun instruction(s: Step): String {
        val onto = if (s.road.isNotBlank()) " onto ${s.road}" else ""
        val dir = when (s.modifier) {
            "left" -> "left"; "right" -> "right"; "slight left" -> "slightly left"; "slight right" -> "slightly right"
            "sharp left" -> "sharp left"; "sharp right" -> "sharp right"; "uturn" -> "around"; else -> null
        }
        return when (s.type) {
            "arrive" -> "Arrive at your destination"
            "roundabout", "rotary", "roundabout turn" -> "At the roundabout, take the ${ordinal(s.exit ?: 1)} exit" + (if (s.road.isNotBlank()) " onto ${s.road}" else "")
            "merge" -> "Merge" + (if (dir != null) " $dir" else "") + onto
            "on ramp" -> "Take the ramp" + (if (dir != null) " on the $dir" else "") + onto
            "off ramp" -> "Take the exit" + (if (dir != null) " on the $dir" else "") + onto
            "fork" -> "Keep " + (if (s.modifier?.contains("left") == true) "left" else "right") + onto
            "end of road" -> "At the end of the road, turn ${dir ?: "ahead"}$onto"
            "continue", "new name" -> if (s.modifier == "uturn") "Make a U-turn$onto" else "Continue$onto"
            else -> when (s.modifier) {
                null, "straight" -> "Continue straight$onto"
                "uturn" -> "Make a U-turn$onto"
                else -> "Turn $dir$onto"
            }
        }
    }

    private fun ordinal(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; else -> "${n}th" }

    fun spokenDistance(m: Int, mph: Boolean): String = when {
        mph -> if (m < 300) "${(m * 3.281 / 50).toInt() * 50} feet" else "%.1f miles".format(Locale.US, m / 1609.34).replace(".0 ", " ")
        m >= 1000 -> "%.1f kilometres".format(Locale.US, m / 1000.0).replace(".0 ", " ")
        else -> "${(m / 50) * 50} metres"
    }

    /**
     * When to speak: once far ahead (~800 m, or 1.5 km on fast roads), once at ~200 m, and "now" (~40 m).
     * Returns the announcement stage that's due (1, 2, 3) or 0, given the stage already spoken for this step.
     */
    fun dueStage(distanceM: Int, speedKmh: Int, spoken: Int): Int {
        val far = if (speedKmh >= 70) 1500 else 800
        return when {
            spoken < 3 && distanceM <= 40 -> 3
            spoken < 2 && distanceM in 41..220 -> 2
            spoken < 1 && distanceM in 221..far -> 1
            else -> 0
        }
    }
}
