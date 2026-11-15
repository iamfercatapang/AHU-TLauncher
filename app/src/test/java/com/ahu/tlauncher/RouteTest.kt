package com.ahu.tlauncher

import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.route.Place
import com.ahu.tlauncher.route.RouteMath
import com.ahu.tlauncher.route.Traffic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    @Test fun parsesGoogleReverseGeocodeForPin() {
        // Geocoding v4 shape; the plus code result is skipped, the pin keeps the pressed spot.
        val v4 = """{"results":[{"formattedAddress":"8HX7+XX Makati","types":["plus_code"]},
            {"formattedAddress":"6750 Ayala Ave, Makati, Metro Manila, Philippines","types":["street_address"],"location":{"latitude":14.55,"longitude":121.02}}]}"""
        val p = RouteMath.parseReverse(v4, 14.5501, 121.0249)!!
        assertEquals("6750 Ayala Ave", p.name); assertEquals("Makati, Metro Manila, Philippines", p.address)
        assertEquals(14.5501, p.lat!!, 1e-9); assertEquals(121.0249, p.lon!!, 1e-9); assertNull(p.placeId)
        // classic shape
        val classic = """{"results":[{"formatted_address":"EDSA, Mandaluyong","types":["route"]}],"status":"OK"}"""
        assertEquals("EDSA", RouteMath.parseReverse(classic, 14.0, 121.0)!!.name)
        assertNull(RouteMath.parseReverse("""{"results":[]}""", 14.0, 121.0))
        assertNull(RouteMath.parseReverse("{}", 14.0, 121.0))
        assertEquals("14.59950, 120.98420", RouteMath.coordText(14.5995, 120.9842))
    }

    @Test fun decodesGooglesExamplePolyline() {
        val p = RouteMath.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, p.size)
        assertEquals(38.5, p[0].lat, 1e-6); assertEquals(-120.2, p[0].lon, 1e-6)
        assertEquals(40.7, p[1].lat, 1e-6); assertEquals(-120.95, p[1].lon, 1e-6)
        assertEquals(43.252, p[2].lat, 1e-6); assertEquals(-126.453, p[2].lon, 1e-6)
    }

    @Test fun parsesRouteWithTraffic() {
        val json = """{"routes":[{"distanceMeters":7400,"duration":"1080s","description":"EDSA",
            "polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
            "travelAdvisory":{"speedReadingIntervals":[{"endPolylinePointIndex":1,"speed":"NORMAL"},{"startPolylinePointIndex":1,"endPolylinePointIndex":2,"speed":"TRAFFIC_JAM"}]}}]}"""
        val r = RouteMath.parseRoute(json)!!
        assertEquals(1080, r.durationS); assertEquals(7400, r.distanceM); assertEquals("EDSA", r.via)
        assertEquals(0, r.traffic[0].from) // missing start = 0 (proto3 JSON)
        assertEquals(Traffic.JAM, r.traffic[1].speed)
        assertNull(RouteMath.parseRoute("""{"routes":[]}"""))
    }

    @Test fun parsesSuggestionsAndPlace() {
        val s = RouteMath.parseSuggestions("""{"suggestions":[{"placePrediction":{"placeId":"abc","text":{"text":"SM Megamall, EDSA"},
            "structuredFormat":{"mainText":{"text":"SM Megamall"},"secondaryText":{"text":"EDSA, Mandaluyong"}},"distanceMeters":7400}},
            {"queryPrediction":{"text":{"text":"sm mega"}}}]}""")
        assertEquals(1, s.size)
        assertEquals("SM Megamall", s[0].title); assertEquals("EDSA, Mandaluyong", s[0].subtitle); assertEquals(7400, s[0].distanceM)
        val p = RouteMath.parsePlace("""{"id":"abc","displayName":{"text":"SM Megamall"},"formattedAddress":"EDSA","location":{"latitude":14.585,"longitude":121.056}}""", "abc")!!
        assertEquals(14.585, p.lat!!, 1e-9); assertEquals("abc", p.placeId)
    }

    @Test fun progressAndRemaining() {
        // a straight 3-point road heading north, ~1.1 km per segment
        val pts = listOf(LatLon(14.0, 121.0), LatLon(14.01, 121.0), LatLon(14.02, 121.0))
        val car = LatLon(14.015, 121.0003) // halfway along segment 1, ~30 m to the side
        val (seg, off) = RouteMath.progress(pts, car, 0)
        assertEquals(1, seg)
        assertEquals(32.0, off, 5.0)
        assertEquals(553.0, RouteMath.remainingM(pts, seg, car), 15.0)
    }

    @Test fun apiErrors() {
        val body = """{"error":{"code":403,"message":"Routes API has not been used in project 123 before or it is disabled.","status":"PERMISSION_DENIED"}}"""
        assertEquals("Routes API isn't enabled on your Google API key yet", RouteMath.apiError(403, body, "Routes API"))
        assertTrue(RouteMath.apiError(500, "", "Routes API").startsWith("Routes API error 500"))
    }

    @Test fun savedPlacesRoundTrip() {
        val list = listOf(Place("SM Megamall", "EDSA", 14.585, 121.056, "abc"), Place("Home", "123 Street"))
        assertEquals(list, RouteMath.decodePlaces(RouteMath.encodePlaces(list)))
        assertEquals(emptyList<Place>(), RouteMath.decodePlaces("garbage"))
    }
}
