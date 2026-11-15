package com.ahu.tlauncher.ui.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.ahu.tlauncher.ui.components.glideOnChange
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
import kotlin.math.abs
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
) {
    val dark = T.colors.isDark
    Box(
        modifier.then(if (drawBackground) Modifier.background(stageBrush()) else Modifier).clipToBounds().drawBehind {
            // Night: a soft pool of light behind the car, like the prototype's lit stage.
            if (dark) drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.5f), radius = size.minDimension * 0.55f),
                radius = size.minDimension * 0.55f, center = Offset(size.width / 2, size.height * 0.5f),
            )
        },
    ) {
        Column(Modifier.fillMaxSize().padding(top = STATUS_H)) {
            SpeedBlock(state, Modifier.fillMaxWidth(), glideKey = expanded)
            CarHero(state, Modifier.fillMaxWidth().weight(1f), glideKey = expanded)
        }
        Telltales(state, Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = STATUS_H + 8.dp))
        // Lights-on tell-tale sits at the panel's top-right corner, in the status row (as in the prototype).
        if (state.lights.on) Icon(
            com.ahu.tlauncher.ui.components.LowBeamIcon, "Headlights on", tint = TELL_GREEN,
            modifier = Modifier.align(if (handleOnLeft || expanded) Alignment.TopStart else Alignment.TopEnd)
                .padding(start = if (expanded) 150.dp else 18.dp, end = 18.dp, top = 9.dp).size(22.dp),
        )

        // bottom cards overlap the reflection, as in the reference layout
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp)) {
            AnimatedContent(
                expanded,
                transitionSpec = { (fadeIn(TMotion.enter()) togetherWith fadeOut(tween(120, easing = TMotion.EaseOut))).using(SizeTransform(clip = false) { _, _ -> snap() }) },
                label = "cards",
            ) { full ->
                if (full) {
                    Row(Modifier.widthIn(max = 660.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicCard(state, actions, Modifier.weight(1.6f))
                        NavigateCard(actions, Modifier.weight(1f))
                    }
                } else {
                    CardPager(state, actions)
                }
            }
        }

        // drag handle on the right edge toggles the map
        Box(
            Modifier.align(if (handleOnLeft) Alignment.CenterStart else Alignment.CenterEnd).width(28.dp).height(120.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(onDragEnd = { if (abs(total) > 24) onToggleMap(); total = 0f }) { _, d -> total += d }
                }
                .pressable(onClick = onToggleMap),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(4.dp).height(56.dp).clip(CircleShape).background(T.colors.secondary.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun SpeedBlock(state: HomeUiState, modifier: Modifier, glideKey: Any) {
    val obd = ObdDisplay.show(state.obd.status)
    val obdSpeed = if (obd) state.obd.data.speedKmh else null
    // No OBD and no GPS fix yet (e.g. parked indoors): show 0 rather than nothing.
    val kmh = state.displaySpeedKmh ?: 0
    val mph = state.settings.speedUnit == SpeedUnit.MPH
    val target = if (mph) (kmh * 0.621371).roundToInt() else kmh
    // Count to the new value instead of jumping (tabular figures keep the width steady).
    val speed by animateIntAsState(target, TMotion.live(), label = "speed")
    // Laid out like the prototype's speed card: number, then a row with the source where gears would be,
    // the unit in the middle and a battery-style fuel gauge on the right, then a thin RPM line.
    Column(modifier.padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(speed.toString(), modifier = Modifier.glideOnChange(glideKey), style = TType.speed, color = T.colors.text, textAlign = TextAlign.Center)
        Text(if (mph) "mph" else "km/h", style = TType.label, color = T.colors.muted, modifier = Modifier.offset(y = (-8).dp))
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(horizontal = 14.dp).offset(y = (-6).dp)) {
            Box(Modifier.fillMaxWidth().height(20.dp)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("OBD" to (obdSpeed != null), "GPS" to (obdSpeed == null)).forEach { (label, on) ->
                        Text(label, style = TType.label, color = if (on) T.colors.text else T.colors.muted)
                    }
                }
                val fuel = if (obd) state.obd.data.fuelPct else null
                if (fuel != null) FuelGauge(fuel, state.fuel.rangeKm, mph, Modifier.align(Alignment.CenterEnd))
            }
            Spacer(Modifier.height(8.dp))
            RpmLine(if (obd) state.obd.data.rpm else null, Modifier.fillMaxWidth())
        }
    }
}

/** TLauncher's fuel gauge: pump icon, slim level bar and "62% · 335 km". Amber when low. */
@Composable
private fun FuelGauge(pct: Int, rangeKm: Int?, mph: Boolean, modifier: Modifier) {
    val level by animateFloatAsState(pct.coerceIn(0, 100) / 100f, TMotion.live(), label = "fuel")
    val low = pct < com.ahu.tlauncher.obd.FuelMath.LOW_FUEL_PCT
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalGasStation, "Fuel", tint = if (low) TELL_AMBER else T.colors.muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(56.dp).height(6.dp).clip(CircleShape).background(T.colors.secondary.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level).clip(CircleShape).background(if (low) TELL_AMBER else T.colors.text))
        }
        Spacer(Modifier.width(8.dp))
        val range = rangeKm?.let { if (mph) " · ${(it * 0.621371).roundToInt()} mi" else " · $it km" } ?: ""
        Text("$pct%$range", style = TType.caption, color = if (low) TELL_AMBER else T.colors.muted, maxLines = 1)
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

