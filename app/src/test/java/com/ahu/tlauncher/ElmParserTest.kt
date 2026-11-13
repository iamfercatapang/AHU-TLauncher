package com.ahu.tlauncher

import com.ahu.tlauncher.obd.ElmParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElmParserTest {
    @Test fun speed() = assertEquals(60, ElmParser.speedKmh("41 0D 3C\r\r>"))
    @Test fun speedNoSpaces() = assertEquals(60, ElmParser.speedKmh("410D3C>"))
    @Test fun speedAfterSearching() = assertEquals(0, ElmParser.speedKmh("SEARCHING...\r41 0D 00\r>"))
    @Test fun rpm() = assertEquals(1726, ElmParser.rpm("41 0C 1A F8"))
    @Test fun coolant() = assertEquals(85, ElmParser.coolantC("41 05 7D"))
    @Test fun coolantNegative() = assertEquals(-40, ElmParser.coolantC("41 05 00"))
    @Test fun noData() = assertNull(ElmParser.speedKmh("NO DATA\r>"))
    @Test fun unableToConnect() = assertNull(ElmParser.rpm("UNABLE TO CONNECT"))
    @Test fun wrongPid() = assertNull(ElmParser.speedKmh("41 0C 1A F8"))
    @Test fun atrv() = assertEquals(12.6, ElmParser.atrv("12.6V")!!, 0.001)
    @Test fun atrvSpaced() = assertEquals(14.1, ElmParser.atrv("14.1 V\r>")!!, 0.001)
    @Test fun moduleVoltage() = assertEquals(13.9, ElmParser.moduleVoltage("41 42 36 4C")!!, 0.01)
    @Test fun multipleEcus() = assertEquals(55, ElmParser.speedKmh("41 0D 37\r41 0D 37\r>"))
    @Test fun extraPids() {
        assertEquals(50, ElmParser.loadPct("41 04 80"))
        assertEquals(100, ElmParser.throttlePct("41 11 FF"))
        assertEquals(34, ElmParser.intakeC("41 0F 4A"))
        assertEquals(6.4, ElmParser.mafGs("41 10 02 80")!!, 0.001)
        assertEquals(21.5, ElmParser.timingDeg("41 0E AB")!!, 0.001)
        assertEquals(0.0, ElmParser.fuelTrimPct("41 06 80", "06")!!, 0.001)
        assertEquals(-12.5, ElmParser.fuelTrimPct("41 07 70", "07")!!, 0.001)
        assertEquals(true, ElmParser.noData("NO DATA\r>"))
    }
}
