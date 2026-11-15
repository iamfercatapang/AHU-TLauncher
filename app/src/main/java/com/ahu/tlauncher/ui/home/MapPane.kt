package com.ahu.tlauncher.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ahu.tlauncher.ui.components.TIcons
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahu.tlauncher.notify.NavCardState
import com.ahu.tlauncher.notify.NavParser
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.TCard
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Free vector styles from OpenFreeMap: no API key, no usage limits. */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val DEFAULT_LAT = 14.5995
private const val DEFAULT_LON = 120.9842
private const val ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

/** What the on-map buttons can do, whichever engine is drawing the map. */
internal interface MapControls {
    fun zoomIn()
    fun zoomOut()
    fun recenter()
    /** Map rotation in degrees (0 = north up), for the compass. */
    val bearing: Float get() = 0f
    /** Compass tap: turn the map back to north-up. */
    fun northUp() {}
}

@Composable
fun MapPane(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier, controlsOnLeft: Boolean = false) {
    val dark = T.colors.isDark
    val preview = LocalInspectionMode.current
    val ctx = LocalContext.current
    val vectorCapable = remember { supportsGles3(ctx) }

    // Google when chosen and available (API key + Play services); otherwise the free OpenFreeMap map.
    val useGoogle = !preview && state.googleMapsAvailable && state.settings.mapEngine == com.ahu.tlauncher.data.MapEngine.GOOGLE
    // Long-press drops a pin (needs search/routing; not while a route is shown). Read through the updated state so
    // the maps' listeners, set once, always see the current rules.
    val pinRules = rememberUpdatedState(state.routesEnabled && state.route == null)
    val onLongPress: (Double, Double) -> Unit = remember(actions) { { lat: Double, lon: Double -> if (pinRules.value) actions.dropPin(lat, lon) } }
    Box(modifier.clipToBounds().background(T.colors.appBg)) {
        val controls: MapControls? = when {
            preview -> { MapPlaceholder(Modifier.fillMaxSize()); null }
            useGoogle -> googleMap(dark, state.settings.mapTraffic, state, Modifier.fillMaxSize(), onLongPress)
            vectorCapable -> vectorMap(dark, state, Modifier.fillMaxSize(), onLongPress)
            else -> rasterMap(dark, state, Modifier.fillMaxSize(), onLongPress)
        }

        // route preview / trip cards (route search needs the Google map)
        RouteOverlays(state, actions, Modifier.fillMaxSize())

        // top-left: live turn-by-turn card while Google Maps / Waze is navigating; otherwise the Navigate pill
        val side = if (controlsOnLeft) Alignment.TopEnd else Alignment.TopStart
        val nav = state.nav
        val freeTurn = state.route?.takeIf { it.guided && it.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE && it.turn != null }
        if (freeTurn != null) {
            FreeTurnCard(state, freeTurn, Modifier.align(side).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp))
        } else if (nav != null) {
            Box(Modifier.align(side).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp)) { NavCard(nav, onClick = actions::openNavCard) }
        } else if (state.route?.phase != com.ahu.tlauncher.route.RoutePhase.ACTIVE) {
            NavigatePill(actions::openNavApp, Modifier.align(side).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp))
        }

        // right edge, like the prototype: compass, zoom pill and follow-me at the top, settings at the bottom
        val edge = if (controlsOnLeft) Alignment.TopStart else Alignment.TopEnd
        Column(
            Modifier.align(edge).padding(horizontal = 10.dp).padding(top = STATUS_H + 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Compass(controls?.bearing ?: 0f) { controls?.northUp() }
            Column(Modifier.width(44.dp).clip(RoundedCornerShape(22.dp)).background(T.colors.chrome)) {
                MapRound(TIcons.Plus, "Zoom in", plain = true) { controls?.zoomIn() }
                MapRound(TIcons.Minus, "Zoom out", plain = true) { controls?.zoomOut() }
            }
            MapRound(TIcons.Locate, "Follow my location") {
                if (!state.locationPermission) actions.requestLocation()
                controls?.recenter()
            }
        }
        MapRound(
            TIcons.Gear, "Map settings",
            modifier = Modifier.align(if (controlsOnLeft) Alignment.BottomStart else Alignment.BottomEnd).padding(10.dp).padding(bottom = 14.dp),
            onClick = actions::openMapSettings,
        )

        if (!useGoogle) Text(
            if (vectorCapable) ATTRIBUTION else "© OpenStreetMap contributors", style = TType.caption, color = T.colors.muted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}

/** Round dark map button (prototype: 80 px circles at 90 % black). */
@Composable
private fun MapRound(icon: ImageVector, desc: String, modifier: Modifier = Modifier, plain: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).then(if (plain) Modifier else Modifier.background(T.colors.chrome)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (T.colors.isDark) Color.White.copy(alpha = 0.75f) else T.colors.text, modifier = Modifier.size(20.dp)) }
}

