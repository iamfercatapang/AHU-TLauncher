package com.ahu.tlauncher

import com.ahu.tlauncher.data.savedPlace
import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.route.Place
import com.ahu.tlauncher.route.RouteMath
import com.ahu.tlauncher.route.Traffic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    @Test fun storesHomeAndWorkPositions() {
        val p = LatLon(14.5501234, 121.0249876)
        val back = RouteMath.decodeLatLon(RouteMath.encodeLatLon(p))!!
        assertEquals(p.lat, back.lat, 1e-6); assertEquals(p.lon, back.lon, 1e-6)
        assertNull(RouteMath.decodeLatLon(null)); assertNull(RouteMath.decodeLatLon(""))
        assertNull(RouteMath.decodeLatLon("14.5")); assertNull(RouteMath.decodeLatLon("a,b")); assertNull(RouteMath.decodeLatLon("95,121"))
    }

    @Test fun pinSavedAsHomeShowsItsAddressOrCoordinates() {
        assertEquals("6750 Ayala Ave, Makati, Metro Manila", RouteMath.savedAddress(Place("6750 Ayala Ave", "Makati, Metro Manila", 14.55, 121.02)))
        // Address not found (yet): the coordinates.
        assertEquals("14.55000, 121.02000", RouteMath.savedAddress(Place(RouteMath.DROPPED_PIN, "14.55000, 121.02000", 14.55, 121.02)))
        assertEquals("EDSA", RouteMath.savedAddress(Place("EDSA", "", 14.0, 121.0)))
    }

    @Test fun homeGoesToThePinnedSpotWhenThereIsOne() {
        val typed = com.ahu.tlauncher.data.Settings(homeAddress = "Makati")
        assertNull(typed.savedPlace(com.ahu.tlauncher.data.SavedSpot.HOME)!!.latLon)
        assertEquals("Makati", typed.savedPlace(com.ahu.tlauncher.data.SavedSpot.HOME)!!.address)
        assertNull(typed.savedPlace(com.ahu.tlauncher.data.SavedSpot.WORK))
        val pinned = com.ahu.tlauncher.data.Settings(workAddress = "Office", workPos = LatLon(14.6, 121.1))
        val w = pinned.savedPlace(com.ahu.tlauncher.data.SavedSpot.WORK)!!
        assertEquals("Work", w.name); assertEquals("Office", w.address); assertEquals(LatLon(14.6, 121.1), w.latLon)
    }

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

    @Test fun searchResultsNearestFirst() {
        fun s(id: String, d: Int?) = com.ahu.tlauncher.route.Suggestion(id, id, "", d)
        val sorted = RouteMath.nearestFirst(listOf(s("far", 331_800), s("none", null), s("near", 24_600), s("mid", 130_100), s("none2", null)))
        assertEquals(listOf("near", "mid", "far", "none", "none2"), sorted.map { it.placeId })
    }

    @Test fun shortDistancesForSearchRows() {
        assertEquals("332 km", com.ahu.tlauncher.ui.home.fmtDistanceShort(331_800, mph = false))
        assertEquals("25 km", com.ahu.tlauncher.ui.home.fmtDistanceShort(24_600, mph = false)) // no decimal from 10 km up
        assertEquals("9.5 km", com.ahu.tlauncher.ui.home.fmtDistanceShort(9_500, mph = false).replace(',', '.')) // decimal below 10 km
        assertEquals("850 m", com.ahu.tlauncher.ui.home.fmtDistanceShort(850, mph = false))
        assertEquals("21 mi", com.ahu.tlauncher.ui.home.fmtDistanceShort(33_800, mph = true))
    }

    @Test fun parsesAlternativeRoutes() {
        val json = """{"routes":[
            {"distanceMeters":7400,"duration":"1080s","description":"EDSA","polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}},
            {"distanceMeters":9100,"duration":"1260s","description":"C-5","polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}},
            {"distanceMeters":1,"duration":"1s","polyline":{"encodedPolyline":""}}]}"""
        val all = RouteMath.parseRoutes(json)
        assertEquals(listOf("EDSA", "C-5"), all.map { it.via }) // a route without a line is skipped
        assertEquals(1260, all[1].durationS)
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

    @Test fun bearings() {
        assertEquals(0.0, RouteMath.bearingDeg(LatLon(14.0, 121.0), LatLon(14.01, 121.0)), 0.5)
        assertEquals(90.0, RouteMath.bearingDeg(LatLon(14.0, 121.0), LatLon(14.0, 121.01)), 0.5)
        assertEquals(180.0, RouteMath.bearingDeg(LatLon(14.01, 121.0), LatLon(14.0, 121.0)), 0.5)
        assertEquals(20.0, RouteMath.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(180.0, RouteMath.angleDiff(90.0, 270.0), 1e-9)
    }

    @Test fun offRoute() {
        val ro = com.ahu.tlauncher.route.OffRoute
        assertTrue(!ro.isOff(30.0, 5f, wrongWay = false))
        assertTrue(ro.isOff(50.0, 5f, wrongWay = false))
        // poor GPS: allow more before calling it off route, but never more than 100 m
        assertTrue(!ro.isOff(50.0, 40f, wrongWay = false))
        assertTrue(ro.isOff(101.0, 200f, wrongWay = false))
        // on the line but driving against it
        assertTrue(ro.isOff(5.0, 5f, wrongWay = true))
        assertTrue(ro.wrongWay(180f, 40, 0.0, 100.0))
        assertTrue(!ro.wrongWay(20f, 40, 0.0, 100.0))
        assertTrue(!ro.wrongWay(180f, 5, 0.0, 100.0)) // crawling: GPS course is noise
        assertTrue(!ro.wrongWay(null, 40, 0.0, 100.0))
    }

    @Test fun rerouteTiming() {
        val ro = com.ahu.tlauncher.route.OffRoute
        assertTrue(!ro.due(guided = true, offSince = 0, lastRequest = 0, now = 10_000)) // not off
        assertTrue(!ro.due(guided = true, offSince = 10_000, lastRequest = 0, now = 12_000))
        assertTrue(ro.due(guided = true, offSince = 10_000, lastRequest = 0, now = 13_000)) // right after starting too
        assertTrue(!ro.due(guided = true, offSince = 10_000, lastRequest = 9_000, now = 13_000)) // just asked
        assertTrue(!ro.due(guided = false, offSince = 10_000, lastRequest = 0, now = 13_000)) // Google Maps reroutes itself first
        assertTrue(ro.due(guided = false, offSince = 10_000, lastRequest = 0, now = 18_000))
    }
}
