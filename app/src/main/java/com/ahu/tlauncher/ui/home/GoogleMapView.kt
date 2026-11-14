package com.ahu.tlauncher.ui.home

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay

/**
 * Night look for the Google map, matching the dashboard's carbon greys.
 * Written for TLauncher (not copied from Google's samples).
 */
private const val GOOGLE_DARK_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1d2025"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#8a8f98"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#1d2025"}]},
 {"elementType":"labels.icon","stylers":[{"saturation":-80},{"lightness":-30}]},
 {"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"color":"#3a3e46"}]},
 {"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#22262c"}]},
 {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#22262c"}]},
 {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#1f2a24"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#353a43"}]},
 {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1d2025"}]},
 {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#3f444e"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#4c525d"}]},
 {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#b4b9c2"}]},
 {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2a2e35"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0f1a24"}]},
 {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4d6276"}]}
]"""

private class GoogleHandle(val view: MapView) : MapControls {
    var map: GoogleMap? = null
    var mode = DriveCamera.Mode.PARK
    var following = true
    var lastTouch = 0L
    var lat: Double? = null
    var lon: Double? = null
    var bearing: Float? = null
    var speedKmh: Int? = null
    val routeShapes = ArrayList<Any>() // Polylines + destination Marker
    var fittedRoute: Any? = null

    override fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn(), 300, null) }
    override fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut(), 300, null) }
    override fun recenter() { following = true; follow(animateMs = 600) }

    /** Drive: heading-up, tilted, zoom by speed. Park: flat, north-up overview. */
    fun follow(animateMs: Int = 900) {
        val m = map ?: return
        if (!following) return
        val target = LatLng(lat ?: return, lon ?: return)
        val pos = if (mode == DriveCamera.Mode.DRIVE) {
            CameraPosition(target, DriveCamera.zoomFor(speedKmh ?: 30).toFloat(), DriveCamera.DRIVE_TILT.toFloat(), bearing ?: m.cameraPosition.bearing)
        } else {
            CameraPosition(target, DriveCamera.PARK_ZOOM.toFloat(), DriveCamera.PARK_TILT.toFloat(), 0f)
        }
        m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), animateMs, null)
    }
}

/** Google's map with live traffic, drawn by TLauncher (Google Maps SDK). Same drive/park behaviour as the free map. */
@SuppressLint("MissingPermission")
@Composable
internal fun googleMap(dark: Boolean, traffic: Boolean, state: HomeUiState, modifier: Modifier): MapControls {
    val ctx = LocalContext.current
    val handle = remember {
        MapsInitializer.initialize(ctx.applicationContext, MapsInitializer.Renderer.LATEST) {}
        val opts = GoogleMapOptions()
            .camera(CameraPosition(LatLng(state.locationLat ?: 14.5995, state.locationLon ?: 120.9842), 15f, 0f, 0f))
            .compassEnabled(false).mapToolbarEnabled(false).zoomControlsEnabled(false)
        GoogleHandle(MapView(ctx, opts).apply { onCreate(null) })
    }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    LifecycleBinding(handle.view::onStart, handle.view::onResume, handle.view::onPause, handle.view::onStop, handle.view::onDestroy)
    LaunchedEffect(handle) {
        handle.view.getMapAsync { m ->
            handle.map = m; map = m
            m.uiSettings.isMyLocationButtonEnabled = false
            m.uiSettings.isRotateGesturesEnabled = true
            m.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    handle.following = false
                    handle.lastTouch = SystemClock.elapsedRealtime()
                }
            }
            handle.follow(animateMs = 1)
        }
    }
    val m = map
    LaunchedEffect(m, dark) { m?.setMapStyle(if (dark) MapStyleOptions(GOOGLE_DARK_STYLE) else null) }
    LaunchedEffect(m, traffic) { m?.isTrafficEnabled = traffic }
    LaunchedEffect(m, state.locationPermission) { if (m != null && state.locationPermission) runCatching { m.isMyLocationEnabled = true } }

    // Follow the car on every GPS update; speed decides park vs drive.
    val speed = state.displaySpeedKmh
    LaunchedEffect(m, state.locationLat, state.locationLon, state.locationBearing, speed) {
        handle.lat = state.locationLat; handle.lon = state.locationLon
        if ((speed ?: 0) >= 5) state.locationBearing?.let { handle.bearing = it } // GPS course is noise when crawling
        handle.speedKmh = speed
        val next = DriveCamera.nextMode(handle.mode, speed)
        if (next != handle.mode) {
            handle.mode = next
            if (next == DriveCamera.Mode.DRIVE) handle.following = true
        }
        handle.follow()
    }
    // While driving, re-follow the car 10 s after the last touch.
    LaunchedEffect(handle) {
        while (true) {
            delay(1000)
            if (!handle.following && handle.mode == DriveCamera.Mode.DRIVE &&
                SystemClock.elapsedRealtime() - handle.lastTouch >= DriveCamera.REFOLLOW_MS
            ) handle.recenter()
        }
    }
    // Route line: driven part trimmed, slow / jammed sections coloured from live traffic.
    val density = LocalContext.current.resources.displayMetrics.density
    LaunchedEffect(m, state.route) {
        val gm = m ?: return@LaunchedEffect
        handle.routeShapes.forEach { (it as? com.google.android.gms.maps.model.Polyline)?.remove(); (it as? com.google.android.gms.maps.model.Marker)?.remove() }
        handle.routeShapes.clear()
        val r = state.route
        val data = r?.route
        if (r == null || data == null) { handle.fittedRoute = null; gm.setPadding(0, 0, 0, 0); return@LaunchedEffect }
        val active = r.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        val start = if (active) (r.progressSeg + 1).coerceAtMost(data.points.size - 1) else 0
        fun ll(p: com.ahu.tlauncher.route.LatLon) = LatLng(p.lat, p.lon)
        val pts = buildList { if (active) r.car?.let { add(ll(it)) }; data.points.drop(start).forEach { add(ll(it)) } }
        if (pts.size >= 2) {
            fun line(points: List<LatLng>, color: androidx.compose.ui.graphics.Color, widthDp: Float, z: Float) =
                gm.addPolyline(
                    com.google.android.gms.maps.model.PolylineOptions().addAll(points).color(color.toArgb()).width(widthDp * density).zIndex(z)
                        .startCap(com.google.android.gms.maps.model.RoundCap()).endCap(com.google.android.gms.maps.model.RoundCap())
                        .jointType(com.google.android.gms.maps.model.JointType.ROUND),
                )
            handle.routeShapes += line(pts, ROUTE_OUTLINE, 9f, 1f)
            handle.routeShapes += line(pts, ROUTE_BLUE, 6f, 2f)
            data.traffic.filter { it.speed != com.ahu.tlauncher.route.Traffic.NORMAL }.forEach { t ->
                val from = maxOf(t.from, start); val to = minOf(t.to, data.points.size - 1)
                if (to > from) handle.routeShapes += line(data.points.subList(from, to + 1).map(::ll),
                    if (t.speed == com.ahu.tlauncher.route.Traffic.JAM) TRAFFIC_JAM else TRAFFIC_SLOW, 6f, 3f)
            }
        }
        gm.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(ll(data.points.last())).title(r.destination.name))?.let { handle.routeShapes += it }
        // Leave room for the cards over the map.
        gm.setPadding(0, (60 * density).toInt(), 0, ((if (active) 80 else 170) * density).toInt())
        if (!active && handle.fittedRoute !== data) {
            // Preview: show the whole route, stop following the car.
            handle.fittedRoute = data
            handle.following = false
            val b = com.google.android.gms.maps.model.LatLngBounds.builder().apply { data.points.forEach { include(ll(it)) } }.build()
            runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b, (40 * density).toInt()), 700, null) }
        }
        if (active && handle.fittedRoute != null) { handle.fittedRoute = null; handle.recenter() }
    }
    AndroidView(factory = { handle.view }, modifier = modifier.clipToBounds())
    return handle
}
