package com.ahu.tlauncher.ui.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.ahu.tlauncher.R
import com.ahu.tlauncher.data.SpeedUnit
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.obd.ObdDisplay
import com.ahu.tlauncher.obd.ObdStatus
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.LocalLowPerf
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Studio backdrop behind the car: soft vertical fade towards a "floor". */
@Composable
internal fun stageBrush(): Brush = if (T.colors.isDark) {
    // Prototype: near-black side panel.
    Brush.verticalGradient(0f to Color(0xFF0E0E0E), 1f to Color(0xFF0E0E0E))
} else {
    Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.6f to Color(0xFFF1F1F1), 0.75f to Color(0xFFE6E6E6), 1f to Color(0xFFF7F7F7))
}

/**
 * The car "stage": speed, warning lights, the car with reflection and data callouts,
 * and the music / navigate cards. Works full-width or as the left third next to the map.
 */
@Composable
fun CarStage(
    state: HomeUiState,
    actions: HomeActions,
    expanded: Boolean,
    onToggleMap: () -> Unit,
    modifier: Modifier = Modifier,
    handleOnLeft: Boolean = false,
    /** False when the caller paints [stageBrush] itself (the home screen sweeps it with the panel edge). */
    drawBackground: Boolean = true,
    /** Swiping the panel between split and full screen (the home screen's gesture); not on the cards. */
    panelSwipe: Modifier = Modifier,
    /** 0 = split view, 1 = full screen, in between while swiping or sweeping. Read at draw/layout time. */
    progress: () -> Float = { if (expanded) 1f else 0f },
    /**
     * How much smaller each part is drawn (read at draw time). The caller lays this panel out 1 / scale
     * times wider than it shows; every part shrinks toward the panel's left edge and its own anchor height.
     */
    contentScale: () -> Float = { 1f },
) {
    val dark = T.colors.isDark
    val prog = androidx.compose.runtime.rememberUpdatedState(progress)
    // Discrete choices flip half-way; the continuous ones read the progress directly.
    val wide by remember { derivedStateOf { prog.value() >= 0.5f } }
    val showPager by remember { derivedStateOf { prog.value() < 1f } }
    val showRow by remember { derivedStateOf { prog.value() > 0f } }
    // 3D car when chosen and the head unit can draw it, behind the whole panel like the prototype's canvas.
    // The photos show until its first frames are on screen (and stay if it can't start); it starts a moment
    // after the dashboard so start-up isn't slower.
    val ctx = LocalContext.current
    val preview = LocalInspectionMode.current
    val car3d = remember { Car3dState() }
    val use3d = !preview && state.settings.car3d && !car3d.failed && remember(state.settings.car3d) { Car3d.available(ctx) }
    var start3d by remember { mutableStateOf(false) }
    LaunchedEffect(use3d) { if (use3d) { delay(700); start3d = true } else { start3d = false; car3d.ready = false } }
    val shown3d = animateFloatAsState(if (use3d && car3d.ready) 1f else 0f, tween(450), label = "car3d")
    // Driving: the 3D car glides to Tesla's chase view (not with the photos, nor in low-performance mode).
    val drive = rememberDriveBlend(state, enabled = use3d && !LocalLowPerf.current)
    var stageW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(
        modifier.then(panelSwipe).onSizeChanged { stageW = it.width }.then(if (drawBackground) Modifier.background(stageBrush()) else Modifier).clipToBounds().drawBehind {
            // Night: a soft pool of light behind the car, like the prototype's lit stage (centred on the visible width).
            val vw = size.width * contentScale()
            val r = kotlin.math.min(vw, size.height) * 0.55f
            if (dark) drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), center = Offset(vw / 2, size.height * 0.5f), radius = r),
                radius = r, center = Offset(vw / 2, size.height * 0.5f),
            )
        },
    ) {
        if (use3d && start3d) Car3dSlot(car3d, state, progress, contentScale, { stageW }, drive, Modifier.matchParentSize().graphicsLayer {
            // Never fully transparent: a view that isn't drawn never takes the renderer's frames, which stalls it.
            alpha = shown3d.value.coerceAtLeast(0.004f)
        })
        Column(Modifier.fillMaxSize().padding(top = STATUS_H)) {
            // Each part shrinks around its own anchor, so it stays at its full-screen height while the panel glides.
            SpeedBlock(state, Modifier.fillMaxWidth().shrinkInPanel(contentScale, pivotY = 0.5f))
            CarHero(state, Modifier.fillMaxWidth().weight(1f), progress, contentScale, use3d, { shown3d.value }, leaders = { 1f - drive })
        }
        Telltales(state, Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = STATUS_H + 8.dp).shrinkInPanel(contentScale, pivotY = 0f))
        // Lights-on tell-tale sits at the panel's top-right corner, in the status row (as in the prototype).
        if (state.lights.on) Box(Modifier.matchParentSize().shrinkInPanel(contentScale, pivotY = 0f)) {
            Icon(
                com.ahu.tlauncher.ui.components.LowBeamIcon, "Headlights on", tint = TELL_GREEN,
                modifier = Modifier.align(if (handleOnLeft || wide) Alignment.TopStart else Alignment.TopEnd)
                    .padding(start = if (wide) 150.dp else 18.dp, end = 18.dp, top = 9.dp).size(22.dp),
            )
        }

        // bottom cards overlap the reflection, as in the reference layout; they shrink toward the bottom edge
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().shrinkInPanel(contentScale, pivotY = 1f), contentAlignment = Alignment.BottomCenter) { Box(
            Modifier
                // Sideways swipes on the cards stay with the cards (the pager, or nothing) instead of moving the panel.
                .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume() } }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Split view's pager (one card a page) fades out as full screen's pager (music and Navigate side by side
            // on the first page) fades in, with the panel's progress.
            if (showPager) Box(Modifier.graphicsLayer { alpha = 1f - prog.value() }) { CardPager(state, actions, wide = false) }
            if (showRow) Box(Modifier.widthIn(max = 660.dp).graphicsLayer { alpha = prog.value() }) { CardPager(state, actions, wide = true) }
        } }

        // Handle on the panel edge: tap to toggle the map (dragging it, like anywhere on the panel, swipes the panel).
        Box(Modifier.matchParentSize().shrinkInPanel(contentScale, pivotY = 0.5f)) {
            Box(
                Modifier.align(if (handleOnLeft) Alignment.CenterStart else Alignment.CenterEnd).width(28.dp).height(120.dp)
                    .pressable(onClick = onToggleMap),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(4.dp).height(56.dp).clip(CircleShape).background(T.colors.secondary.copy(alpha = 0.5f)))
            }
        }
    }
}