/** Compass disc with an "N" and a small north arrow; rotates with the map. Tap to turn north-up. */
@Composable
private fun Compass(bearing: Float, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(T.colors.chrome).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).graphicsLayer { rotationZ = -bearing }.clip(CircleShape).background(T.colors.muted), contentAlignment = Alignment.Center) {
            Text("N", style = TType.caption, color = Color.White)
            Icon(TIcons.ChevronUp, null, tint = Color.White, modifier = Modifier.align(Alignment.TopCenter).size(10.dp))
        }
    }
}

/** "Navigate ›" pill at the map's top-left (prototype's navigation entry). Opens destination search. */
@Composable
private fun NavigatePill(onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.width(210.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(T.colors.chrome).pressable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TIcons.Navigate, null, tint = T.colors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text("Navigate", style = TType.body, color = T.colors.muted, modifier = Modifier.weight(1f))
        Icon(TIcons.ChevronRight, null, tint = T.colors.muted, modifier = Modifier.size(16.dp))
    }
}

/** Red map pin for a long-pressed spot (TLauncher's drawing); its tip is the bottom centre. */
internal fun pinBitmap(d: Float): Bitmap {
    val w = (30 * d).toInt(); val h = (40 * d).toInt()
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = w / 2f - 2 * d
        val cx = w / 2f; val cy = r + 2 * d
        val path = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.62f, cy + r * 0.78f); lineTo(cx, h - 1.5f * d); lineTo(cx + r * 0.62f, cy + r * 0.78f); close()
        }
        p.style = Paint.Style.FILL; p.color = 0xFFE5484D.toInt(); c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFFFFFFFF.toInt(); c.drawPath(path, p)
        p.style = Paint.Style.FILL; c.drawCircle(cx, cy, r * 0.38f, p)
    }
}

private fun supportsGles3(ctx: Context): Boolean = runCatching {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}.getOrDefault(false)

/** Forward the screen lifecycle to a classic Android map view. */
@Composable
internal fun LifecycleBinding(onStart: () -> Unit, onResume: () -> Unit, onPause: () -> Unit, onStop: () -> Unit, onDestroy: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                Lifecycle.Event.ON_STOP -> onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs) // replays ON_START/ON_RESUME if already resumed
        onDispose {
            owner.lifecycle.removeObserver(obs)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onPause()
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStop()
            onDestroy()
        }
    }
}

// ---------------------------------------------------------------- MapLibre (vector)

/** Adds (once per style) and updates the route line and destination dot on the MapLibre map. */
private fun drawFreeRoute(style: Style, r: com.ahu.tlauncher.route.RouteState?, data: com.ahu.tlauncher.route.RouteData?) {
    val lineSrc = "tl-route"; val destSrc = "tl-route-dest"
    if (style.getSource(lineSrc) == null) {
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource(lineSrc))
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource(destSrc))
        val casing = org.maplibre.android.style.layers.LineLayer("tl-route-casing", lineSrc).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineColor("#1E3E9A"),
            org.maplibre.android.style.layers.PropertyFactory.lineWidth(9f),
            org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
            org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
        )
        val line = org.maplibre.android.style.layers.LineLayer("tl-route-line", lineSrc).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineColor("#3E6AE1"),
            org.maplibre.android.style.layers.PropertyFactory.lineWidth(6f),
            org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
            org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
        )
        val dest = org.maplibre.android.style.layers.CircleLayer("tl-route-dest", destSrc).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.circleColor("#E5484D"),
            org.maplibre.android.style.layers.PropertyFactory.circleRadius(8f),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"),
            org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(3f),
        )
        // under the car puck when it's there
        val under = org.maplibre.android.location.LocationComponentConstants.SHADOW_LAYER
        listOf(casing, line, dest).forEach { if (style.getLayer(under) != null) style.addLayerBelow(it, under) else style.addLayer(it) }
    }
    val pts = if (data == null || r == null) emptyList() else {
        val active = r.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        val start = if (active) (r.progressSeg + 1).coerceAtMost(data.points.size - 1) else 0
        buildList { if (active) r.car?.let { add(it) }; addAll(data.points.drop(start)) }
    }
    val lineGeo = org.maplibre.geojson.FeatureCollection.fromFeatures(
        if (pts.size < 2) emptyArray() else arrayOf(org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(pts.map { org.maplibre.geojson.Point.fromLngLat(it.lon, it.lat) }))),
    )
    val destGeo = org.maplibre.geojson.FeatureCollection.fromFeatures(
        data?.points?.lastOrNull()?.let { arrayOf(org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(it.lon, it.lat))) } ?: emptyArray(),
    )
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(lineSrc)?.setGeoJson(lineGeo)
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(destSrc)?.setGeoJson(destGeo)
}

