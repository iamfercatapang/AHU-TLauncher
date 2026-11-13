package com.ahu.tlauncher.ui.home

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
private interface MapControls {
    fun zoomIn()
    fun zoomOut()
    fun recenter()
}

@Composable
fun MapPane(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier, controlsOnLeft: Boolean = false) {
    val dark = T.colors.isDark
    val preview = LocalInspectionMode.current
    val ctx = LocalContext.current
    val vectorCapable = remember { supportsGles3(ctx) }

    Box(modifier.clipToBounds().background(T.colors.appBg)) {
        val controls: MapControls? = when {
            preview -> { MapPlaceholder(Modifier.fillMaxSize()); null }
            vectorCapable -> vectorMap(dark, state, Modifier.fillMaxSize())
            else -> rasterMap(dark, state, Modifier.fillMaxSize())
        }

        // top-left: live turn-by-turn card while Google Maps / Waze is navigating
        state.nav?.let { nav ->
            Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = STATUS_H + 4.dp)) { NavCard(nav, onClick = actions::openNavCard) }
        }

        // right edge: map controls
        TCard(
            if (controlsOnLeft) Modifier.align(Alignment.CenterStart).padding(start = 12.dp) else Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            color = T.colors.frost, shape = TShapes.button,
        ) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButtonT(Icons.Rounded.Add, "Zoom in", size = 40.dp, iconSize = 22.dp) { controls?.zoomIn() }
                IconButtonT(Icons.Rounded.Remove, "Zoom out", size = 40.dp, iconSize = 22.dp) { controls?.zoomOut() }
                IconButtonT(Icons.Rounded.MyLocation, "Follow my location", size = 40.dp, iconSize = 20.dp) {
                    if (!state.locationPermission) actions.requestLocation()
                    controls?.recenter()
                }
            }
        }

        Text(
            if (vectorCapable) ATTRIBUTION else "© OpenStreetMap contributors", style = TType.caption, color = T.colors.muted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}

private fun supportsGles3(ctx: Context): Boolean = runCatching {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}.getOrDefault(false)

/** Forward the screen lifecycle to a classic Android map view. */
@Composable
private fun LifecycleBinding(onStart: () -> Unit, onResume: () -> Unit, onPause: () -> Unit, onStop: () -> Unit, onDestroy: () -> Unit) {
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

private class VectorHandle(val view: MapView) : MapControls {
    var map: MapLibreMap? = null
    var locationActive = false
    var styleUrl = STYLE_LIGHT
    var mode = DriveCamera.Mode.PARK
    var following = true
    var lastTouch = 0L
    var speedKmh: Int? = null
    private var appliedMode: DriveCamera.Mode? = null
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
                lc.tiltWhileTracking(DriveCamera.DRIVE_TILT, 750)
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
private fun vectorMap(dark: Boolean, state: HomeUiState, modifier: Modifier): MapControls {
    val speed = state.displaySpeedKmh
    val ctx = LocalContext.current
    val handle = remember {
        MapLibre.getInstance(ctx)
        val opts = MapLibreMapOptions.createFromAttributes(ctx)
            .textureMode(false).attributionEnabled(false).logoEnabled(false).compassEnabled(false)
            .camera(CameraPosition.Builder().target(LatLng(state.locationLat ?: DEFAULT_LAT, state.locationLon ?: DEFAULT_LON)).zoom(15.0).build())
        VectorHandle(MapView(ctx, opts).apply { onCreate(null) })
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    LifecycleBinding(handle.view::onStart, handle.view::onResume, handle.view::onPause, handle.view::onStop, handle.view::onDestroy)
    DisposableEffect(handle) {
        handle.view.getMapAsync { m ->
            handle.map = m; map = m
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) handle.lastTouch = SystemClock.elapsedRealtime()
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
                    handle.recenter()
                }
            }
        }
    }
    // Speed decides park vs. drive (with hysteresis); starting to drive always resumes following.
    LaunchedEffect(speed) {
        handle.speedKmh = speed
        val next = DriveCamera.nextMode(handle.mode, speed)
        if (next != handle.mode) {
            handle.mode = next
            if (next == DriveCamera.Mode.DRIVE) handle.following = true
        }
        handle.apply()
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
    override fun zoomIn() { map.controller.zoomIn() }
    override fun zoomOut() { map.controller.zoomOut() }
    override fun recenter() { me?.let { it.enableFollowLocation(); it.myLocation?.let { p -> map.controller.animateTo(p) } } }

    var mode = DriveCamera.Mode.PARK
    var unfollowedAt = 0L

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
private fun rasterMap(dark: Boolean, state: HomeUiState, modifier: Modifier): MapControls {
    val ctx = LocalContext.current
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
        RasterHandle(map, me)
    }
    LifecycleBinding({}, handle.map::onResume, handle.map::onPause, {}, { handle.me?.disableMyLocation(); handle.map.onDetach() })
    val speed = state.displaySpeedKmh
    LaunchedEffect(handle, speed) {
        val next = DriveCamera.nextMode(handle.mode, speed)
        if (next != handle.mode && next == DriveCamera.Mode.DRIVE) handle.recenter()
        handle.mode = next
        handle.apply(speed)
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
