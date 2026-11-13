package com.ahu.tlauncher.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight location used for the day/night switch and map fallback (no Play Services needed). */
class LocationTracker(private val context: Context) {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()
    private var started = false
    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listener = LocationListener { _location.value = it }
    private var fast = false

    fun hasPermission() = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission()) return
        started = true
        val providers = lm.getProviders(true)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { _location.value = it }
        request()
    }

    /** 1 s GPS updates while the launcher is on screen (for GPS speed); slow updates otherwise. */
    fun setFast(on: Boolean) {
        if (fast == on) return
        fast = on
        if (started) request()
    }

    @SuppressLint("MissingPermission")
    private fun request() {
        if (!hasPermission()) return
        runCatching { lm.removeUpdates(listener) }
        for (p in lm.getProviders(true)) {
            if (p == LocationManager.PASSIVE_PROVIDER) continue
            val (interval, distance) = if (fast && p == LocationManager.GPS_PROVIDER) 1_000L to 0f else 60_000L to 200f
            runCatching { lm.requestLocationUpdates(p, interval, distance, listener, Looper.getMainLooper()) }
        }
    }
}