/** Adds (once per style) and moves the dropped-pin marker on the MapLibre map; empty when there's no pin. */
private fun drawPin(style: Style, pin: com.ahu.tlauncher.route.Place?, density: Float) {
    val src = "tl-pin"
    if (style.getSource(src) == null) {
        style.addImage("tl-pin-icon", pinBitmap(density))
        style.addSource(org.maplibre.android.style.sources.GeoJsonSource(src))
        style.addLayer(org.maplibre.android.style.layers.SymbolLayer("tl-pin", src).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.iconImage("tl-pin-icon"),
            org.maplibre.android.style.layers.PropertyFactory.iconAnchor(org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM),
            org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
            org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
        ))
    }
    val at = pin?.latLon
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(src)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(
        if (at == null) emptyArray() else arrayOf(org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(at.lon, at.lat))),
    ))
}

private class VectorHandle(val view: MapView) : MapControls {
    var fittedRoute: Any? = null
    val bearingState = androidx.compose.runtime.mutableFloatStateOf(0f)
    override val bearing: Float get() = bearingState.floatValue
    override fun northUp() { following = false; map?.animateCamera(CameraUpdateFactory.bearingTo(0.0), 500) }
    var map: MapLibreMap? = null
    var locationActive = false
    var styleUrl = STYLE_LIGHT
    var mode = DriveCamera.Mode.PARK
    var following = true
    var lastTouch = 0L
    var speedKmh: Int? = null
    var lowPerf = false
    private var appliedMode: DriveCamera.Mode? = null

    /** Caps the map's and the car puck's frame rate in low-performance mode (no cap otherwise). */
    fun applyFps() {
        val fps = if (lowPerf) com.ahu.tlauncher.system.PerfPolicy.LOW_PERF_MAP_FPS else Int.MAX_VALUE
        view.setMaximumFps(fps)
        if (locationActive) runCatching { map?.locationComponent?.setMaxAnimationFps(fps) }
    }
    private var appliedZoom = -1.0

