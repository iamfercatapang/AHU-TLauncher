package com.ahu.tlauncher.ui.home

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
 * Night look for the Google map, after the dashboard prototype's map: deep blue-grey ground,
 * slate roads, teal water, quiet labels and no business clutter. Written for TLauncher.
 */
private const val GOOGLE_DARK_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1b2029"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#8a94a3"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#1b2029"}]},
 {"elementType":"labels.icon","stylers":[{"saturation":-90},{"lightness":-25}]},
 {"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"color":"#2f3744"}]},
 {"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#1f252f"}]},
 {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#1f252f"}]},
 {"featureType":"poi.business","stylers":[{"visibility":"off"}]},
 {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#1c2a2a"}]},
 {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6f8a86"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2d3542"}]},
 {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1b2029"}]},
 {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#36404f"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#46536a"}]},
 {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#b8c1cf"}]},
 {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#262d38"}]},
 {"featureType":"transit.station","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#173c46"}]},
 {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4f7d88"}]}
]"""

/** Day look: Google's map, quieter (muted colours, no business icons), to sit with DESIGN.md's white UI. */
private const val GOOGLE_LIGHT_STYLE = """[
 {"elementType":"geometry","stylers":[{"saturation":-35}]},
 {"elementType":"labels.icon","stylers":[{"saturation":-70}]},
 {"featureType":"poi.business","stylers":[{"visibility":"off"}]},
 {"featureType":"transit.station","elementType":"labels.icon","stylers":[{"visibility":"off"}]}
]"""

/** The car on the map: the prototype's red navigation arrow (TLauncher's own drawing), white edge. */
private fun carArrow(density: Float): com.google.android.gms.maps.model.BitmapDescriptor {
    val s = (34 * density).toInt()
    val b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val path = android.graphics.Path().apply {
        moveTo(s * 0.5f, s * 0.08f); lineTo(s * 0.86f, s * 0.9f); lineTo(s * 0.5f, s * 0.72f); lineTo(s * 0.14f, s * 0.9f); close()
    }
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.style = android.graphics.Paint.Style.FILL; p.color = 0xFFE5484D.toInt(); c.drawPath(path, p)
    p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2f * density; p.strokeJoin = android.graphics.Paint.Join.ROUND
    p.color = android.graphics.Color.WHITE; c.drawPath(path, p)
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(b)
}

/** Position, course and speed of the car, for the map camera. */
private data class CarFix(val lat: Double?, val lon: Double?, val bearing: Float?, val speed: Int?)

private class GoogleHandle(val view: MapView) : MapControls {
    var map: GoogleMap? = null
    var mode = DriveCamera.Mode.PARK
    var following = true
    var lastTouch = 0L
    var lat: Double? = null
    var lon: Double? = null
    var gpsBearing: Float? = null
    /** Map rotation for the compass. */
    val bearingState = androidx.compose.runtime.mutableFloatStateOf(0f)
    override val bearing: Float get() = bearingState.floatValue
    override fun northUp() { val m = map ?: return; following = false; m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0f).tilt(0f).build()), 500, null) }
    var speedKmh: Int? = null
    /** Low-performance mode: no tilt while driving. */
    var lowPerf = false
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
            CameraPosition(target, DriveCamera.zoomFor(speedKmh ?: 30).toFloat(), DriveCamera.driveTilt(lowPerf).toFloat(), gpsBearing ?: m.cameraPosition.bearing)
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
            m.setOnCameraMoveListener { handle.bearingState.floatValue = m.cameraPosition.bearing }
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
    LaunchedEffect(m, dark) { m?.setMapStyle(MapStyleOptions(if (dark) GOOGLE_DARK_STYLE else GOOGLE_LIGHT_STYLE)) }
    LaunchedEffect(m, traffic) { m?.isTrafficEnabled = traffic }
    // The car is drawn as the prototype's red arrow (pointing along the GPS course) instead of Google's blue dot.
    val carDensity = LocalContext.current.resources.displayMetrics.density
    val car = remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    LaunchedEffect(handle, state.lowPerf) {
        if (handle.lowPerf != state.lowPerf) { handle.lowPerf = state.lowPerf; handle.follow(animateMs = 600) }
    }
    // On every GPS fix (or new speed): move the car arrow and follow it; speed decides park vs drive.
    // Watched here rather than read in composition, so a new fix doesn't recompose the map.
    val current = rememberUpdatedState(state)
    LaunchedEffect(m) {
        val gm = m ?: return@LaunchedEffect
        snapshotFlow { current.value.let { s -> CarFix(s.locationLat, s.locationLon, s.locationBearing, s.displaySpeedKmh) } }.collect { fix ->
            if (fix.lat != null && fix.lon != null) {
                val pos = LatLng(fix.lat, fix.lon)
                val marker = car.value ?: gm.addMarker(
                    com.google.android.gms.maps.model.MarkerOptions().position(pos).icon(carArrow(carDensity)).anchor(0.5f, 0.55f).flat(true).zIndex(10f),
                )?.also { car.value = it }
                marker?.position = pos
                fix.bearing?.let { marker?.rotation = it }
            }
            handle.lat = fix.lat; handle.lon = fix.lon
            if ((fix.speed ?: 0) >= 5) fix.bearing?.let { handle.gpsBearing = it } // GPS course is noise when crawling
            handle.speedKmh = fix.speed
            val next = DriveCamera.nextMode(handle.mode, fix.speed)
            if (next != handle.mode) {
                handle.mode = next
                if (next == DriveCamera.Mode.DRIVE) handle.following = true
            }
            handle.follow()
        }
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
