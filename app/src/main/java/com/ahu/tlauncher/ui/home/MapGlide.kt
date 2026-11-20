package com.ahu.tlauncher.ui.home

import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.route.RouteMath
import kotlin.math.abs
import kotlin.math.exp

/**
 * Smooth car motion between GPS fixes (about one a second). Instead of jumping to each fix, the car glides
 * toward it at a steady pace, taking as long as the gap between the last two fixes, so by the time it gets
 * there the next fix has usually arrived and it carries straight on. Pure, unit tested.
 */
internal class MapGlide {
    private var fromLat = 0.0
    private var fromLon = 0.0
    private var toLat = 0.0
    private var toLon = 0.0
    private var fromBearing = 0f
    private var toBearing = 0f
    private var startMs = 0L
    private var durMs = DEFAULT_MS
    private var lastFixMs = 0L
    var hasFix = false
        private set

    fun fix(lat: Double, lon: Double, bearing: Float?, nowMs: Long) {
        val jump = hasFix && RouteMath.distanceM(LatLon(lat(nowMs), lon(nowMs)), LatLon(lat, lon)) > SNAP_M
        if (!hasFix || jump) {
            // First fix, or back after a long gap (tunnel, GPS off): go straight there.
            fromLat = lat; fromLon = lon
            fromBearing = bearing ?: toBearing
            durMs = DEFAULT_MS
        } else {
            fromLat = lat(nowMs); fromLon = lon(nowMs); fromBearing = bearing(nowMs)
            durMs = (nowMs - lastFixMs).coerceIn(MIN_MS, MAX_MS)
        }
        toLat = lat; toLon = lon
        if (bearing != null) toBearing = bearing else toBearing = fromBearing
        startMs = nowMs; lastFixMs = nowMs
        hasFix = true
    }

    private fun t(nowMs: Long): Double = ((nowMs - startMs).toDouble() / durMs).coerceIn(0.0, 1.0)

    fun lat(nowMs: Long): Double = fromLat + (toLat - fromLat) * t(nowMs)
    fun lon(nowMs: Long): Double = fromLon + (toLon - fromLon) * t(nowMs)

    /** The arrow turns a little faster than it moves, so it points along the road in time for the next stretch. */
    fun bearing(nowMs: Long): Float = lerpAngle(fromBearing, toBearing, (t(nowMs) * 1.5).coerceAtMost(1.0).toFloat())

    /** Still on the way to the latest fix. */
    fun moving(nowMs: Long): Boolean = hasFix && nowMs < startMs + durMs

    companion object {
        const val DEFAULT_MS = 1_000L
        const val MIN_MS = 200L
        const val MAX_MS = 1_500L
        /** Further than this from where the car is shown: jump instead of gliding across the map. */
        const val SNAP_M = 300.0
    }
}

/** From [a] to [b] the short way round, [t] of the way (degrees, 0–360). */
internal fun lerpAngle(a: Float, b: Float, t: Float): Float {
    val d = ((b - a) % 360f + 540f) % 360f - 180f
    return ((a + d * t) % 360f + 360f) % 360f
}

/** Eases [current] toward [target]: after [tauMs] it has covered about 63% of the way, whatever the frame rate. */
internal fun approach(current: Double, target: Double, dtMs: Long, tauMs: Long): Double =
    target + (current - target) * exp(-dtMs.toDouble() / tauMs)

internal fun approachAngle(current: Float, target: Float, dtMs: Long, tauMs: Long): Float =
    lerpAngle(current, target, (1 - exp(-dtMs.toDouble() / tauMs)).toFloat())

/** Bearings closer than [deg] degrees count as equal. */
internal fun anglesClose(a: Float, b: Float, deg: Float): Boolean = abs(((b - a) % 360f + 540f) % 360f - 180f) < deg