/** Reads the panel's progress and visible width itself, so only the 3D layer recomposes while it glides. */
@Composable
private fun Car3dSlot(car: Car3dState, state: HomeUiState, progress: () -> Float, contentScale: () -> Float, stageW: () -> Int, drive: Float, modifier: Modifier) {
    val p = progress()
    // In low-performance mode the camera jumps half-way instead of following the swipe.
    val low = LocalLowPerf.current
    val p3d = if (low) (if (p >= 0.5f) 1f else 0f) else p
    // Driving ([drive] > 0): Tesla's chase view, wheels turning, and the lane lines while a route is being followed.
    val wheelTurn = rememberWheelTurn(state, spinning = drive > 0f)
    val lanes = state.route?.phase == com.ahu.tlauncher.route.RoutePhase.ACTIVE || state.nav != null
    Car3dLayer(
        car, state.settings.carPaint, T.colors.isDark, low, state.lights.on, p3d, stageW() * contentScale(), modifier,
        drive = drive, wheelTurn = wheelTurn, lanes = lanes,
    )
}

/** Above this speed (km/h) the car counts as driving; below it for [PARKED_AFTER_MS], it's back to parked. */
private const val DRIVE_KMH = 5
private const val PARKED_AFTER_MS = 3_000L
/** The camera's glide between the parked views and the chase view. */
private const val CHASE_GLIDE_MS = 800

/** 0 while parked, 1 while driving, gliding between them (ease-out cubic). */
@Composable
private fun rememberDriveBlend(state: HomeUiState, enabled: Boolean): Float {
    val moving by remember(state.live) { derivedStateOf { (state.displaySpeedKmh ?: 0) >= DRIVE_KMH } }
    var driving by remember { mutableStateOf(false) }
    LaunchedEffect(moving) {
        if (moving) driving = true else { delay(PARKED_AFTER_MS); driving = false }
    }
    val d by animateFloatAsState(
        if (driving && enabled) 1f else 0f, tween(CHASE_GLIDE_MS, easing = TMotion.EaseOutCubic), label = "chase",
    )
    return d
}

