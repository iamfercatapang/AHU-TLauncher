package com.ahu.tlauncher

import com.ahu.tlauncher.obd.ElmParser
import com.ahu.tlauncher.obd.FuelMath
import com.ahu.tlauncher.obd.FuelStore
import com.ahu.tlauncher.obd.FuelTracker
import com.ahu.tlauncher.obd.ObdData
import com.ahu.tlauncher.obd.ObdState
import com.ahu.tlauncher.obd.ObdStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelTest {
    @Test fun parsesLevelAndMap() {
        assertEquals(50, ElmParser.fuelLevelPct("41 2F 80"))
        assertEquals(100, ElmParser.fuelLevelPct("41 2F FF"))
        assertEquals(35, ElmParser.mapKpa("41 0B 23"))
    }

    @Test fun litresPerHour() {
        // 2.7 g/s of air is about a light cruise: ~0.89 L/h
        assertEquals(0.888, FuelMath.litresPerHour(2.7), 0.01)
    }

    @Test fun speedDensityIsPlausible() {
        // 1.5 L engine, wide open at 3000 rpm, 27 °C: roughly 35–40 g/s of air
        assertEquals(37.0, FuelMath.mafFromMap(100, 27, 3000, 1.5), 1.5)
        assertEquals(2.0, FuelMath.airFlow(ObdData(mafGs = 2.0), 1.5)!!, 0.0)
        assertNull(FuelMath.airFlow(ObdData(), 1.5))
    }

    @Test fun rangeAndEconomy() {
        assertEquals(12.5, FuelMath.kmPerLitre(25.0, 2.0)!!, 0.001)
        assertNull(FuelMath.kmPerLitre(0.5, 0.1))
        assertEquals(322, FuelMath.rangeKm(60, 43, 12.5))
    }

    @Test fun trackerIntegratesDistanceAndFuel() {
        val obd = MutableStateFlow(ObdState())
        var saved: Pair<Double, Double>? = null
        val store = object : FuelStore {
            override suspend fun load() = 0.0 to 0.0
            override suspend fun save(km: Double, litres: Double) { saved = km to litres }
        }
        val t = FuelTracker(CoroutineScope(Dispatchers.Unconfined), obd, { 43 }, { 1.5 }, store)
        // 60 km/h and 3.0 g/s for 10 minutes, sampled every second
        var ms = 1_000_000L
        repeat(601) {
            obd.value = ObdState(ObdStatus.CONNECTED, ObdData(speedKmh = 60, rpm = 2000, mafGs = 3.0, fuelPct = 50, sampleAtMs = ms))
            ms += 1000
        }
        val s = t.stats.value
        assertEquals(10.0, s.tripKm, 0.01)
        assertEquals(FuelMath.litresPerHour(3.0) / 6, s.tripLitres, 0.001)
        // 10 km of trip data is enough for the range (long-term average needs 20 km)
        assertEquals((0.5 * 43 * s.tripKmPerL!!).toInt(), s.rangeKm)
        assertEquals(10.0, saved!!.first, 1.01)
        t.resetTrip()
        assertEquals(0.0, t.stats.value.tripKm, 0.0)
    }

    @Test fun trackerSkipsGaps() {
        val obd = MutableStateFlow(ObdState())
        val store = object : FuelStore { override suspend fun load() = 0.0 to 0.0; override suspend fun save(km: Double, litres: Double) {} }
        val t = FuelTracker(CoroutineScope(Dispatchers.Unconfined), obd, { 43 }, { 1.5 }, store)
        obd.value = ObdState(ObdStatus.CONNECTED, ObdData(speedKmh = 60, mafGs = 3.0, sampleAtMs = 1_000))
        obd.value = ObdState(ObdStatus.CONNECTED, ObdData(speedKmh = 60, mafGs = 3.0, sampleAtMs = 600_000))
        assertEquals(0.0, t.stats.value.tripKm, 0.0)
    }
}