    override fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn(), 300) }
    override fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut(), 300) }

    /** ⊙ button: follow the car again right away, in the current mode. */
    override fun recenter() {
        following = true
        appliedMode = null
        apply()
    }

    /** Drive: heading-up, tilted, zoom by speed. Park: flat, north-up overview. */
    fun apply() {
        val m = map ?: return
        if (!locationActive || !following) return
        val lc = m.locationComponent
        if (mode == DriveCamera.Mode.DRIVE) {
            val z = DriveCamera.zoomFor(speedKmh ?: 30)
            if (appliedMode != mode) {
                lc.cameraMode = CameraMode.TRACKING_GPS
                lc.renderMode = RenderMode.GPS
                lc.tiltWhileTracking(DriveCamera.driveTilt(lowPerf), 750)
                lc.zoomWhileTracking(z, 750); appliedZoom = z
            } else if (kotlin.math.abs(z - appliedZoom) >= 0.25) {
                lc.zoomWhileTracking(z, 1200); appliedZoom = z
            }
        } else if (appliedMode != mode) {
            lc.cameraMode = CameraMode.TRACKING
            lc.renderMode = RenderMode.NORMAL
            lc.tiltWhileTracking(DriveCamera.PARK_TILT, 750)
            lc.zoomWhileTracking(DriveCamera.PARK_ZOOM, 750)
            m.animateCamera(CameraUpdateFactory.bearingTo(0.0), 750)
            appliedZoom = DriveCamera.PARK_ZOOM
        }
        appliedMode = mode
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun vectorMap(dark: Boolean, state: HomeUiState, modifier: Modifier, onLongPress: (Double, Double) -> Unit): MapControls {
    val ctx = LocalContext.current
    val current = rememberUpdatedState(state)
    val longPress = rememberUpdatedState(onLongPress)
    val handle = remember {
        MapLibre.getInstance(ctx)
        val opts = MapLibreMapOptions.createFromAttributes(ctx)
            .textureMode(false).attributionEnabled(false).logoEnabled(false).compassEnabled(false)
            .camera(CameraPosition.Builder().target(LatLng(state.locationLat ?: DEFAULT_LAT, state.locationLon ?: DEFAULT_LON)).zoom(15.0).build())
        VectorHandle(MapView(ctx, opts).apply { onCreate(null) })
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LifecycleBinding(handle.view::onStart, handle.view::onResume, handle.view::onPause, handle.view::onStop, handle.view::onDestroy)
    DisposableEffect(handle) {
        handle.view.getMapAsync { m ->
            handle.map = m; map = m
            m.addOnCameraMoveListener { handle.bearingState.floatValue = m.cameraPosition.bearing.toFloat() }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) handle.lastTouch = SystemClock.elapsedRealtime()
            }
            m.addOnMapLongClickListener { p ->
                handle.view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                longPress.value(p.latitude, p.longitude); true
            }
        }
        handle.view.addOnDidFailLoadingMapListener {
            if (handle.styleUrl == STYLE_DARK) { handle.styleUrl = STYLE_LIGHT; handle.map?.setStyle(Style.Builder().fromUri(STYLE_LIGHT)) }
        }
        onDispose { }
    }

    // Load (or switch) the style; fall back to the light style if the dark one ever fails.
    val m = map
    LaunchedEffect(m, dark, state.locationPermission) {
        if (m == null) return@LaunchedEffect
        val url = if (dark) STYLE_DARK else STYLE_LIGHT
        handle.styleUrl = url
        m.setStyle(Style.Builder().fromUri(url)) { style ->
            styleTick++
            if (state.locationPermission && !handle.locationActive) {
                runCatching {
                    val lc = m.locationComponent
                    lc.activateLocationComponent(
                        LocationComponentActivationOptions.builder(ctx, style)
                            .locationComponentOptions(
                                LocationComponentOptions.builder(ctx)
                                    .foregroundTintColor(BLUE).bearingTintColor(BLUE).accuracyAlpha(0.12f)
                                    .pulseEnabled(false).trackingGesturesManagement(true).elevation(0f)
                                    .build()
                            )
                            .useDefaultLocationEngine(true)
                            .locationEngineRequest(
                                LocationEngineRequest.Builder(1000).setFastestInterval(500)
                                    .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY).build()
                            )
                            .build()
                    )
                    lc.isLocationComponentEnabled = true
                    lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                        override fun onCameraTrackingDismissed() {
                            handle.following = false
                            handle.lastTouch = SystemClock.elapsedRealtime()
                        }
                        override fun onCameraTrackingChanged(currentMode: Int) = Unit
                    })
                    handle.locationActive = true
                    if (handle.lowPerf) handle.applyFps()
                    handle.recenter()
                }
            }
        }
    }
    // Route (free map): blue line with a darker edge from the car to the destination, driven part trimmed.
    LaunchedEffect(m, styleTick, state.route) {
        val gm = m ?: return@LaunchedEffect
        val style = gm.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val r = state.route
        val data = r?.route
        drawFreeRoute(style, r, data)
        if (data == null) { handle.fittedRoute = null; return@LaunchedEffect }
        val active = r.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        if (!active && handle.fittedRoute !== data) {
            handle.fittedRoute = data
            handle.following = false
            val b = org.maplibre.android.geometry.LatLngBounds.Builder().includes(data.points.map { LatLng(it.lat, it.lon) }).build()
            val d = handle.view.resources.displayMetrics.density
            runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b, (40 * d).toInt(), (70 * d).toInt(), (40 * d).toInt(), (180 * d).toInt()), 700) }
        }
        if (active && handle.fittedRoute != null) { handle.fittedRoute = null; handle.recenter() }
    }
    // Dropped pin (free map).
    LaunchedEffect(m, styleTick, state.pin) {
        val style = m?.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        drawPin(style, state.pin, handle.view.resources.displayMetrics.density)
    }
    // Low-performance mode: at most 30 fps, and a flat map while driving.
    val lowPerf = state.lowPerf
    LaunchedEffect(handle, lowPerf) {
        if (handle.lowPerf != lowPerf) { handle.lowPerf = lowPerf; handle.applyFps(); handle.recenter() }
    }
    // Speed decides park vs. drive (with hysteresis); starting to drive always resumes following.
    // Watched here rather than read in composition, so a new speed doesn't recompose the map.
    LaunchedEffect(handle) {
        snapshotFlow { current.value.displaySpeedKmh }.collect { speed ->
            handle.speedKmh = speed
            val next = DriveCamera.nextMode(handle.mode, speed)
            if (next != handle.mode) {
                handle.mode = next
                if (next == DriveCamera.Mode.DRIVE) handle.following = true
            }
            handle.apply()
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
    AndroidView(factory = { handle.view }, modifier = modifier.clipToBounds())
    return handle
}

private const val BLUE = 0xFF3E6AE1.toInt()

/** Night look for raster OSM tiles: invert, then dim and cool slightly. */
private val DARK_TILES = android.graphics.ColorMatrixColorFilter(
    floatArrayOf(
        -0.75f, 0f, 0f, 0f, 205f,
        0f, -0.75f, 0f, 0f, 210f,
        0f, 0f, -0.75f, 0f, 220f,
        0f, 0f, 0f, 1f, 0f,
    )
)

// ---------------------------------------------------------------- osmdroid (raster fallback)

private class RasterHandle(val map: org.osmdroid.views.MapView, val me: MyLocationNewOverlay?) : MapControls {
    override val bearing: Float get() = -map.mapOrientation
    override fun northUp() { map.mapOrientation = 0f }
    override fun zoomIn() { map.controller.zoomIn() }
    override fun zoomOut() { map.controller.zoomOut() }
    override fun recenter() { me?.let { it.enableFollowLocation(); it.myLocation?.let { p -> map.controller.animateTo(p) } } }

    var mode = DriveCamera.Mode.PARK
    var unfollowedAt = 0L
    var pinMarker: org.osmdroid.views.overlay.Marker? = null

    /** Drive: heading-up + zoom by speed. Park: north-up at street zoom. */
    fun apply(speedKmh: Int?) {
        if (mode == DriveCamera.Mode.DRIVE) {
            me?.lastFix?.takeIf { it.hasBearing() }?.let { map.mapOrientation = -it.bearing }
            map.controller.zoomTo(DriveCamera.zoomFor(speedKmh ?: 30), 800L)
        } else {
            map.mapOrientation = 0f
            map.controller.zoomTo(DriveCamera.PARK_ZOOM, 800L)
        }
    }
}

@Composable
private fun rasterMap(dark: Boolean, state: HomeUiState, modifier: Modifier, onLongPress: (Double, Double) -> Unit): MapControls {
    val ctx = LocalContext.current
    val longPress = rememberUpdatedState(onLongPress)
    val handle = remember(state.locationPermission) {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = ctx.cacheDir.resolve("osmdroid")
            osmdroidTileCache = ctx.cacheDir.resolve("osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 200L * 1024 * 1024
        }
        val map = org.osmdroid.views.MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(state.locationLat ?: DEFAULT_LAT, state.locationLon ?: DEFAULT_LON))
        }
        val me = if (state.locationPermission) MyLocationNewOverlay(GpsMyLocationProvider(ctx), map).apply {
            setDirectionIcon(arrowBitmap(ctx.resources.displayMetrics.density))
            setPersonIcon(dotBitmap(ctx.resources.displayMetrics.density))
            setPersonAnchor(0.5f, 0.5f); setDirectionAnchor(0.5f, 0.5f)
            enableMyLocation(); enableFollowLocation()
            map.overlays.add(this)
        } else null
        // Long-press drops a pin.
        map.overlays.add(0, org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p ?: return false
                map.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                longPress.value(p.latitude, p.longitude); return true
            }
        }))
        RasterHandle(map, me)
    }
    // Dropped pin (raster map).
    LaunchedEffect(handle, state.pin) {
        handle.pinMarker?.let { handle.map.overlays.remove(it) }; handle.pinMarker = null
        state.pin?.latLon?.let { at ->
            handle.pinMarker = org.osmdroid.views.overlay.Marker(handle.map).apply {
                position = GeoPoint(at.lat, at.lon)
                icon = android.graphics.drawable.BitmapDrawable(ctx.resources, pinBitmap(ctx.resources.displayMetrics.density))
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> true } // the card shows the details
            }.also { handle.map.overlays.add(it) }
        }
        handle.map.invalidate()
    }
    LifecycleBinding({}, handle.map::onResume, handle.map::onPause, {}, { handle.me?.disableMyLocation(); handle.map.onDetach() })
    val current = rememberUpdatedState(state)
    LaunchedEffect(handle) {
        snapshotFlow { current.value.displaySpeedKmh }.collect { speed ->
            val next = DriveCamera.nextMode(handle.mode, speed)
            if (next != handle.mode && next == DriveCamera.Mode.DRIVE) handle.recenter()
            handle.mode = next
            handle.apply(speed)
        }
    }
    // While driving, re-follow 10 s after a touch stopped following.
    LaunchedEffect(handle) {
        while (true) {
            delay(1000)
            val me = handle.me ?: continue
            if (me.isFollowLocationEnabled) { handle.unfollowedAt = 0L; continue }
            val now = SystemClock.elapsedRealtime()
            if (handle.unfollowedAt == 0L) handle.unfollowedAt = now
            else if (handle.mode == DriveCamera.Mode.DRIVE && now - handle.unfollowedAt >= DriveCamera.REFOLLOW_MS) handle.recenter()
        }
    }
    androidx.compose.runtime.key(handle) {
        AndroidView(factory = { handle.map }, modifier = modifier.clipToBounds(), update = { m ->
            m.overlayManager.tilesOverlay.setColorFilter(if (dark) DARK_TILES else null)
        })
    }
    return handle
}