/** How far the wheels have turned (radians), rolling at the car's speed (capped so they don't strobe), ~30 fps. */
@Composable
private fun rememberWheelTurn(state: HomeUiState, spinning: Boolean): Double {
    var turn by remember { androidx.compose.runtime.mutableDoubleStateOf(0.0) }
    val speed by androidx.compose.runtime.rememberUpdatedState(state.displaySpeedKmh ?: 0)
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last == 0L) last = t
                val dt = (t - last) / 1e9
                if (dt >= 1.0 / 30) {
                    val w = kotlin.math.min(speed / 3.6 / CarFraming.WHEEL_RADIUS, CarFraming.WHEEL_MAX_RAD_S)
                    if (w > 0) turn = (turn + w * dt) % (2 * Math.PI)
                    last = t
                }
            }
        }
    }
    return turn
}

/**
 * Draws a part of the car panel [scale] times smaller, toward the panel's left edge (x = 0, so the part
 * must start there) and around [pivotY] (a fraction of its own height), which keeps that point at the
 * same height in split view and full screen. Draw phase only: cheap while the panel glides.
 */
private fun Modifier.shrinkInPanel(scale: () -> Float, pivotY: Float): Modifier = graphicsLayer {
    val k = scale()
    scaleX = k; scaleY = k
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, pivotY)
}

@Composable
private fun SpeedBlock(state: HomeUiState, modifier: Modifier) {
    val obd = ObdDisplay.show(state.obd.status)
    val obdSpeed = if (obd) state.obd.data.speedKmh else null
    // No OBD and no GPS fix yet (e.g. parked indoors): show 0 rather than nothing.
    val kmh = state.displaySpeedKmh ?: 0
    val mph = state.settings.speedUnit == SpeedUnit.MPH
    val target = if (mph) (kmh * 0.621371).roundToInt() else kmh
    // Count to the new value instead of jumping (tabular figures keep the width steady).
    val speed by animateIntAsState(target, TMotion.live(), label = "speed")
    // Laid out like the prototype's speed card: number, then a row with the source where gears would be,
    // the unit in the middle, then a thin RPM line.
    Column(modifier.padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(speed.toString(), style = TType.speed, color = T.colors.text, textAlign = TextAlign.Center)
        Text(if (mph) "mph" else "km/h", style = TType.label, color = T.colors.muted, modifier = Modifier.offset(y = (-8).dp))
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(horizontal = 14.dp).offset(y = (-6).dp)) {
            Box(Modifier.fillMaxWidth().height(20.dp)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("OBD" to (obdSpeed != null), "GPS" to (obdSpeed == null)).forEach { (label, on) ->
                        Text(label, style = TType.label, color = if (on) T.colors.text else T.colors.muted)
                    }
                }
                // Fuel left now sits in the status bar, where Tesla shows its battery.
            }
            Spacer(Modifier.height(8.dp))
            RpmLine(if (obd) state.obd.data.rpm else null, Modifier.fillMaxWidth())
        }
    }
}

/** Thin track with a green marker that moves with engine RPM (the prototype's power line). */
@Composable
private fun RpmLine(rpm: Int?, modifier: Modifier) {
    val frac by animateFloatAsState(((rpm ?: 0) / 7000f).coerceIn(0f, 1f), TMotion.live(), label = "rpm")
    BoxWithConstraints(modifier.height(4.dp)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(T.colors.muted))
        if (rpm != null) {
            val w = 22.dp
            Box(Modifier.offset(x = (maxWidth - w) * frac).width(w).height(4.dp).clip(CircleShape).background(T.colors.green))
        }
    }
}

/** Vehicle tell-tales: only shown when active, in conventional indicator colours. */
@Composable
private fun Telltales(state: HomeUiState, modifier: Modifier) {
    // Derived, so new engine readings only redraw this when a light comes on or goes off.
    val items by remember(state.live) { derivedStateOf { telltaleItems(state) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (icon, color, desc) -> Icon(icon, desc, tint = color, modifier = Modifier.size(28.dp)) }
    }
}

private fun telltaleItems(state: HomeUiState) = state.obd.let { o ->
    val d = o.data
    buildList {
        if (!ObdDisplay.show(o.status)) return@buildList
        if (o.status == ObdStatus.CONNECTED) add(Triple(Icons.Rounded.SettingsInputComponent, TELL_GREEN, "OBD connected"))
        d.coolantC?.let {
            if (it < 50) add(Triple(Icons.Rounded.Thermostat, TELL_BLUE, "Engine cold"))
            if (it >= 105) add(Triple(Icons.Rounded.Thermostat, TELL_RED, "Engine hot"))
        }
        d.batteryV?.let { if (it < 11.8 || it > 15.2) add(Triple(Icons.Rounded.BatteryAlert, TELL_RED, "Battery voltage")) }
        d.fuelPct?.let { if (it < com.ahu.tlauncher.obd.FuelMath.LOW_FUEL_PCT) add(Triple(Icons.Rounded.LocalGasStation, TELL_AMBER, "Low fuel")) }
    }
}