/** Width the three callouts need to sit side by side without overlapping. */
private val CALLOUT_MIN_W = 330.dp

private enum class CalloutKind(val fraction: Float, val label: String) { RPM(0.2f, "Engine"), COOLANT(0.5f, "Coolant"), BATTERY(0.82f, "Battery") }

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

/** Car photo (tap to change view) with mirrored floor reflection and data callouts with leader lines. */
@Composable
private fun CarHero(state: HomeUiState, modifier: Modifier, glideKey: Any) {
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
    val expanded = glideKey == true
    // 0 = front, 1 = 3/4. Like the prototype's turntable: the car swings round on a vertical axis.
    // Low-performance mode: a short cross-fade instead of the 3D turn.
    val low = LocalLowPerf.current
    val turn = remember { androidx.compose.animation.core.Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        turn.animateTo(if (expanded) 1f else 0f, if (low) tween(LOW_PERF_FADE_MS) else tween(CAR_TURN_MS, easing = TMotion.EaseInOut))
    }

    val f = state.settings.tempUnit == TempUnit.F
    // The car and its layout only redraw when a callout appears or disappears, not on every reading.
    val callouts by remember(state.live) { derivedStateOf { calloutKinds(state) } }
    val shadowColor = if (T.colors.isDark) Color.Black.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.32f)

    val front = rememberCarPainter(frontSrc)
    val side = rememberCarPainter(sideSrc)
    BoxWithConstraints(modifier) {
        fun ratioOf(p: Painter?) = p?.intrinsicSize?.let { if (it.isSpecified() && it.height > 0) it.width / it.height else null } ?: 2f
        // Callout zone on top (only with OBD data), then the car on a soft ground shadow.
        val calloutH = if (callouts.isEmpty()) 8.dp else 56.dp
        val maxImgH = (maxHeight - calloutH) * (if (callouts.isEmpty()) 0.64f else 0.56f)
        fun widthFor(r: Float) = min(maxWidth - 32.dp, maxImgH * r) * CAR_SCALE
        val target = if (expanded) side else front
        val tRatio = ratioOf(target)
        val imgW = widthFor(tRatio)
        val imgH = imgW / tRatio
        val shadowH = imgH * 0.14f
        // Narrow views (front) still get readable callouts: they spread over a wider row, without leader lines.
        val rowW = if (callouts.isEmpty()) imgW else androidx.compose.ui.unit.max(imgW, min(maxWidth - 16.dp, CALLOUT_MIN_W))
        val carX = (rowW - imgW) / 2
        val lines = rowW == imgW
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.glideOnChange(glideKey).width(rowW).height(calloutH + imgH)) {
                Box(
                    Modifier.offset(x = carX + imgW * 0.06f, y = calloutH + imgH - shadowH).size(imgW * 0.88f, shadowH)
                        .drawBehind {
                            scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                                drawCircle(Brush.radialGradient(listOf(shadowColor, Color.Transparent), center, size.width / 2), size.width / 2, center)
                            }
                        },
                )
                callouts.forEach { c -> CalloutView(c, state, f, rowW, calloutH, imgH, lines) }
                // Both photos sit on the same ground line; the turn swaps them mid-way.
                val p = turn.value
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
            }
        }
    }
}

/** Length of the front ↔ 3/4 car turn (prototype's card transitions are ~0.6 s). */
private const val CAR_TURN_MS = 600
/** Cross-fade used instead of the turn in low-performance mode. */
private const val LOW_PERF_FADE_MS = 200

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

@Composable
private fun CalloutView(c: CalloutKind, state: HomeUiState, fahrenheit: Boolean, imgW: Dp, calloutH: Dp, imgH: Dp, line: Boolean) {
    val x = imgW * c.fraction
    val lineTop = 38.dp
    val lineEnd = calloutH + imgH * 0.22f
    if (line) Box(Modifier.offset(x = x - 0.5.dp, y = lineTop).width(1.dp).height(lineEnd - lineTop).background(T.colors.text.copy(alpha = 0.55f)))
    Column(Modifier.offset(x = x + 6.dp, y = 0.dp)) {
        Text(c.label, style = TType.caption, color = T.colors.muted, maxLines = 1)
        CalloutValue(c, state, fahrenheit)
    }
}

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

@Composable
private fun CardPager(state: HomeUiState, actions: HomeActions) {
    val pager = rememberPagerState { 2 }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, pageSpacing = 12.dp) { page ->
            if (page == 0) MusicCard(state, actions, Modifier.fillMaxWidth()) else NavigateCard(actions, Modifier.fillMaxWidth())
        }
        // Prototype pagination: dots fade between states (300 ms); tapping one slides the cards (600 ms ease-out).
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Row(Modifier.padding(top = 1.dp)) {
            repeat(2) { i ->
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

