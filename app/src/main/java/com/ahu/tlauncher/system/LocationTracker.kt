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

    fun hasPermission() = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission()) return
        started = true
        val providers = lm.getProviders(true)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { _location.value = it }
        for (p in providers) {
            if (p == LocationManager.PASSIVE_PROVIDER) continue
            runCatching { lm.requestLocationUpdates(p, 60_000L, 200f, listener, Looper.getMainLooper()) }
        }
    }
}