private val TELL_GREEN = Color(0xFF25CB55) // prototype vehicle green
private val TELL_BLUE = Color(0xFF3E6AE1)
private val TELL_RED = Color(0xFFE5484D)
private val TELL_AMBER = Color(0xFFF5A623)

/** The car is drawn a bit smaller than the space allows, leaving air around it. */
private const val CAR_SCALE = 0.85f

private enum class CalloutKind(val label: String) { RPM("Engine"), COOLANT("Coolant"), BATTERY("Battery") }

/** Which callouts have data. Changes rarely, unlike the values themselves. */
private fun calloutKinds(state: HomeUiState): List<CalloutKind> {
    val o = state.obd
    if (!ObdDisplay.show(o.status)) return emptyList()
    return listOfNotNull(
        CalloutKind.RPM.takeIf { o.data.rpm != null },
        CalloutKind.COOLANT.takeIf { o.data.coolantC != null },
        CalloutKind.BATTERY.takeIf { o.data.batteryV != null },
    )
}

/** The car photos on a ground shadow (until the 3D car is on screen), with the engine readings beside it on the left. */
@Composable
private fun CarHero(
    state: HomeUiState, modifier: Modifier, progress: () -> Float, contentScale: () -> Float = { 1f }, use3d: Boolean = false, shown3d: () -> Float = { 0f },
    /** How visible the callouts' leader lines are (they fade out while the car glides to the driving view). */
    leaders: () -> Float = { 1f },
) {
    val ctx = LocalContext.current
    val preview = LocalInspectionMode.current
    val custom = state.settings.carImages
    // Split view shows the front of the car, full screen the 3/4 view (your own photos when set).
    val frontSrc = remember(custom) {
        if (preview) CarSource.Bundled(R.drawable.car_front)
        else CarImages.source(ctx, com.ahu.tlauncher.data.CarView.FRONT, custom) ?: CarImages.source(ctx, com.ahu.tlauncher.data.CarView.SIDE, custom) ?: CarSource.Illustration
    }
    val sideSrc = remember(custom) {
        if (preview) CarSource.Bundled(R.drawable.car_side) else CarImages.source(ctx, com.ahu.tlauncher.data.CarView.SIDE, custom) ?: CarSource.Illustration
    }
    // Low-performance mode: a cross-fade instead of the 3D turn.
    val low = LocalLowPerf.current
    val f = state.settings.tempUnit == TempUnit.F
    // The car and its layout only redraw when a callout appears or disappears, not on every reading.
    val callouts by remember(state.live) { derivedStateOf { calloutKinds(state) } }
    val shadowColor = if (T.colors.isDark) Color.Black.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.32f)

    val front = rememberCarPainter(frontSrc)
    val side = rememberCarPainter(sideSrc)
    BoxWithConstraints(modifier) {
        fun ratioOf(p: Painter?) = p?.intrinsicSize?.let { if (it.isSpecified() && it.height > 0) it.width / it.height else null } ?: 2f
        // The car on a soft ground shadow; the engine readings stand in a column on the left, so nothing sits above it.
        val calloutH = 8.dp
        val maxImgH = (maxHeight - calloutH) * 0.64f
        // With engine readings, room for their column on the left (and the same on the right, so the car stays centred).
        val sideRoom = if (callouts.isEmpty()) 16.dp else CALLOUTS_W
        fun widthFor(r: Float) = (min(maxWidth - sideRoom * 2, maxImgH * r) * CAR_SCALE).coerceAtLeast(1.dp)
        // 0 = front, 1 = 3/4, following the panel (swipe or sweep). Like the prototype's turntable: the car
        // swings round on a vertical axis, and its footprint moves between the two photos' proportions.
        val p = progress()
        val tRatio = ratioOf(front) + (ratioOf(side) - ratioOf(front)) * p
        val imgW = widthFor(tRatio)
        val imgH = imgW / tRatio
        val shadowH = imgH * 0.14f
        val rowW = imgW
        val carX = 0.dp
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        Box(Modifier.fillMaxWidth().height(calloutH + imgH)) {
            // The car and its ground shadow shrink toward the ground line, so the car stays standing on the same floor.
            // (Both fade out once the 3D car, drawn behind the whole panel, is on screen.)
            Box(Modifier.matchParentSize().shrinkInPanel(contentScale, pivotY = 1f).graphicsLayer { alpha = 1f - shown3d() }) { Box(Modifier.align(Alignment.TopCenter).width(rowW).fillMaxHeight()) {
                Box(
                    Modifier.offset(x = carX + imgW * 0.06f, y = calloutH + imgH - shadowH).size(imgW * 0.88f, shadowH)
                        .drawBehind {
                            scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                                drawCircle(Brush.radialGradient(listOf(shadowColor, Color.Transparent), center, size.width / 2), size.width / 2, center)
                            }
                        },
                )
                // Both photos sit on the same ground line; the turn swaps them mid-way.
                listOf(front to (1f - p), side to p).forEach { (painter, weight) ->
                    if (painter == null || weight <= 0.001f) return@forEach
                    val r = ratioOf(painter)
                    val w = widthFor(r); val h = w / r
                    val isFront = painter === front
                    Image(
                        painter, "My car", contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .offset(x = (rowW - w) / 2, y = calloutH + imgH - h)
                            .size(w, h)
                            .graphicsLayer {
                                // front swings away to the left; the 3/4 view swings in from the right
                                if (!low) {
                                    rotationY = if (isFront) -60f * p else 60f * (1f - p)
                                    cameraDistance = 14f * density
                                }
                                alpha = ((weight - 0.25f) / 0.5f).coerceIn(0f, 1f)
                            },
                    )
                }
            } }
        }
        // Engine readings: a column at the panel's left edge, level with the middle of the car. It shrinks around
        // its own middle with the panel, like the other parts.
        // Tesla's callouts: a thin leader line runs from each reading to a dot just inside the car's side,
        // when there's room for it (full screen).
        val leaderW = (maxWidth - imgW) / 2 + imgW * 0.1f - 18.dp
        val lineColor = T.colors.muted.copy(alpha = 0.55f)
        if (callouts.isNotEmpty()) Column(
            // Never negative: until the car photo has loaded, its stand-in shape can be shorter than the column.
            Modifier.padding(top = (calloutH + imgH / 2 - CALLOUTS_HALF_H).coerceAtLeast(0.dp)).shrinkInPanel(contentScale, pivotY = 0.5f).padding(start = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            callouts.forEach { c ->
                Row(if (leaderW >= LEADER_MIN_W) Modifier.width(leaderW) else Modifier, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(c.label, style = TType.caption, color = T.colors.muted, maxLines = 1)
                        CalloutValue(c, state, f)
                    }
                    if (leaderW >= LEADER_MIN_W) {
                        Spacer(Modifier.width(10.dp))
                        Row(Modifier.weight(1f).graphicsLayer { alpha = leaders() * ((progress() - 0.5f) * 2f).coerceIn(0f, 1f) }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(lineColor))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(lineColor))
                        }
                    }
                }
            }
        }
    }
}

