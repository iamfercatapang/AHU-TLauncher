package com.ahu.tlauncher

import com.ahu.tlauncher.obd.Dtc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcTest {
    @Test fun decodesLetters() {
        assertEquals("P0171", Dtc.decode(0x01, 0x71))
        assertEquals("P1349", Dtc.decode(0x13, 0x49))
        assertEquals("C0035", Dtc.decode(0x40, 0x35))
        assertEquals("B1234", Dtc.decode(0x92, 0x34))
        assertEquals("U0100", Dtc.decode(0xC1, 0x00))
    }

    @Test fun canSingleFrame() {
        assertEquals(listOf("P0171", "P0300"), Dtc.parse("43 02 01 71 03 00\r\r>", 0x03))
    }

    @Test fun canNoCodes() {
        assertEquals(emptyList<String>(), Dtc.parse("43 00", 0x03))
    }

    @Test fun canMultiFrame() {
        val raw = "00C\r0: 43 05 01 71 03 00\r1: 04 20 01 33 04 55 00\r"
        assertEquals(listOf("P0171", "P0300", "P0420", "P0133", "P0455"), Dtc.parse(raw, 0x03))
    }

    @Test fun kLinePadded() {
        assertEquals(listOf("P0171", "P0420"), Dtc.parse("43 01 71 04 20 00 00", 0x03))
        assertEquals(emptyList<String>(), Dtc.parse("43 00 00 00 00 00 00", 0x03))
    }

    @Test fun twoEcus() {
        assertEquals(listOf("P0171", "P0700"), Dtc.parse("43 01 01 71\r43 01 07 00", 0x03))
    }

    @Test fun pendingAndNoSpaces() {
        assertEquals(listOf("P0420"), Dtc.parse("SEARCHING...\r47010420", 0x07))
    }

    @Test fun noDataMeansNone() { assertEquals(emptyList<String>(), Dtc.parse("NO DATA", 0x03)) }
    @Test fun garbageIsNull() { assertNull(Dtc.parse("UNABLE TO CONNECT", 0x03)) }

    @Test fun clearAndMil() {
        assertTrue(Dtc.cleared("44\r>"))
        assertFalse(Dtc.cleared("NO DATA"))
        assertEquals(true, Dtc.milOn("41 01 81 07 E5 00"))
        assertEquals(false, Dtc.milOn("41 01 00 07 E5 00"))
    }

    @Test fun descriptions() {
        assertEquals("System too lean (Bank 1)", Dtc.describe("P0171"))
        assertTrue(Dtc.describe("P1349").startsWith("Manufacturer-specific"))
        assertEquals("Ignition system or misfire", Dtc.describe("P0399"))
        assertTrue(Dtc.describe("C1234").startsWith("Chassis"))
    }
}
