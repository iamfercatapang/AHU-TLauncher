package com.ahu.tlauncher.system

import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.route.RouteMath
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------- trip log

/** One drive: from the first movement to the last, distance from GPS, fuel from OBD when connected. */
data class Trip(val startMs: Long, val endMs: Long, val km: Double, val litres: Double?) {
    val minutes: Int get() = ((endMs - startMs) / 60_000).toInt()
    val kmPerL: Double? get() = litres?.let { com.ahu.tlauncher.obd.FuelMath.kmPerLitre(km, it) }
}

/** [litres] and [fuelKm] cover only the trips with fuel data (OBD connected), so km/L isn't diluted by GPS-only trips. */
data class TripTotals(val count: Int, val km: Double, val minutes: Int, val litres: Double?, val fuelKm: Double = 0.0) {
    val kmPerL: Double? get() = litres?.let { com.ahu.tlauncher.obd.FuelMath.kmPerLitre(fuelKm, it) }
}

/** Pure trip log maths, unit tested. */
object TripMath {
    /** Moving faster than this (km/h) starts a trip and keeps it going. */
    const val MOVING_KMH = 8
    /** Stopped this long ends the trip (a long red light doesn't). */
    const val STOP_MS = 4 * 60_000L
    /** Shorter drives (moving the car in the driveway) aren't kept. */
    const val MIN_KM = 0.3
    const val MAX_TRIPS = 300
    /** GPS points less accurate than this, or closer than [MIN_STEP_M], don't add distance (jitter). */
    const val MAX_ACCURACY_M = 35f
    const val MIN_STEP_M = 4.0

    /** Distance to add between two GPS points (km), or 0 for jitter and jumps. */
    fun stepKm(from: LatLon, to: LatLon, accuracyM: Float?, dtMs: Long): Double {
        if (accuracyM != null && accuracyM > MAX_ACCURACY_M) return 0.0
        val m = RouteMath.distanceM(from, to)
        if (m < MIN_STEP_M) return 0.0
        // Faster than 250 km/h between two points is a GPS jump.
        if (dtMs > 0 && m / (dtMs / 1000.0) > 70) return 0.0
        return m / 1000.0
    }

    fun totals(trips: List<Trip>, fromMs: Long, toMs: Long = Long.MAX_VALUE): TripTotals {
        val t = trips.filter { it.startMs in fromMs until toMs }
        val withFuel = t.filter { it.litres != null }
        val litres = if (withFuel.isEmpty()) null else withFuel.sumOf { it.litres!! }
        return TripTotals(t.size, t.sumOf { it.km }, t.sumOf { it.minutes }, litres, withFuel.sumOf { it.km })
    }

    /** Average km/L over the trips that have fuel data. */
    fun fuelEconomy(trips: List<Trip>): Double? {
        val f = trips.filter { it.litres != null }
        return com.ahu.tlauncher.obd.FuelMath.kmPerLitre(f.sumOf { it.km }, f.sumOf { it.litres!! })
    }