/** Half the height of the three-reading column (label + value, 14 dp apart), to centre it on the car. */
private val CALLOUTS_HALF_H = 64.dp
/** Width kept free for the reading column ("2,340 rpm" plus its margin). */
private val CALLOUTS_W = 92.dp
/** Narrowest reading-to-car span that still gets a leader line (reading, gap and a short line). */
private val LEADER_MIN_W = 140.dp

@Composable
private fun rememberCarPainter(src: CarSource): Painter? {
    val preview = LocalInspectionMode.current
    return when {
        src is CarSource.Illustration -> painterResource(R.drawable.car_mpv)
        preview && src is CarSource.Bundled -> painterResource(src.resId)
        else -> {
            val ctx = LocalContext.current
            var bmp by remember(src) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(src) { bmp = CarImages.load(ctx, src) }
            bmp?.let { remember(it) { BitmapPainter(it) } }
        }
    }
}

private fun Size.isSpecified() = width.isFinite() && height.isFinite()

/** The live number under a callout: the only part that redraws on each reading. Counts to new values. */
@Composable
private fun CalloutValue(c: CalloutKind, state: HomeUiState, fahrenheit: Boolean) {
    val d = state.obd.data
    val (text, alert) = when (c) {
        CalloutKind.RPM -> {
            val rpm by animateIntAsState(d.rpm ?: 0, TMotion.live(), label = "rpm")
            "%,d rpm".format(rpm) to false
        }
        CalloutKind.COOLANT -> {
            val coolant by animateIntAsState(d.coolantC ?: 0, TMotion.live(), label = "coolant")
            "${if (fahrenheit) (coolant * 9 / 5.0 + 32).roundToInt() else coolant}°${if (fahrenheit) "F" else "C"}" to ((d.coolantC ?: 0) >= 105)
        }
        CalloutKind.BATTERY -> {
            val volts by animateFloatAsState((d.batteryV ?: 0.0).toFloat(), TMotion.live(), label = "volts")
            "%.1f V".format(volts) to (d.batteryV?.let { it < 11.8 || it > 15.2 } == true)
        }
    }
    Text(text, style = TType.button, color = if (alert) TELL_RED else T.colors.text, maxLines = 1)
}

