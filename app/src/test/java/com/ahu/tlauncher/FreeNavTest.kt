package com.ahu.tlauncher

import com.ahu.tlauncher.route.FreeNav
import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.route.RouteData
import com.ahu.tlauncher.route.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeNavTest {
    @Test fun parsesPhotonReverseForPin() {
        val json = """{"features":[{"geometry":{"coordinates":[121.0001,14.5002]},"properties":{"osm_type":"N","osm_id":9,"street":"Ayala Avenue","housenumber":"6750","city":"Makati"}}]}"""
        val p = FreeNav.parseReverse(json, 14.5, 121.0)!!
        assertEquals("Ayala Avenue 6750", p.name); assertEquals("Makati", p.address)
        assertEquals(14.5, p.lat!!, 1e-9); assertEquals(121.0, p.lon!!, 1e-9)
        assertNull(p.placeId) // routed to the exact spot, not looked up again
        assertNull(FreeNav.parseReverse("""{"features":[]}""", 14.5, 121.0))
    }

    @Test fun parsesPhotonResults() {
        val json = """{"features":[{"geometry":{"coordinates":[121.056,14.585]},"properties":{"osm_type":"W","osm_id":123,"name":"SM Megamall","street":"EDSA","city":"Mandaluyong"}},
            {"geometry":{"coordinates":[121.0,14.5]},"properties":{"osm_type":"N","osm_id":9,"street":"Ayala Avenue","housenumber":"6750","city":"Makati"}}]}"""
        val s = FreeNav.parseSearch(json, LatLon(14.585, 121.0))
        assertEquals(2, s.size)
        assertEquals("SM Megamall", s[0].title); assertEquals("EDSA, Mandaluyong", s[0].subtitle)
        assertEquals(14.585, s[0].lat!!, 1e-9); assertEquals("osm:W123", s[0].placeId)
        assertEquals("Ayala Avenue 6750", s[1].title) // no name: street + number
        assertTrue(s[0].distanceM!! in 5900..6100)
    }

    @Test fun parsesOsrmRouteWithSteps() {
        val json = """{"code":"Ok","routes":[{"distance":7400.5,"duration":1080.2,"geometry":"_p~iF~ps|U_ulLnnqC_mqNvxq`@",
            "legs":[{"steps":[
              {"name":"Mabini St","distance":100,"maneuver":{"type":"depart","location":[-120.2,38.5]}},
              {"name":"EDSA","distance":7000,"maneuver":{"type":"turn","modifier":"right","location":[-120.95,40.7]}},
              {"name":"","distance":0,"maneuver":{"type":"arrive","location":[-126.453,43.252]}}]}]}]}"""
        val r = FreeNav.parseRoute(json)!!
        assertEquals(1080, r.durationS); assertEquals(7400, r.distanceM); assertEquals("EDSA", r.via)
        assertEquals(listOf(0, 1, 2), r.steps.map { it.pointIndex })
        assertNull(FreeNav.parseRoute("""{"code":"NoRoute"}"""))
    }

    @Test fun instructions() {
        assertEquals("Turn right onto EDSA", FreeNav.instruction(Step("turn", "right", "EDSA", 0)))
        assertEquals("Turn slightly left", FreeNav.instruction(Step("turn", "slight left", "", 0)))
        assertEquals("At the roundabout, take the second exit onto Ortigas Ave", FreeNav.instruction(Step("roundabout", "right", "Ortigas Ave", 0, exit = 2)))
        assertEquals("Keep left onto SLEX", FreeNav.instruction(Step("fork", "slight left", "SLEX", 0)))
        assertEquals("Arrive at your destination", FreeNav.instruction(Step("arrive", null, "", 0)))
        assertEquals("Make a U-turn", FreeNav.instruction(Step("turn", "uturn", "", 0)))
    }

    @Test fun announcementTiming() {
        assertEquals(1, FreeNav.dueStage(700, 40, 0))
        assertEquals(0, FreeNav.dueStage(1200, 40, 0)) // too far yet in town
        assertEquals(1, FreeNav.dueStage(1200, 90, 0)) // but not on a fast road
        assertEquals(2, FreeNav.dueStage(200, 40, 1))
        assertEquals(0, FreeNav.dueStage(200, 40, 2)) // already said
        assertEquals(3, FreeNav.dueStage(30, 40, 2))
        assertEquals("300 metres", FreeNav.spokenDistance(320, false))
        assertEquals("1.5 kilometres", FreeNav.spokenDistance(1500, false))
    }

    @Test fun nextTurnDistance() {
        val pts = listOf(LatLon(14.0, 121.0), LatLon(14.01, 121.0), LatLon(14.02, 121.0), LatLon(14.02, 121.01))
        val r = RouteData(pts, 600, 3300, "", emptyList(), listOf(Step("depart", null, "", 0), Step("turn", "right", "EDSA", 2), Step("arrive", null, "", 3)))
        val t = FreeNav.nextTurn(r, 0, LatLon(14.005, 121.0))!!
        assertEquals("EDSA", t.step.road)
        assertEquals(1660.0, t.distanceM.toDouble(), 20.0)
        assertEquals("arrive", FreeNav.nextTurn(r, 2, LatLon(14.02, 121.005))!!.step.type)
    }
}
