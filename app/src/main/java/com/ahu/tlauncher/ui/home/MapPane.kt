package com.ahu.tlauncher.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.sp
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
// Same style addresses as the offline areas, so saved areas are found when there's no connection.
private const val STYLE_LIGHT = com.ahu.tlauncher.map.STYLE_LIGHT
private const val STYLE_DARK = com.ahu.tlauncher.map.STYLE_DARK
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
    /** Ground metres per dp at the map's centre, for the scale bar; null when unknown. */
    val metersPerDp: Float? get() = null
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
        RouteOverlays(state, actions, Modifier.fillMaxSize(), controlsOnLeft)

        // top-left: live turn-by-turn card while Google Maps / Waze is navigating; otherwise the Navigate pill
        val side = if (controlsOnLeft) Alignment.TopEnd else Alignment.TopStart
        val nav = state.nav
        val freeTurn = state.route?.takeIf { it.guided && it.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE && it.turn != null }
        if (freeTurn != null) {
            FreeTurnCard(state, freeTurn, Modifier.align(side).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp))
        } else if (nav != null && state.route?.phase != com.ahu.tlauncher.route.RoutePhase.PREVIEW) {
            Box(Modifier.align(side).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp)) { NavCard(nav, onClick = actions::openNavCard) }
        } else if (state.route == null && state.picking != null) {
            PickSpotBanner(state.picking, actions::cancelPicking, Modifier.align(side).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp))
        } else if (state.route == null) {
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
        // bottom, on the cards' side: scale bar (hidden under the trip and pin cards)
        val scale = controls?.metersPerDp ?: if (preview) 4.5f else null
        if (scale != null && state.route?.phase != com.ahu.tlauncher.route.RoutePhase.ACTIVE && state.pin == null) {
            ScaleBar(
                scale, state.settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH,
                Modifier.align(if (controlsOnLeft) Alignment.BottomEnd else Alignment.BottomStart)
                    // Google's logo sits in the bottom-left corner
                    .padding(start = if (useGoogle && !controlsOnLeft) 84.dp else 14.dp, end = 14.dp, bottom = if (!controlsOnLeft || useGoogle) 12.dp else 24.dp),
            )
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

/** Compass: an "N" under a small red north marker, turning with the map (prototype style). Tap to turn north-up. */
@Composable
private fun Compass(bearing: Float, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(T.colors.chrome).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        val ink = if (T.colors.isDark) Color.White else T.colors.text
        Box(Modifier.size(34.dp).graphicsLayer { rotationZ = -bearing }, contentAlignment = Alignment.Center) {
            ComposeCanvas(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(6.dp, 5.dp)) {
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, TRAFFIC_JAM)
            }
            Text("N", style = TType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = ink, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Map scale: a round length ("500 m", "1 km", "0.5 mi") over a thin bar of that length. */
@Composable
private fun ScaleBar(metersPerDp: Float, mph: Boolean, modifier: Modifier = Modifier) {
    val steps = if (mph) listOf(50 to "50 ft", 100 to "100 ft", 200 to "200 ft", 500 to "500 ft", 1000 to "1000 ft").map { (ft, l) -> ft * 0.3048f to l } +
        listOf(0.25f to "¼ mi", 0.5f to "½ mi", 1f to "1 mi", 2f to "2 mi", 5f to "5 mi", 10f to "10 mi", 20f to "20 mi", 50f to "50 mi").map { (mi, l) -> mi * 1609.34f to l }
    else listOf(10, 20, 50, 100, 200, 300, 500, 1000, 2000, 3000, 5000, 10_000, 20_000, 50_000, 100_000).map { m -> m.toFloat() to if (m >= 1000) "${m / 1000} km" else "$m m" }
    val (meters, label) = steps.lastOrNull { it.first / metersPerDp <= 80f } ?: steps.first()
    val widthDp = (meters / metersPerDp).coerceIn(12f, 120f)
    val ink = if (T.colors.isDark) Color.White.copy(alpha = 0.7f) else T.colors.body
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = TType.caption.copy(fontSize = 10.sp), color = ink)
        ComposeCanvas(Modifier.width(widthDp.dp).height(5.dp)) {
            val w = 1.2.dp.toPx()
            drawLine(ink, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            drawLine(ink, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
            drawLine(ink, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
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

internal fun supportsGles3(ctx: Context): Boolean = runCatching {
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

/** Adds (once per style) and updates the route line, the other route choices and the destination flag on the MapLibre map. */
private fun drawFreeRoute(style: Style, r: com.ahu.tlauncher.route.RouteState?, data: com.ahu.tlauncher.route.RouteData?, density: Float) {
    val lineSrc = "tl-route"; val altSrc = "tl-route-alts"; val destSrc = "tl-route-dest"
    if (style.getSource(lineSrc) == null) {
        listOf(lineSrc, altSrc, destSrc).forEach { style.addSource(org.maplibre.android.style.sources.GeoJsonSource(it)) }
        style.addImage("tl-dest-flag", destinationBitmap(density))
        fun lineLayer(id: String, src: String, color: androidx.compose.ui.graphics.Color, width: Float) =
            org.maplibre.android.style.layers.LineLayer(id, src).withProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(color.toArgb()),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(width),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
            )
        val dest = org.maplibre.android.style.layers.SymbolLayer("tl-route-dest", destSrc).withProperties(
            org.maplibre.android.style.layers.PropertyFactory.iconImage("tl-dest-flag"),
            org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
            org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
        )
        // under the car puck when it's there
        val under = org.maplibre.android.location.LocationComponentConstants.SHADOW_LAYER
        listOf(
            lineLayer("tl-route-alt-casing", altSrc, ROUTE_ALT_CASING, 9f), lineLayer("tl-route-alt-line", altSrc, ROUTE_ALT, 5.5f),
            lineLayer("tl-route-casing", lineSrc, ROUTE_LINE_CASING, 10f), lineLayer("tl-route-line", lineSrc, ROUTE_LINE, 6.5f), dest,
        ).forEach { if (style.getLayer(under) != null) style.addLayerBelow(it, under) else style.addLayer(it) }
    }
    fun line(points: List<com.ahu.tlauncher.route.LatLon>) =
        org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(points.map { org.maplibre.geojson.Point.fromLngLat(it.lon, it.lat) }))
    val pts = if (data == null || r == null) emptyList() else {
        val active = r.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        val start = if (active) (r.progressSeg + 1).coerceAtMost(data.points.size - 1) else 0
        buildList { if (active) r.car?.let { add(it) }; addAll(data.points.drop(start)) }
    }
    val alts = r?.takeIf { it.phase == com.ahu.tlauncher.route.RoutePhase.PREVIEW }?.alternatives
        ?.filterIndexed { i, _ -> i != r.selected }?.filter { it.points.size >= 2 }.orEmpty()
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(lineSrc)?.setGeoJson(
        org.maplibre.geojson.FeatureCollection.fromFeatures(if (pts.size < 2) emptyArray() else arrayOf(line(pts))))
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(altSrc)?.setGeoJson(
        org.maplibre.geojson.FeatureCollection.fromFeatures(alts.map { line(it.points) }.toTypedArray()))
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(destSrc)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(
        data?.points?.lastOrNull()?.let { arrayOf(org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(it.lon, it.lat))) } ?: emptyArray(),
    ))
}

/**
 * Destination marker: a white tile with a flag above a blue dot (TLauncher's drawing of the prototype's marker).
 * The dot is the bitmap's centre, so anchor it at the centre.
 */
internal fun destinationBitmap(d: Float): Bitmap {
    val box = 26 * d; val gap = 5 * d; val dot = 7 * d
    val w = (box + 4 * d).toInt(); val half = box + gap + dot; val h = (2 * half).toInt()
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = w / 2f; val cy = h / 2f
        val top = cy - half
        p.color = 0x40000000; c.drawRoundRect(cx - box / 2, top + d, cx + box / 2, top + box + d, 6 * d, 6 * d, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(cx - box / 2, top, cx + box / 2, top + box, 6 * d, 6 * d, p)
        // flag: pole and pennant
        p.color = 0xFF171A20.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f * d; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        val fx = cx - 6 * d; val fy = top + 6 * d
        c.drawLine(fx, fy, fx, fy + 14 * d, p)
        c.drawPath(Path().apply { moveTo(fx, fy); lineTo(fx + 12 * d, fy + 1 * d); lineTo(fx + 9 * d, fy + 4.5f * d); lineTo(fx + 12 * d, fy + 8 * d); lineTo(fx, fy + 8 * d); close() }, p)
        // stem from tile to dot
        p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 2 * d; c.drawLine(cx, top + box, cx, cy - dot, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, dot, p)
        p.color = ROUTE_LINE.toArgb(); c.drawCircle(cx, cy, dot - 2.5f * d, p)
    }
}

/** Ground metres per dp: [tile] is the world's width in dp at zoom 0 (MapLibre 512, Google 256). */
internal fun metersPerDp(zoom: Double, lat: Double, tile: Int): Float =
    (40_075_016.686 * kotlin.math.cos(Math.toRadians(lat)) / (tile * Math.pow(2.0, zoom))).toFloat()

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
    val scaleState = androidx.compose.runtime.mutableFloatStateOf(0f)
    override val metersPerDp: Float? get() = scaleState.floatValue.takeIf { it > 0f }
    override fun northUp() { following = false; map?.animateCamera(CameraUpdateFactory.bearingTo(0.0), 500) }
    var map: MapLibreMap? = null
    var locationActive = false
    var styleUrl = STYLE_LIGHT
    var mode = DriveCamera.Mode.PARK
    var following = true
    var lastTouch = 0L
    var speedKmh: Int? = null
    var lowPerf = false
    /** A route is being followed (TLauncher's guidance, or Google Maps / Waze navigating), and which side its cards are on. */
    var navigating = false
    var cardsRight = false
    private var appliedDrive: Boolean? = null
    private var appliedPad: IntArray? = null

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
        appliedDrive = null
        appliedPad = null
        apply()
    }

    /**
     * Drive (or navigating): flat, heading-up, close zoom by speed, the car low on the map (and away from the
     * route cards while navigating). Park: flat, north-up overview with the car in the middle.
     */
    fun apply() {
        val m = map ?: return
        if (!locationActive || !following) return
        val lc = m.locationComponent
        val drive = DriveCamera.driveView(mode, navigating)
        if (drive) {
            val z = DriveCamera.zoomFor(speedKmh ?: 30)
            if (appliedDrive != true) {
                lc.cameraMode = CameraMode.TRACKING_GPS
                lc.renderMode = RenderMode.GPS
                lc.tiltWhileTracking(DriveCamera.driveTilt(lowPerf), 750)
                lc.zoomWhileTracking(z, 750); appliedZoom = z
            } else if (kotlin.math.abs(z - appliedZoom) >= 0.1) {
                // Small, slow steps as the speed changes, so the zoom drifts instead of jumping.
                lc.zoomWhileTracking(z, 2000); appliedZoom = z
            }
        } else if (appliedDrive != false) {
            lc.cameraMode = CameraMode.TRACKING
            lc.renderMode = RenderMode.GPS // the arrow keeps pointing along the course
            lc.tiltWhileTracking(DriveCamera.PARK_TILT, 750)
            lc.zoomWhileTracking(DriveCamera.PARK_ZOOM, 750)
            m.animateCamera(CameraUpdateFactory.bearingTo(0.0), 750)
            appliedZoom = DriveCamera.PARK_ZOOM
        }
        appliedDrive = drive
        val pad = if (drive && view.width > 0) DriveCamera.padding(view.width, view.height, DriveCamera.carAnchor(navigating, cardsRight)) else IntArray(4)
        if (!pad.contentEquals(appliedPad)) {
            // Camera padding: the followed car sits in the middle of what's left. Set on the camera itself
            // (paddingWhileTracking alone didn't move the view); that ends tracking, so it is turned back on.
            m.moveCamera(CameraUpdateFactory.paddingTo(pad[0].toDouble(), pad[1].toDouble(), pad[2].toDouble(), pad[3].toDouble()))
            lc.cameraMode = if (drive) CameraMode.TRACKING_GPS else CameraMode.TRACKING
            following = true
            appliedPad = pad
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun vectorMap(dark: Boolean, state: HomeUiState, modifier: Modifier, onLongPress: (Double, Double) -> Unit): MapControls {
    val ctx = LocalContext.current
    val current = rememberUpdatedState(state)
    val longPress = rememberUpdatedState(onLongPress)
    val handle = remember {
        com.ahu.tlauncher.map.OfflineMaps.setUp(ctx)
        val opts = MapLibreMapOptions.createFromAttributes(ctx)
            .textureMode(false).attributionEnabled(false).logoEnabled(false).compassEnabled(false)
            .camera(CameraPosition.Builder().target(LatLng(state.locationLat ?: DEFAULT_LAT, state.locationLon ?: DEFAULT_LON)).zoom(DriveCamera.PARK_ZOOM).build())
        VectorHandle(MapView(ctx, opts).apply { onCreate(null) })
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LifecycleBinding(handle.view::onStart, handle.view::onResume, handle.view::onPause, handle.view::onStop, handle.view::onDestroy)
    DisposableEffect(handle) {
        handle.view.getMapAsync { m ->
            handle.map = m; map = m
            val onCamera = {
                val cp = m.cameraPosition
                handle.bearingState.floatValue = cp.bearing.toFloat()
                cp.target?.let { handle.scaleState.floatValue = metersPerDp(cp.zoom, it.latitude, 512) }
            }
            onCamera()
            m.addOnCameraMoveListener { onCamera() }
            m.addOnCameraIdleListener { onCamera() }
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
                                    // the prototype's red arrow instead of MapLibre's blue dot
                                    .foregroundDrawable(com.ahu.tlauncher.R.drawable.puck_arrow).gpsDrawable(com.ahu.tlauncher.R.drawable.puck_arrow)
                                    .foregroundDrawableStale(com.ahu.tlauncher.R.drawable.puck_arrow)
                                    .backgroundDrawable(com.ahu.tlauncher.R.drawable.puck_none).backgroundDrawableStale(com.ahu.tlauncher.R.drawable.puck_none)
                                    .bearingDrawable(com.ahu.tlauncher.R.drawable.puck_none)
                                    .accuracyColor(ROUTE_LINE.toArgb()).accuracyAlpha(0.10f)
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
        val d = handle.view.resources.displayMetrics.density
        drawFreeRoute(style, r, data, d)
        if (data == null) { handle.fittedRoute = null; return@LaunchedEffect }
        val active = r.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE
        if (!active && handle.fittedRoute !== data) {
            handle.fittedRoute = data
            handle.following = false
            val all = r.alternatives.ifEmpty { listOf(data) }.flatMap { it.points }
            val b = org.maplibre.android.geometry.LatLngBounds.Builder().includes(all.map { LatLng(it.lat, it.lon) }).build()
            // Leave room for the route card (top, on the side away from the map buttons) and the buttons.
            val card = ((NAV_CARD_W.value + 36) * d).toInt(); val edge = (70 * d).toInt()
            val cardsRight = state.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT
            runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b, if (cardsRight) edge else card, (60 * d).toInt(), if (cardsRight) card else edge, (40 * d).toInt()), 700) }
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
    // Navigating (own route guidance, or Google Maps / Waze): keep the drive view, car away from the route cards.
    LaunchedEffect(handle) {
        snapshotFlow { current.value.let { it.isNavigating to (it.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT) } }.collect { (nav, right) ->
            handle.navigating = nav; handle.cardsRight = right
            handle.apply()
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
    AndroidView(factory = { handle.view }, modifier = modifier.clipToBounds())
    return handle
}


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
    var navigating = false
    var cardsRight = false
    var speedKmh: Int? = null

    /** Drive (or navigating): heading-up, zoom by speed, car low (away from the route cards). Park: north-up at street zoom. */
    fun apply() {
        if (DriveCamera.driveView(mode, navigating)) {
            me?.lastFix?.takeIf { it.hasBearing() }?.let { map.mapOrientation = -it.bearing }
            map.controller.zoomTo(DriveCamera.zoomFor(speedKmh ?: 30) + DriveCamera.TILE_256_OFFSET, 800L)
            val (ax, ay) = DriveCamera.carAnchor(navigating, cardsRight)
            map.setMapCenterOffset(((ax - 0.5f) * map.width).toInt(), ((ay - 0.5f) * map.height).toInt())
        } else {
            map.mapOrientation = 0f
            map.controller.zoomTo(DriveCamera.PARK_ZOOM + DriveCamera.TILE_256_OFFSET, 800L)
            map.setMapCenterOffset(0, 0)
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
            controller.setZoom(DriveCamera.PARK_ZOOM + DriveCamera.TILE_256_OFFSET)
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
            handle.speedKmh = speed
            handle.apply()
        }
    }
    LaunchedEffect(handle) {
        snapshotFlow { current.value.let { it.isNavigating to (it.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT) } }.collect { (nav, right) ->
            handle.navigating = nav; handle.cardsRight = right
            handle.apply()
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
            else if (DriveCamera.driveView(handle.mode, handle.navigating) && now - handle.unfollowedAt >= DriveCamera.REFOLLOW_MS) handle.recenter()
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
        p.color = 0xFFE5484D.toInt(); c.drawCircle(s / 2f, s / 2f, s / 3.2f, p)
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
        p.color = 0xFFE5484D.toInt(); p.style = Paint.Style.FILL; c.drawPath(path, p)
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

/** Google Maps / Waze's next turn, read from their notification, in the same card as TLauncher's own guidance. */
@Composable
fun NavCard(nav: NavCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val i = nav.info
    val c = navColors
    TurnCardFrame(
        icon = {
            val icon = nav.icon
            if (icon != null) Image(
                icon.asImageBitmap(), null, modifier = Modifier.size(44.dp),
                colorFilter = if (i.packageName == NavParser.MAPS) ColorFilter.tint(c.text) else null,
            ) else Icon(Icons.Rounded.Navigation, null, tint = c.text, modifier = Modifier.size(36.dp))
        },
        distance = i.distance,
        instruction = i.instruction,
        footer = i.detail ?: i.eta ?: "Navigating",
        footerRight = i.appLabel,
        modifier = modifier,
        onClick = onClick,
    )
}
