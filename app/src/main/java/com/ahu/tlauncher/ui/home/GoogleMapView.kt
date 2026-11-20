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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.withFrameMillis
import com.google.android.gms.maps.model.Polyline

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
    /** Course of the latest fix (as reported, even when crawling). */
    var fixBearing: Float? = null
    /** Map rotation for the compass. */
    val bearingState = androidx.compose.runtime.mutableFloatStateOf(0f)
    override val bearing: Float get() = bearingState.floatValue
    val scaleState = androidx.compose.runtime.mutableFloatStateOf(0f)
    override val metersPerDp: Float? get() = scaleState.floatValue.takeIf { it > 0f }
    override fun northUp() { val m = map ?: return; following = false; m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0f).tilt(0f).build()), 500, null) }
    var speedKmh: Int? = null
    /** Low-performance mode: no tilt while driving. */
    var lowPerf = false
    /** A route is being followed (TLauncher's guidance, or Google Maps / Waze navigating), and which side its cards are on. */
    var navigating = false
    var cardsRight = false
    private var appliedPad: IntArray? = null
    val routeShapes = ArrayList<Any>() // Polylines + destination Marker
    var fittedRoute: Any? = null
    var pinMarker: com.google.android.gms.maps.model.Marker? = null

    /** Forget the applied padding so the next follow sets it again ([dirty]: something else changed it). */
    fun resetPadding(dirty: Boolean = false) {
        if (!dirty) map?.setPadding(0, 0, 0, 0)
        appliedPad = if (dirty) null else IntArray(4)
    }

    override fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn(), 300, null) }
    override fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut(), 300, null) }
    override fun recenter() { following = true; follow(animateMs = 600) }

    /** Smooth following: the car glides between fixes, zoom and turns ease. Off in low-performance mode. */
    val smooth: Boolean get() = !lowPerf
    val glide = MapGlide()
    private var zoomShown: Double? = null
    private var bearingShown: Float? = null
    /** An animated camera move (recenter) plays until then; the frame loop leaves the camera alone meanwhile. */
    private var holdUntil = 0L
    /** Bumped when the frame loop has something to do (a new fix, the view changed). */
    val ticks = MutableStateFlow(0)
    fun wake() { ticks.value++ }
    /** The route line from the car on (casing and line), and the route after the car, for moving its start with the car. */
    var routeLines: List<Polyline> = emptyList()
    var routeTail: List<LatLng> = emptyList()
    private var headAt = 0L

    /** Where the car is drawn: gliding (smooth), or the latest fix. */
    fun carPos(): LatLng? {
        if (smooth && glide.hasFix) return SystemClock.elapsedRealtime().let { LatLng(glide.lat(it), glide.lon(it)) }
        return LatLng(lat ?: return null, lon ?: return null)
    }

    private fun applyPadding(m: GoogleMap, drive: Boolean) {
        val pad = if (drive && view.width > 0) DriveCamera.padding(view.width, view.height, DriveCamera.carAnchor(navigating, cardsRight)) else IntArray(4)
        if (!pad.contentEquals(appliedPad)) { m.setPadding(pad[0], pad[1], pad[2], pad[3]); appliedPad = pad }
    }

    /**
     * Drive (or navigating): flat, heading-up, close zoom by speed, the car low on the map (and away from the
     * route cards while navigating). Park: flat, north-up overview with the car in the middle.
     * Smooth mode: without [animateMs] this only wakes the frame loop, which eases the camera there.
     */
    fun follow(animateMs: Int? = null) {
        val m = map ?: return
        if (!following) return
        if (smooth && animateMs == null) { wake(); return }
        val target = carPos() ?: return
        val drive = DriveCamera.driveView(mode, navigating)
        applyPadding(m, drive)
        val pos = if (drive) {
            CameraPosition(target, (DriveCamera.zoomFor(speedKmh ?: 30) + DriveCamera.TILE_256_OFFSET).toFloat(), DriveCamera.driveTilt(lowPerf).toFloat(), gpsBearing ?: m.cameraPosition.bearing)
        } else {
            CameraPosition(target, (DriveCamera.PARK_ZOOM + DriveCamera.TILE_256_OFFSET).toFloat(), DriveCamera.PARK_TILT.toFloat(), 0f)
        }
        val ms = animateMs ?: 900
        m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), ms, null)
        if (smooth) { holdUntil = SystemClock.elapsedRealtime() + ms + 50; zoomShown = null; bearingShown = null; wake() }
    }

    /**
     * One frame of smooth following: moves the car arrow (and the start of the route line) along its glide, and
     * eases the camera after it. Returns true while anything is still moving.
     */
    fun frame(marker: com.google.android.gms.maps.model.Marker?, dtMs: Long): Boolean {
        val m = map ?: return false
        if (!glide.hasFix) return false
        val now = SystemClock.elapsedRealtime()
        val moving = glide.moving(now)
        val pos = LatLng(glide.lat(now), glide.lon(now))
        marker?.position = pos
        marker?.rotation = glide.bearing(now)
        // The line starts at the car: moved about 10 times a second (it can be thousands of points long).
        if (routeLines.isNotEmpty() && (now - headAt >= 100 || !moving)) {
            headAt = now
            val pts = ArrayList<LatLng>(routeTail.size + 1).apply { add(pos); addAll(routeTail) }
            routeLines.forEach { it.points = pts }
        }
        if (!following) { zoomShown = null; bearingShown = null; return moving }
        if (now < holdUntil) return true
        val drive = DriveCamera.driveView(mode, navigating)
        applyPadding(m, drive)
        val cp = m.cameraPosition
        val zoomTo = (if (drive) DriveCamera.zoomFor(speedKmh ?: 30) else DriveCamera.PARK_ZOOM) + DriveCamera.TILE_256_OFFSET
        val turnTo = if (drive) (gpsBearing ?: bearingShown ?: cp.bearing) else 0f
        val tilt = (if (drive) DriveCamera.driveTilt(lowPerf) else DriveCamera.PARK_TILT).toFloat()
        val z = approach(zoomShown ?: cp.zoom.toDouble(), zoomTo, dtMs, DriveCamera.ZOOM_EASE_MS)
        val b = approachAngle(bearingShown ?: cp.bearing, turnTo, dtMs, DriveCamera.TURN_EASE_MS)
        val settled = kotlin.math.abs(z - zoomTo) < 0.005 && anglesClose(b, turnTo, 0.3f)
        val zs = if (settled) zoomTo else z
        val bs = if (settled) turnTo else b
        zoomShown = zs; bearingShown = bs
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(pos, zs.toFloat(), tilt, bs)))
        return moving || !settled
    }
}