private fun dotBitmap(d: Float): Bitmap {
    val s = (24 * d).toInt()
    return Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(s / 2f, s / 2f, s / 2.2f, p)
        p.color = 0xFF3E6AE1.toInt(); c.drawCircle(s / 2f, s / 2f, s / 3.2f, p)
    }
}

private fun arrowBitmap(d: Float): Bitmap {
    val s = (34 * d).toInt()
    return Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(s / 2f, s * 0.08f); lineTo(s * 0.86f, s * 0.9f); lineTo(s / 2f, s * 0.7f); lineTo(s * 0.14f, s * 0.9f); close()
        }
        p.color = 0xFFFFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, p)
        p.color = 0xFF3E6AE1.toInt(); p.style = Paint.Style.FILL; c.drawPath(path, p)
    }
}

/** Stylised street grid used in previews/screenshots (no network). */
@Composable
private fun MapPlaceholder(modifier: Modifier) {
    val road = T.colors.text.copy(alpha = 0.10f)
    val major = T.colors.text.copy(alpha = 0.18f)
    val accent = T.colors.accent
    ComposeCanvas(modifier) {
        val step = 46.dp.toPx()
        var x = -step / 2; while (x < size.width) { drawLine(road, Offset(x, 0f), Offset(x + 60f, size.height), 2f); x += step }
        var y = step / 3; while (y < size.height) { drawLine(road, Offset(0f, y), Offset(size.width, y - 40f), 2f); y += step }
        drawLine(major, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.38f), 10f)
        drawLine(major, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.55f, size.height), 8f)
        drawCircle(androidx.compose.ui.graphics.Color.White, 11.dp.toPx(), Offset(size.width * 0.46f, size.height * 0.5f))
        drawCircle(accent, 8.dp.toPx(), Offset(size.width * 0.46f, size.height * 0.5f))
    }
}

@Composable
fun NavCard(nav: NavCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val i = nav.info
    Column(
        modifier.widthIn(min = 280.dp, max = 340.dp).clip(TShapes.surface).background(T.colors.frost)
            .pressable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val icon = nav.icon
                if (icon != null) {
                    Image(
                        icon.asImageBitmap(), null, modifier = Modifier.size(48.dp),
                        colorFilter = if (i.packageName == NavParser.MAPS) ColorFilter.tint(T.colors.text) else null,
                    )
                } else {
                    Icon(Icons.Rounded.Navigation, null, tint = T.colors.text, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                if (i.distance != null) Text(i.distance, style = TType.display, color = T.colors.text, maxLines = 1)
                Text(i.instruction, style = TType.button, color = T.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (i.detail != null) Text(i.detail, style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.height(1.dp).widthIn(min = 280.dp, max = 340.dp).background(T.colors.divider))
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(i.eta ?: "Navigating", style = TType.label, color = T.colors.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(i.appLabel, style = TType.label, color = T.colors.muted)
        }
    }
}