    fun weekStartMs(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthStartMs(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun encode(trips: List<Trip>): String = JSONArray().apply {
        trips.takeLast(MAX_TRIPS).forEach { t ->
            put(JSONObject().put("s", t.startMs).put("e", t.endMs).put("km", t.km).apply { t.litres?.let { put("l", it) } })
        }
    }.toString()

    fun decode(json: String?): List<Trip> = runCatching {
        val a = JSONArray(json ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Trip(o.getLong("s"), o.getLong("e"), o.getDouble("km"), if (o.has("l")) o.getDouble("l") else null)
        }
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------------------------------------- maintenance

/** Service items with their usual intervals (km, and months where time matters too). */
enum class ServiceItem(val label: String, val months: Int, val defaultKm: Int, val choicesKm: List<Int>) {
    OIL("Oil change", 6, 5_000, listOf(5_000, 7_500, 10_000)),
    ROTATION("Tyre rotation", 0, 10_000, listOf(5_000, 10_000)),
    PMS("PMS (periodic maintenance)", 12, 10_000, listOf(5_000, 10_000, 20_000)),
}

/** When an item was last done: at [doneKm] on the odometer, on [doneDay] (epoch day); due again after [intervalKm]. */
data class ServiceRecord(val doneKm: Int, val doneDay: Long, val intervalKm: Int)

/**
 * The odometer is read once from the dashboard ([odoKm] when [odoAtLoggedKm] km had been logged); after that it
 * counts up with the trip log.
 */
data class Maintenance(
    val odoKm: Int? = null,
    val odoAtLoggedKm: Double = 0.0,
    val records: Map<ServiceItem, ServiceRecord> = emptyMap(),
)

/** How soon an item is due: km and days left (negative = overdue), null where unknown or not by time. */
data class ServiceDue(val item: ServiceItem, val kmLeft: Int?, val daysLeft: Long?) {
    val overdue: Boolean get() = (kmLeft ?: 1) <= 0 || (daysLeft ?: 1) <= 0
    val soon: Boolean get() = overdue || (kmLeft ?: Int.MAX_VALUE) <= MaintenanceMath.SOON_KM || (daysLeft ?: Long.MAX_VALUE) <= MaintenanceMath.SOON_DAYS
}

/** Pure maintenance maths, unit tested. */
object MaintenanceMath {
    const val SOON_KM = 500
    const val SOON_DAYS = 14L

    fun odometer(m: Maintenance, loggedKm: Double): Int? =
        m.odoKm?.let { it + (loggedKm - m.odoAtLoggedKm).coerceAtLeast(0.0).roundToInt() }

    fun due(item: ServiceItem, rec: ServiceRecord, odo: Int?, today: LocalDate): ServiceDue {
        val km = odo?.let { rec.doneKm + rec.intervalKm - it }
        val days = if (item.months > 0) ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(rec.doneDay).plusMonths(item.months.toLong())) else null
        return ServiceDue(item, km, days)
    }

    /** Every tracked item, soonest first. */
    fun all(m: Maintenance, loggedKm: Double, today: LocalDate): List<ServiceDue> {
        val odo = odometer(m, loggedKm)
        return m.records.map { (item, rec) -> due(item, rec, odo, today) }
            .sortedWith(compareBy<ServiceDue>({ !it.overdue }, { !it.soon }, { it.kmLeft ?: Int.MAX_VALUE }, { it.daysLeft ?: Long.MAX_VALUE }))
    }

    /** "due in 1,200 km or 3 months" / "overdue by 150 km". */
    fun text(d: ServiceDue): String {
        fun km(v: Int) = "%,d km".format(java.util.Locale.US, kotlin.math.abs(v))
        fun days(v: Long): String { val a = kotlin.math.abs(v); return when { a >= 60 -> "${a / 30} months"; a >= 14 -> "${a / 7} weeks"; a == 1L -> "1 day"; else -> "$a days" } }
        if (d.overdue) {
            // Only what has run out.
            val over = listOfNotNull(d.kmLeft?.takeIf { it <= 0 }?.let(::km), d.daysLeft?.takeIf { it <= 0 }?.let(::days))
            return "Overdue by " + over.joinToString(" / ")
        }
        val parts = listOfNotNull(d.kmLeft?.let(::km), d.daysLeft?.let(::days))
        if (parts.isEmpty()) return "Enter the odometer to track this"
        return "Due in " + parts.joinToString(" or ")
    }

    fun encode(m: Maintenance): String = JSONObject().apply {
        m.odoKm?.let { put("odo", it) }
        put("odoAt", m.odoAtLoggedKm)
        put("items", JSONObject().apply {
            m.records.forEach { (k, r) -> put(k.name, JSONObject().put("km", r.doneKm).put("day", r.doneDay).put("every", r.intervalKm)) }
        })
    }.toString()

    fun decode(json: String?): Maintenance = runCatching {
        val o = JSONObject(json ?: return Maintenance())
        val items = o.optJSONObject("items") ?: JSONObject()
        val recs = ServiceItem.entries.mapNotNull { k ->
            val r = items.optJSONObject(k.name) ?: return@mapNotNull null
            k to ServiceRecord(r.getInt("km"), r.getLong("day"), r.optInt("every", k.defaultKm))
        }.toMap()
        Maintenance(if (o.has("odo")) o.getInt("odo") else null, o.optDouble("odoAt", 0.0), recs)
    }.getOrDefault(Maintenance())
}
