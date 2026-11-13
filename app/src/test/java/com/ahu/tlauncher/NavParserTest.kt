package com.ahu.tlauncher

import com.ahu.tlauncher.notify.NavParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavParserTest {
    @Test fun googleMapsTypical() {
        val i = NavParser.parse(NavParser.MAPS, "300 m", "Turn right onto Ayala Ave", "12 min · 4.3 km · 10:42 ETA")!!
        assertEquals("300 m", i.distance)
        assertEquals("Turn right onto Ayala Ave", i.instruction)
        assertEquals("12 min · 4.3 km · 10:42 ETA", i.eta)
        assertEquals("Google Maps", i.appLabel)
    }

    @Test fun googleMapsDistanceInTitleWithToward() {
        val i = NavParser.parse(NavParser.MAPS, "1.2 km", "toward EDSA", null)!!
        assertEquals("1.2 km", i.distance)
        assertEquals("toward EDSA", i.instruction)
        assertNull(i.eta)
    }

    @Test fun wazeInstructionThenDistance() {
        val i = NavParser.parse(NavParser.WAZE, "Turn left", "In 200m, Shaw Blvd", "ETA 10:05")!!
        assertEquals("200 m", i.distance)
        assertEquals("Turn left", i.instruction)
        assertEquals("Shaw Blvd", i.detail)
        assertEquals("ETA 10:05", i.eta)
        assertEquals("Waze", i.appLabel)
    }

    @Test fun distanceLeadingTitle() {
        val i = NavParser.parse(NavParser.WAZE, "500 ft Keep right", "Main St", null)!!
        assertEquals("500 ft", i.distance)
        assertEquals("Keep right", i.instruction)
        assertEquals("Main St", i.detail)
    }

    @Test fun unknownFormatFallsBackToRawText() {
        val i = NavParser.parse(NavParser.MAPS, "Rerouting…", null, null)!!
        assertNull(i.distance)
        assertEquals("Rerouting…", i.instruction)
    }

    @Test fun otherPackagesIgnored() = assertNull(NavParser.parse("com.spotify.music", "300 m", "Song", null))
    @Test fun emptyIgnored() = assertNull(NavParser.parse(NavParser.MAPS, null, null, null))
}
