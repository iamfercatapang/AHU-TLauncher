package com.ahu.tlauncher.system

import android.location.Location
import android.os.SystemClock
import com.ahu.tlauncher.LauncherApp
import com.ahu.tlauncher.route.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Trip log and maintenance. A trip starts when the car moves (GPS speed) and ends after it has stood still for a
 * few minutes or the head unit slept; its distance comes from GPS, its fuel from the OBD adapter when connected.
 * The odometer, read once from the dashboard, counts up with the logged distance; service items are due by km
 * and by date, and a reminder is offered when the car starts.
 */
class CarCare(private val app: LauncherApp, private val scope: CoroutineScope) {
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()
    /** All distance ever logged (km), for the odometer; the list itself only keeps the latest trips. */
    private val _loggedKm = MutableStateFlow(0.0)
    val loggedKm: StateFlow<Double> = _loggedKm.asStateFlow()
    private val _maintenance = MutableStateFlow(Maintenance())
    val maintenance: StateFlow<Maintenance> = _maintenance.asStateFlow()
    /** Service item to remind about on the dashboard (once per car start), null when none. */
    private val _reminder = MutableStateFlow<ServiceDue?>(null)
    val reminder: StateFlow<ServiceDue?> = _reminder.asStateFlow()

    private class Open(val startMs: Long, var lastMoveMs: Long, var km: Double, val litresAtStart: Double)
    private var open: Open? = null
    private var lastPoint: LatLon? = null
    private var lastPointAt = 0L
    private var loaded = false

    init {
        scope.launch {
            val (t, km) = app.prefs.loadTrips()
            _trips.value = t; _loggedKm.value = km
            _maintenance.value = app.prefs.loadMaintenance()
            loaded = true
            app.location.location.collect { it?.let(::onFix) }
        }
        // Ends a trip once the car has stood still long enough, also after the head unit wakes from sleep.
        scope.launch {
            while (true) {
                delay(30_000)
                val o = open ?: continue
                if (System.currentTimeMillis() - o.lastMoveMs >= TripMath.STOP_MS) close()
            }
        }
    }

    private fun onFix(loc: Location) {
        if (!loaded) return
        val now = System.currentTimeMillis()
        open?.let { if (now - it.lastMoveMs >= TripMath.STOP_MS) close() }
        val p = LatLon(loc.latitude, loc.longitude)
        val moving = loc.hasSpeed() && loc.speed * 3.6f >= TripMath.MOVING_KMH
        val o = open
        if (o == null) {
            if (moving) open = Open(now, now, 0.0, app.fuel.stats.value.tripLitres)
        } else {
            // Distance only while rolling (even crawling in traffic): GPS drifts a few metres while parked.
            val rolling = loc.hasSpeed() && loc.speed * 3.6f >= 2f
            if (rolling) lastPoint?.let { from ->
                o.km += TripMath.stepKm(from, p, loc.takeIf { it.hasAccuracy() }?.accuracy, SystemClock.elapsedRealtime() - lastPointAt)
            }
            if (moving) o.lastMoveMs = now
        }
        lastPoint = p; lastPointAt = SystemClock.elapsedRealtime()
    }

    private fun close() {
        val o = open ?: return
        open = null
        if (o.km < TripMath.MIN_KM) return
        // Fuel from the OBD trip counter, if it moved (it doesn't without an adapter, or after "Reset trip").
        val used = app.fuel.stats.value.tripLitres - o.litresAtStart
        val trip = Trip(o.startMs, o.lastMoveMs, o.km, used.takeIf { it > 0.01 })
        val list = (_trips.value + trip).takeLast(TripMath.MAX_TRIPS)
        _trips.value = list
        _loggedKm.value += o.km
        val km = _loggedKm.value
        scope.launch { app.prefs.saveTrips(list, km) }
    }

    /** The drive under way (distance so far), for Settings. */
    fun currentKm(): Double? = open?.km

    fun clearTrips() {
        _trips.value = emptyList()
        val km = _loggedKm.value
        scope.launch { app.prefs.saveTrips(emptyList(), km) }
    }

    // ------------------------------------------------------------------------------------------ maintenance

    fun odometer(): Int? = MaintenanceMath.odometer(_maintenance.value, _loggedKm.value)

    fun setOdometer(km: Int) = save(_maintenance.value.copy(odoKm = km, odoAtLoggedKm = _loggedKm.value))

    /** Done today, at the current odometer reading. */
    fun markDone(item: ServiceItem) {
        val odo = odometer() ?: return
        val m = _maintenance.value
        val every = m.records[item]?.intervalKm ?: item.defaultKm
        save(m.copy(records = m.records + (item to ServiceRecord(odo, LocalDate.now().toEpochDay(), every))))
        if (_reminder.value?.item == item) _reminder.value = null
    }

    fun setInterval(item: ServiceItem, km: Int) {
        val m = _maintenance.value
        val r = m.records[item] ?: return
        save(m.copy(records = m.records + (item to r.copy(intervalKm = km))))
    }

    fun stopTracking(item: ServiceItem) {
        val m = _maintenance.value
        save(m.copy(records = m.records - item))
        if (_reminder.value?.item == item) _reminder.value = null
    }

    fun due(): List<ServiceDue> = MaintenanceMath.all(_maintenance.value, _loggedKm.value, LocalDate.now())

    /** When the car starts: remind about the most pressing item that's due soon. */
    fun onCarStart() { _reminder.value = due().firstOrNull { it.soon } }

    /** "Later" on the reminder: hidden until the next start. */
    fun snoozeReminder() { _reminder.value = null }

    private fun save(m: Maintenance) {
        _maintenance.value = m
        scope.launch { app.prefs.saveMaintenance(m) }
    }
}