/** Google's map with live traffic, drawn by TLauncher (Google Maps SDK). Same drive/park behaviour as the free map. */
@SuppressLint("MissingPermission")
@Composable
internal fun googleMap(dark: Boolean, traffic: Boolean, state: HomeUiState, modifier: Modifier, onLongPress: (Double, Double) -> Unit): MapControls {
    val ctx = LocalContext.current
    val longPress = rememberUpdatedState(onLongPress)
    val handle = remember {
        MapsInitializer.initialize(ctx.applicationContext, MapsInitializer.Renderer.LATEST) {}
        val opts = GoogleMapOptions()
            .camera(CameraPosition(LatLng(state.locationLat ?: 14.5995, state.locationLon ?: 120.9842), (DriveCamera.PARK_ZOOM + DriveCamera.TILE_256_OFFSET).toFloat(), 0f, 0f))
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
            val onCamera = {
                val cp = m.cameraPosition
                handle.bearingState.floatValue = cp.bearing
                handle.scaleState.floatValue = metersPerDp(cp.zoom.toDouble(), cp.target.latitude, 256)
            }
            onCamera()
            m.setOnCameraMoveListener { onCamera() }
            m.setOnCameraIdleListener { onCamera() }
            m.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    handle.following = false
                    handle.lastTouch = SystemClock.elapsedRealtime()
                }
            }
            m.setOnMapLongClickListener { p ->
                handle.view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                longPress.value(p.latitude, p.longitude)
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
                // Only a new position or course starts a new glide (a speed change alone doesn't).
                val moved = fix.lat != handle.lat || fix.lon != handle.lon || fix.bearing != handle.fixBearing || !handle.glide.hasFix
                if (handle.smooth) { if (moved) handle.glide.fix(fix.lat, fix.lon, fix.bearing, SystemClock.elapsedRealtime()) }
                else { marker?.position = pos; fix.bearing?.let { marker?.rotation = it } }
            }
            handle.lat = fix.lat; handle.lon = fix.lon; handle.fixBearing = fix.bearing
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
    // Smooth mode: every frame while the car glides or the camera eases; idle until the next fix otherwise.
    LaunchedEffect(m) {
        if (m == null) return@LaunchedEffect
        var last = 0L
        while (true) {
            val seen = handle.ticks.value
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 16L else (now - last).coerceIn(1L, 100L)
            last = now
            val busy = handle.smooth && handle.frame(car.value, dt)
            if (!busy) { last = 0L; handle.ticks.first { it != seen } }
        }
    }
    // Navigating (own route guidance, or Google Maps / Waze): keep the drive view, car away from the route cards.
    LaunchedEffect(handle) {
        snapshotFlow { current.value.let { it.isNavigating to (it.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT) } }.collect { (nav, right) ->
            handle.navigating = nav; handle.cardsRight = right
            handle.follow()
        }
    }
    // While driving or navigating, re-follow the car 10 s after the last touch.
    LaunchedEffect(handle) {
        while (true) {
            delay(1000)
            if (!handle.following && DriveCamera.driveView(handle.mode, handle.navigating) &&
                SystemClock.elapsedRealtime() - handle.lastTouch >= DriveCamera.REFOLLOW_MS
            ) handle.recenter()
        }
    }
    // Route line: driven part trimmed, slow / jammed sections coloured from live traffic. Drawn once per route;
    // as the car moves the lines are updated in place (not removed and drawn again, which flickered).
    val density = LocalContext.current.resources.displayMetrics.density
    val r = state.route
    val active = r?.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
    fun ll(p: com.ahu.tlauncher.route.LatLon) = LatLng(p.lat, p.lon)
    fun line(gm: GoogleMap, points: List<LatLng>, color: androidx.compose.ui.graphics.Color, widthDp: Float, z: Float) =
        gm.addPolyline(
            com.google.android.gms.maps.model.PolylineOptions().addAll(points).color(color.toArgb()).width(widthDp * density).zIndex(z)
                .startCap(com.google.android.gms.maps.model.RoundCap()).endCap(com.google.android.gms.maps.model.RoundCap())
                .jointType(com.google.android.gms.maps.model.JointType.ROUND),
        )
    val trafficLines = remember { ArrayList<Polyline>() }
    LaunchedEffect(m, r?.route, r?.phase, r?.alternatives, r?.selected, r?.destination) {
        val gm = m ?: return@LaunchedEffect
        handle.routeShapes.forEach { (it as? Polyline)?.remove(); (it as? com.google.android.gms.maps.model.Marker)?.remove() }
        handle.routeShapes.clear()
        trafficLines.forEach { it.remove() }; trafficLines.clear()
        handle.routeLines = emptyList(); handle.routeTail = emptyList()
        val rs = current.value.route
        val data = rs?.route
        if (rs == null || data == null) { handle.fittedRoute = null; handle.resetPadding(); handle.follow(); return@LaunchedEffect }
        val isActive = rs.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        if (data.points.size >= 2) {
            // other route choices (preview only), grey and under the chosen one
            if (!isActive) rs.alternatives.forEachIndexed { i, alt ->
                if (i == rs.selected || alt.points.size < 2) return@forEachIndexed
                val ap = alt.points.map(::ll)
                handle.routeShapes += line(gm, ap, ROUTE_ALT_CASING, 9f, 0.5f)
                handle.routeShapes += line(gm, ap, ROUTE_ALT, 5.5f, 0.6f)
            }
            val all = data.points.map(::ll)
            val lines = listOf(line(gm, all, ROUTE_LINE_CASING, 10f, 1f), line(gm, all, ROUTE_LINE, 6.5f, 2f))
            handle.routeShapes.addAll(lines)
            // While driving, the progress effect below trims the lines and moves their start with the car.
            if (isActive) handle.routeLines = lines
            else data.traffic.filter { it.speed != com.ahu.tlauncher.route.Traffic.NORMAL }.forEach { t ->
                val to = minOf(t.to, data.points.size - 1)
                if (to > t.from) trafficLines += line(gm, data.points.subList(t.from, to + 1).map(::ll),
                    if (t.speed == com.ahu.tlauncher.route.Traffic.JAM) TRAFFIC_JAM else TRAFFIC_SLOW, 6f, 3f)
            }
        }
        gm.addMarker(
            com.google.android.gms.maps.model.MarkerOptions().position(ll(data.points.last())).title(rs.destination.name).anchor(0.5f, 0.5f).zIndex(4f)
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(destinationBitmap(density))),
        )?.let { handle.routeShapes += it }
        // Leave room for the cards over the map: the route card (top, away from the map buttons) while previewing,
        // the turn card (top) and trip card (bottom) while driving.
        val card = ((NAV_CARD_W.value + 36) * density).toInt(); val edge = (70 * density).toInt()
        val cardsRight = current.value.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT
        // (While driving the route, the drive view places the car instead: low, away from the cards.)
        if (!isActive) { gm.setPadding(if (cardsRight) edge else card, (60 * density).toInt(), if (cardsRight) card else edge, (30 * density).toInt()); handle.resetPadding(dirty = true) }
        if (!isActive && handle.fittedRoute !== data) {
            // Preview: show the whole route, stop following the car.
            handle.fittedRoute = data
            handle.following = false
            val b = com.google.android.gms.maps.model.LatLngBounds.builder().apply { rs.alternatives.ifEmpty { listOf(data) }.forEach { a -> a.points.forEach { include(ll(it)) } } }.build()
            runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b, (40 * density).toInt()), 700, null) }
        }
        if (isActive && handle.fittedRoute != null) { handle.fittedRoute = null; handle.recenter() }
        handle.wake()
    }
    // Driving the route: trim the driven part (and its traffic colours) as progress moves on. In smooth mode the frame
    // loop moves the line's start with the gliding car; otherwise it starts at the latest fix.
    LaunchedEffect(m, r?.route, active, r?.progressSeg, if (handle.smooth) null else r?.car) {
        val gm = m ?: return@LaunchedEffect
        val rs = current.value.route ?: return@LaunchedEffect
        val data = rs.route ?: return@LaunchedEffect
        if (!active || handle.routeLines.isEmpty()) return@LaunchedEffect
        val start = (rs.progressSeg + 1).coerceAtMost(data.points.size - 1)
        handle.routeTail = data.points.drop(start).map(::ll)
        val head = handle.carPos() ?: rs.car?.let(::ll)
        val pts = buildList { head?.let { add(it) }; addAll(handle.routeTail) }
        if (pts.size >= 2) handle.routeLines.forEach { it.points = pts }
        trafficLines.forEach { it.remove() }; trafficLines.clear()
        data.traffic.filter { it.speed != com.ahu.tlauncher.route.Traffic.NORMAL }.forEach { t ->
            val from = maxOf(t.from, start); val to = minOf(t.to, data.points.size - 1)
            if (to > from) trafficLines += line(gm, data.points.subList(from, to + 1).map(::ll),
                if (t.speed == com.ahu.tlauncher.route.Traffic.JAM) TRAFFIC_JAM else TRAFFIC_SLOW, 6f, 3f)
        }
    }
    // Dropped pin.
    LaunchedEffect(m, state.pin) {
        val gm = m ?: return@LaunchedEffect
        handle.pinMarker?.remove(); handle.pinMarker = null
        val at = state.pin?.latLon ?: return@LaunchedEffect
        handle.pinMarker = gm.addMarker(
            com.google.android.gms.maps.model.MarkerOptions().position(LatLng(at.lat, at.lon)).anchor(0.5f, 1f).zIndex(5f)
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(pinBitmap(density))),
        )
    }
    AndroidView(factory = { handle.view }, modifier = modifier.clipToBounds())
    return handle
}