// ---------------------------------------------------------------- cards

@Composable
private fun cardColor() = if (T.colors.isDark) T.colors.card else Color.White.copy(alpha = 0.96f)

/** Pages of the card pager under the car. */
private enum class CardPage { MUSIC, NAVIGATE, MUSIC_NAVIGATE, TRIP, ENGINE, FUEL }

/**
 * Split view: music, Navigate, trip, then engine and fuel when OBD gives them, one card a page.
 * Full screen: music and Navigate side by side on the first page (like Tesla's), then the same.
 */
private fun cardPages(state: HomeUiState, wide: Boolean): List<CardPage> {
    val obd = ObdDisplay.show(state.obd.status)
    return buildList {
        if (wide) add(CardPage.MUSIC_NAVIGATE) else { add(CardPage.MUSIC); add(CardPage.NAVIGATE) }
        add(CardPage.TRIP)
        if (obd) add(CardPage.ENGINE)
        if (obd && state.obd.data.fuelPct != null) add(CardPage.FUEL)
    }
}

@Composable
private fun CardPager(state: HomeUiState, actions: HomeActions, wide: Boolean) {
    // Engine readings change all the time; the page list only when OBD comes or goes.
    val pages by remember(state.live, wide) { derivedStateOf { cardPages(state, wide) } }
    val pager = rememberPagerState { pages.size }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, pageSpacing = 12.dp) { page ->
            when (pages.getOrNull(page)) {
                CardPage.MUSIC -> MusicCard(state, actions, Modifier.fillMaxWidth())
                CardPage.NAVIGATE -> NavigateCard(actions, Modifier.fillMaxWidth())
                CardPage.MUSIC_NAVIGATE -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicCard(state, actions, Modifier.weight(1.6f))
                    NavigateCard(actions, Modifier.weight(1f))
                }
                CardPage.TRIP -> TripCard(state, wide, Modifier.fillMaxWidth())
                CardPage.ENGINE -> EngineCard(state, actions, Modifier.fillMaxWidth())
                CardPage.FUEL -> FuelCard(state, actions, Modifier.fillMaxWidth())
                null -> Box(Modifier.fillMaxWidth().height(CARD_H))
            }
        }
        // Prototype pagination: dots fade between states (300 ms); tapping one slides the cards (600 ms ease-out).
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Row(Modifier.padding(top = 1.dp)) {
            repeat(pages.size) { i ->
                val dot by androidx.compose.animation.animateColorAsState(
                    if (pager.currentPage == i) T.colors.text else T.colors.secondary.copy(alpha = 0.4f), TMotion.dot(), label = "dot",
                )
                Box(
                    Modifier
                        .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            scope.launch { pager.animateScrollToPage(i, animationSpec = TMotion.carousel()) }
                        }
                        .padding(start = 2.5.dp, end = 2.5.dp, top = 5.dp),
                ) { Box(Modifier.size(5.dp).clip(CircleShape).background(dot)) }
            }
        }
    }
}

private val CARD_H = 112.dp

