package com.ahu.tlauncher.obd

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Pure fuel maths, unit tested. Petrol: stoichiometric 14.7:1, ~745 g per litre. */
object FuelMath {
    private const val AFR = 14.7
    private const val DENSITY_G_PER_L = 745.0
    private const val VE = 0.85 // typical volumetric efficiency for the MAP-based estimate

    /** Fuel flow in litres per hour from mass air flow (g/s). */
    fun litresPerHour(mafGs: Double): Double = mafGs * 3600.0 / (AFR * DENSITY_G_PER_L)

    /** Air flow (g/s) estimated from manifold pressure, intake temperature and RPM (speed-density). */
    fun mafFromMap(mapKpa: Int, intakeC: Int, rpm: Int, displacementL: Double): Double {
        val tempK = intakeC + 273.15
        return mapKpa * displacementL * VE * (rpm / 120.0) * 1000.0 / (287.05 * tempK)
    }

    /** Air flow from whatever the car reports, or null. */
    fun airFlow(d: ObdData, displacementL: Double): Double? =
        d.mafGs ?: run {
            val map = d.mapKpa ?: return null
            val rpm = d.rpm ?: return null
            mafFromMap(map, d.intakeC ?: 30, rpm, displacementL)
        }

    fun kmPerLitre(km: Double, litres: Double): Double? = if (km >= 1.0 && litres > 0.05) km / litres else null

    fun rangeKm(fuelPct: Int, tankL: Int, kmPerL: Double): Int = (fuelPct / 100.0 * tankL * kmPerL).toInt()

    const val LOW_FUEL_PCT = 12
}

data class FuelStats(
    val tripKm: Double = 0.0,
    val tripLitres: Double = 0.0,
    /** Longer-term average (roughly the last 500–1000 km), km/L. */
    val avgKmPerL: Double? = null,
    val rangeKm: Int? = null,
) {
    val tripKmPerL: Double? get() = FuelMath.kmPerLitre(tripKm, tripLitres)
}

/** Totals kept between launches so the range works from the first minute of a drive. */
interface FuelStore {
    suspend fun load(): Pair<Double, Double>
    suspend fun save(km: Double, litres: Double)
}

/**
 * Adds up distance (from speed) and fuel (from air flow) while OBD data is live. Trip = since the launcher
 * started or the last "Reset trip"; the long-term average decays so it follows recent driving.
 */
class FuelTracker(
    private val scope: CoroutineScope,
    obd: StateFlow<ObdState>,
    private val tankL: () -> Int,
    private val displacementL: () -> Double,
    private val store: FuelStore,
) {
    private val _stats = MutableStateFlow(FuelStats())
    val stats: StateFlow<FuelStats> = _stats.asStateFlow()
    private var longKm = 0.0
    private var longL = 0.0
    private var lastSavedKm = 0.0
    private var prev: ObdData? = null

    init {
        scope.launch {
            val (km, l) = store.load(); longKm = km; longL = l; lastSavedKm = km
            obd.collect { s -> if (ObdDisplay.show(s.status)) onSample(s.data) else prev = null }
        }
    }

    fun resetTrip() { _stats.value = _stats.value.copy(tripKm = 0.0, tripLitres = 0.0); publish(prev) }

    private fun onSample(d: ObdData) {
        val p = prev
        prev = d
        if (p == null || d.sampleAtMs <= p.sampleAtMs) { publish(d); return }
        val dtH = (d.sampleAtMs - p.sampleAtMs) / 3_600_000.0
        if (dtH > 3.0 / 3600) { publish(d); return } // gap (e.g. reconnect): don't guess
        val km = ((d.speedKmh ?: 0) + (p.speedKmh ?: 0)) / 2.0 * dtH
        val litres = FuelMath.airFlow(d, displacementL())?.let { FuelMath.litresPerHour(it) * dtH } ?: 0.0
        val s = _stats.value
        _stats.value = s.copy(tripKm = s.tripKm + km, tripLitres = s.tripLitres + litres)
        longKm += km; longL += litres
        if (longKm > 1000) { longKm /= 2; longL /= 2; lastSavedKm = longKm }
        if (longKm - lastSavedKm > 1.0) { lastSavedKm = longKm; val k = longKm; val l = longL; scope.launch { store.save(k, l) } }
        publish(d)
    }

    private fun publish(d: ObdData?) {
        val s = _stats.value
        val avg = FuelMath.kmPerLitre(longKm, longL)?.takeIf { longKm >= 20 } ?: s.tripKmPerL?.takeIf { s.tripKm >= 5 }
        val pct = d?.fuelPct ?: prev?.fuelPct
        _stats.value = s.copy(avgKmPerL = avg, rangeKm = if (pct != null && avg != null) FuelMath.rangeKm(pct, tankL(), avg) else null)
    }
}