@Composable
fun MusicCard(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Box(modifier.height(CARD_H).clip(TShapes.button).background(cardColor())) {
        if (!state.notificationAccess) {
            Row(
                Modifier.fillMaxSize().pressable { actions.requestNotificationAccess() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = T.colors.text, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Allow notification access", style = TType.button, color = T.colors.text)
                    Text("Shows music and Maps/Waze directions here", style = TType.label, color = T.colors.muted, maxLines = 2)
                }
            }
            return@Box
        }
        // Nothing playing yet (e.g. after the engine was off): show the last song; Play resumes it.
        val live = state.media != null
        val m = state.media ?: state.lastMedia
        val appLabel = m?.let { mi -> state.apps.firstOrNull { it.packageName == mi.packageName }?.label }
        Column(Modifier.fillMaxSize()) {
            // Tap the song to open the player over the car panel (or the music app if nothing was ever played).
            Row(Modifier.fillMaxWidth().height(60.dp).pressable { if (m != null) actions.openCardPlayer() else actions.openMediaApp() }, verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(m, Modifier.size(60.dp).graphicsLayer { alpha = if (live || m == null) 1f else 0.6f })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(m?.title?.ifBlank { null } ?: "Not playing", style = TType.title, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.musicSubtitle(appLabel) ?: "Start music in any app", style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TrackProgress(m, Modifier.fillMaxWidth().height(2.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButtonT(Icons.Rounded.SkipPrevious, "Previous", size = 40.dp, iconSize = 28.dp, enabled = live) { actions.previous() }
                IconButtonT(if (m?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", size = 40.dp, iconSize = 30.dp, enabled = m != null && !state.resumingMedia) { actions.playPause() }
                IconButtonT(Icons.Rounded.SkipNext, "Next", size = 40.dp, iconSize = 28.dp, enabled = live) { actions.next() }
            }
        }
    }
}

@Composable
private fun NavigateCard(actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier.height(CARD_H).clip(TShapes.button).background(cardColor())) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).pressable(onClick = actions::openNavApp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = T.colors.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Navigate", style = TType.title, color = T.colors.muted)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(T.colors.divider))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            NavShortcut(Icons.Rounded.Home, "Home", actions::navigateHome, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(28.dp).background(T.colors.divider))
            NavShortcut(Icons.Rounded.Work, "Work", actions::navigateWork, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavShortcut(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxHeight().pressable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = T.colors.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = TType.button, color = T.colors.text)
    }
}

/**
 * Tesla's trip card ("Current drive / Since charge / Trip A"), from TLauncher's trip log: this drive (or the last
 * one when parked), today and this week. Distance, time and fuel economy (with OBD fuel data).
 */
@Composable
internal fun TripCard(state: HomeUiState, wide: Boolean, modifier: Modifier = Modifier) {
    val mph = state.settings.speedUnit == SpeedUnit.MPH
    val zone = java.time.ZoneId.systemDefault()
    val nowMs = state.now.atZone(zone).toInstant().toEpochMilli()
    val current = state.currentTripKm
    val cols = remember(state.trips, current, state.now.toLocalDate()) {
        val dayStart = state.now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = com.ahu.tlauncher.system.TripMath.weekStartMs(nowMs, zone)
        val extra = current ?: 0.0
        val drive = if (current != null) TripColumn("This drive", current, null, state.fuel.tripKmPerL)
        else state.trips.lastOrNull()?.let { TripColumn("Last drive", it.km, it.minutes, it.kmPerL) } ?: TripColumn("Last drive", 0.0, 0, null)
        fun total(label: String, from: Long) = com.ahu.tlauncher.system.TripMath.totals(state.trips, from).let { t ->
            TripColumn(label, t.km + extra, t.minutes, t.kmPerL)
        }
        listOf(drive, total("Today", dayStart), total("This week", weekStart))
    }
    Row(modifier.height(CARD_H).clip(TShapes.button).background(cardColor()).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        cols.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(T.colors.divider))
            Column(Modifier.weight(1f).padding(horizontal = if (wide) 16.dp else 8.dp)) {
                Text(c.label, style = TType.caption, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dist = if (mph) c.km * 0.621371 else c.km
                Text("%.1f %s".format(dist, if (mph) "mi" else "km"), style = if (wide) TType.title else TType.button, color = T.colors.text, maxLines = 1)
                Text(c.minutes?.let { if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min" } ?: "Driving", style = TType.caption, color = T.colors.muted, maxLines = 1)
                Text(c.kmPerL?.let { "%.1f km/L".format(it) } ?: "– km/L", style = TType.caption, color = T.colors.muted, maxLines = 1)
            }
        }
    }
}

private data class TripColumn(val label: String, val km: Double, val minutes: Int?, val kmPerL: Double?)

/** Engine readings and stored fault codes in one card; tap for Settings → Car (diagnostics). */
@Composable
internal fun EngineCard(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val d = state.obd.data
    val f = state.settings.tempUnit == TempUnit.F
    val codes = state.diag.stored
    val cells = listOf(
        Triple("Coolant", d.coolantC?.let { if (f) "${(it * 9 / 5.0 + 32).roundToInt()}°F" else "$it°C" } ?: "–", (d.coolantC ?: 0) >= 105),
        Triple("Battery", d.batteryV?.let { "%.1f V".format(it) } ?: "–", d.batteryV?.let { it < 11.8 || it > 15.2 } == true),
        Triple("Engine", d.rpm?.let { "%,d rpm".format(it) } ?: "–", false),
        Triple("Fault codes", when { codes == null -> "Not read"; codes.isEmpty() -> "None"; else -> "${codes.size} stored" }, !codes.isNullOrEmpty()),
    )
    Column(modifier.height(CARD_H).clip(TShapes.button).background(cardColor()).pressable(onClick = actions::openCarSettings).padding(horizontal = 16.dp, vertical = 12.dp)) {
        for (row in cells.chunked(2)) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { (label, value, alert) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = TType.caption, color = T.colors.muted, maxLines = 1)
                        Text(value, style = TType.button, color = if (alert) TELL_RED else T.colors.text, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Fuel left (where Tesla shows charge): level, range, average economy and a button to find fuel nearby. */
@Composable
internal fun FuelCard(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val pct = state.obd.data.fuelPct ?: return
    val mph = state.settings.speedUnit == SpeedUnit.MPH
    val low = pct < com.ahu.tlauncher.obd.FuelMath.LOW_FUEL_PCT
    val tint = if (low) TELL_AMBER else T.colors.text
    val level by animateFloatAsState(pct.coerceIn(0, 100) / 100f, TMotion.live(), label = "fuelCard")
    Row(modifier.height(CARD_H).clip(TShapes.button).background(cardColor()).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$pct%", style = TType.display, color = tint)
                Spacer(Modifier.width(10.dp))
                state.fuel.rangeKm?.let { r ->
                    Text(if (mph) "${(r * 0.621371).roundToInt()} mi left" else "$r km left", style = TType.label, color = T.colors.muted, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(T.colors.secondary.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(level).clip(CircleShape).background(tint))
            }
            Spacer(Modifier.height(6.dp))
            Text(state.fuel.avgKmPerL?.let { "Average %.1f km/L".format(it) } ?: "Average –", style = TType.caption, color = T.colors.muted, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        com.ahu.tlauncher.ui.components.PillButton("Find fuel", icon = Icons.Rounded.LocalGasStation, height = 40.dp, onClick = actions::findFuel)
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
fun ObdBadge(status: ObdStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        ObdStatus.DEMO -> "OBD demo"
        ObdStatus.CONNECTED -> "OBD connected"
        ObdStatus.CONNECTING -> "OBD connecting"
        ObdStatus.ERROR -> "OBD not responding"
        ObdStatus.DISCONNECTED -> "OBD off"
    }
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (status == ObdStatus.CONNECTED) TELL_GREEN else T.colors.secondary))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TType.label, color = T.colors.muted)
    }
}

@Composable
fun AlbumArt(m: MediaInfo?, modifier: Modifier) {
    Box(modifier.background(T.colors.card), contentAlignment = Alignment.Center) {
        val art = m?.art
        if (art != null) Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.MusicNote, null, tint = T.colors.muted, modifier = Modifier.size(24.dp))
    }
}

/** Live position = last reported position + elapsed time × speed (while playing). */
@Composable
fun livePosition(m: MediaInfo?): Long {
    val low = LocalLowPerf.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(m?.isPlaying, m?.updateTimeMs) {
        while (m?.isPlaying == true) { now = SystemClock.elapsedRealtime(); delay(if (low) 1_000 else 500) }
    }
    if (m == null) return 0
    if (!m.isPlaying || m.updateTimeMs <= 0) return m.positionMs
    val p = m.positionMs + ((now - m.updateTimeMs) * m.speed).toLong()
    return if (m.durationMs > 0) p.coerceIn(0, m.durationMs) else p
}

@Composable
private fun TrackProgress(m: MediaInfo?, modifier: Modifier) {
    val pos = if (LocalInspectionMode.current) m?.positionMs ?: 0 else livePosition(m)
    val frac = if (m == null || m.durationMs <= 0) 0f else (pos.toFloat() / m.durationMs).coerceIn(0f, 1f)
    BoxWithConstraints(modifier.background(T.colors.divider)) {
        Box(Modifier.width(maxWidth * frac).fillMaxHeight().background(T.colors.text))
    }
}

