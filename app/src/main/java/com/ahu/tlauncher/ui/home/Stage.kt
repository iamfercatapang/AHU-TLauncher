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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
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
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Studio backdrop behind the car: soft vertical fade towards a "floor". */
@Composable
private fun stageBrush(): Brush = if (T.colors.isDark) {
    Brush.verticalGradient(0f to Color(0xFF4A4C50), 0.62f to Color(0xFF2C2D30), 1f to Color(0xFF141517))
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
) {
    Box(modifier.background(stageBrush()).clipToBounds()) {
        Column(Modifier.fillMaxSize().padding(top = STATUS_H)) {
            SpeedBlock(state, Modifier.fillMaxWidth(), glideKey = expanded)
            CarHero(state, Modifier.fillMaxWidth().weight(1f), glideKey = expanded)
        }
        Telltales(state, Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = STATUS_H + 8.dp))

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
    val obdSpeed = if (ObdDisplay.show(state.obd.status)) state.obd.data.speedKmh else null
    // No OBD and no GPS fix yet (e.g. parked indoors): show 0 rather than nothing.
    val kmh = state.displaySpeedKmh ?: 0
    val mph = state.settings.speedUnit == SpeedUnit.MPH
    val target = if (mph) (kmh * 0.621371).roundToInt() else kmh
    // Count to the new value instead of jumping (tabular figures keep the width steady).
    val speed by animateIntAsState(target, TMotion.count(), label = "speed")
    Column(modifier.padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(speed.toString(), modifier = Modifier.glideOnChange(glideKey), style = TType.speed, color = T.colors.text, textAlign = TextAlign.Center)
        Text(
            (if (mph) "mph" else "km/h") + if (obdSpeed == null) " · GPS" else "",
            style = TType.label, color = T.colors.muted, modifier = Modifier.offset(y = (-6).dp),
        )
    }
}

/** Vehicle tell-tales: only shown when active, in conventional indicator colours. */
@Composable
private fun Telltales(state: HomeUiState, modifier: Modifier) {
    val d = state.obd.data
    if (!ObdDisplay.show(state.obd.status)) return
    val items = buildList {
        if (state.obd.status == ObdStatus.CONNECTED) add(Triple(Icons.Rounded.SettingsInputComponent, TELL_GREEN, "OBD connected"))
        d.coolantC?.let {
            if (it < 50) add(Triple(Icons.Rounded.Thermostat, TELL_BLUE, "Engine cold"))
            if (it >= 105) add(Triple(Icons.Rounded.Thermostat, TELL_RED, "Engine hot"))
        }
        d.batteryV?.let { if (it < 11.8 || it > 15.2) add(Triple(Icons.Rounded.BatteryAlert, TELL_RED, "Battery voltage")) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (icon, color, desc) -> Icon(icon, desc, tint = color, modifier = Modifier.size(28.dp)) }
    }
}

private val TELL_GREEN = Color(0xFF25CB55)
private val TELL_BLUE = Color(0xFF3E6AE1)
private val TELL_RED = Color(0xFFE5484D)

/** The car is drawn a bit smaller than the space allows, leaving air around it. */
private const val CAR_SCALE = 0.85f

/** Width the three callouts need to sit side by side without overlapping. */
private val CALLOUT_MIN_W = 330.dp

private data class Callout(val fraction: Float, val label: String, val value: String, val alert: Boolean = false)

/** Car photo (tap to change view) with mirrored floor reflection and data callouts with leader lines. */
@Composable
private fun CarHero(state: HomeUiState, modifier: Modifier, glideKey: Any) {
    val ctx = LocalContext.current
    val preview = LocalInspectionMode.current
    val custom = state.settings.carImages
    val shown = state.settings.carViews
    val views = remember(custom, shown) {
        val all = if (preview) listOf(
            com.ahu.tlauncher.data.CarView.SIDE to CarSource.Bundled(R.drawable.car_side),
            com.ahu.tlauncher.data.CarView.FRONT to CarSource.Bundled(R.drawable.car_front),
            com.ahu.tlauncher.data.CarView.REAR to CarSource.Bundled(R.drawable.car_rear),
        ) else CarImages.available(ctx, custom)
        // Only the views ticked in Settings; fall back to all if none of the ticked ones has an image.
        (all.filter { it.first in shown }.ifEmpty { all.take(1) }).map { it.second }
    }
    var index by remember(views) { mutableIntStateOf(0) }
    val src = views.getOrNull(index % views.size.coerceAtLeast(1)) ?: CarSource.Illustration

    val showObd = ObdDisplay.show(state.obd.status)
    val d = state.obd.data
    val f = state.settings.tempUnit == TempUnit.F
    // Readouts count to new values rather than jumping.
    val rpm by animateIntAsState(d.rpm ?: 0, TMotion.count(), label = "rpm")
    val coolant by animateIntAsState(d.coolantC ?: 0, TMotion.count(), label = "coolant")
    val volts by animateFloatAsState((d.batteryV ?: 0.0).toFloat(), TMotion.count(), label = "volts")
    val callouts = if (!showObd) emptyList() else listOfNotNull(
        d.rpm?.let { Callout(0.2f, "Engine", "%,d rpm".format(rpm)) },
        d.coolantC?.let { Callout(0.5f, "Coolant", "${if (f) (coolant * 9 / 5.0 + 32).roundToInt() else coolant}°${if (f) "F" else "C"}", alert = it >= 105) },
        d.batteryV?.let { Callout(0.82f, "Battery", "%.1f V".format(volts), alert = it < 11.8 || it > 15.2) },
    )
    val shadowColor = if (T.colors.isDark) Color.Black.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.32f)

    // With a single view there's nothing to cycle: no tap target (and no dimmed "disabled" look).
    val tap = if (views.size > 1) Modifier.pressable { index = (index + 1) % views.size } else Modifier
    BoxWithConstraints(modifier.then(tap)) {
        // Turntable-style view switch: the current view eases out to the left while the next
        // comes in from the right (transforms + opacity only; a new tap retargets mid-way).
        AnimatedContent(
            src,
            transitionSpec = {
                val enter = slideInHorizontally(TMotion.enter()) { it / 10 } + fadeIn(TMotion.enter()) + scaleIn(TMotion.enter(), initialScale = 0.97f)
                val exit = slideOutHorizontally(tween(160, easing = TMotion.EaseOut)) { -it / 10 } + fadeOut(tween(160, easing = TMotion.EaseOut))
                (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> snap() }) // no per-frame re-layout
            },
            label = "car",
        ) { s ->
            val painter = rememberCarPainter(s) ?: return@AnimatedContent
            val ratio = painter.intrinsicSize.let { if (it.isSpecified() && it.height > 0) it.width / it.height else 2f }
            // Callout zone on top (only with OBD data), then the car on a soft ground shadow.
            // The bottom cards overlap the space under the car.
            val calloutH = if (callouts.isEmpty()) 8.dp else 56.dp
            val maxImgH = (maxHeight - calloutH) * (if (callouts.isEmpty()) 0.64f else 0.56f)
            val imgW = min(maxWidth - 32.dp, maxImgH * ratio) * CAR_SCALE
            val imgH = imgW / ratio
            // Contact shadow: spans the bottom 14% of the photo (where the tyres meet the floor) so the
            // densest part sits under the car body and only a soft edge shows around the wheels.
            val shadowH = imgH * 0.14f
            // Narrow views (front/rear) still get readable callouts: they spread over a wider row,
            // without leader lines, since most of them wouldn't point at the car.
            val rowW = if (callouts.isEmpty()) imgW else androidx.compose.ui.unit.max(imgW, min(maxWidth - 16.dp, CALLOUT_MIN_W))
            val carX = (rowW - imgW) / 2
            val lines = rowW == imgW
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.glideOnChange(glideKey).width(rowW).height(calloutH + imgH)) {
                    // ground shadow, drawn behind the car and anchored to the tyre line
                    Box(
                        Modifier.offset(x = carX + imgW * 0.06f, y = calloutH + imgH - shadowH).size(imgW * 0.88f, shadowH)
                            .drawBehind {
                                scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                                    drawCircle(Brush.radialGradient(listOf(shadowColor, Color.Transparent), center, size.width / 2), size.width / 2, center)
                                }
                            },
                    )
                    callouts.forEach { c -> CalloutView(c, rowW, calloutH, imgH, lines) }
                    Image(painter, "My car", contentScale = ContentScale.Fit, modifier = Modifier.offset(x = carX, y = calloutH).size(imgW, imgH))
                }
                if (views.size > 1) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        views.indices.forEach { i ->
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == index % views.size) T.colors.text else T.colors.secondary.copy(alpha = 0.4f)))
                        }
                    }
                }
            }
        }
    }
}

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
private fun CalloutView(c: Callout, imgW: Dp, calloutH: Dp, imgH: Dp, line: Boolean) {
    val x = imgW * c.fraction
    val lineTop = 38.dp
    val lineEnd = calloutH + imgH * 0.22f
    if (line) Box(Modifier.offset(x = x - 0.5.dp, y = lineTop).width(1.dp).height(lineEnd - lineTop).background(T.colors.text.copy(alpha = 0.55f)))
    Column(Modifier.offset(x = x + 6.dp, y = 0.dp)) {
        Text(c.label, style = TType.caption, color = T.colors.muted, maxLines = 1)
        Text(c.value, style = TType.button, color = if (c.alert) TELL_RED else T.colors.text, maxLines = 1)
    }
}

// ---------------------------------------------------------------- cards

@Composable
private fun cardColor() = if (T.colors.isDark) Color(0xFF26282C).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)

@Composable
private fun CardPager(state: HomeUiState, actions: HomeActions) {
    val pager = rememberPagerState { 2 }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, pageSpacing = 12.dp) { page ->
            if (page == 0) MusicCard(state, actions, Modifier.fillMaxWidth()) else NavigateCard(actions, Modifier.fillMaxWidth())
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) { i -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (pager.currentPage == i) T.colors.text else T.colors.secondary.copy(alpha = 0.4f))) }
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
        val m = state.media
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(60.dp).pressable { actions.openMediaApp() }, verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(m, Modifier.size(60.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(m?.title?.ifBlank { null } ?: "Not playing", style = TType.title, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m?.artist?.ifBlank { null } ?: "Start music in any app", style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TrackProgress(m, Modifier.fillMaxWidth().height(2.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButtonT(Icons.Rounded.SkipPrevious, "Previous", size = 40.dp, iconSize = 28.dp, enabled = m != null) { actions.previous() }
                IconButtonT(if (m?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", size = 40.dp, iconSize = 30.dp, enabled = m != null) { actions.playPause() }
                IconButtonT(Icons.Rounded.SkipNext, "Next", size = 40.dp, iconSize = 28.dp, enabled = m != null) { actions.next() }
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
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(m?.isPlaying, m?.updateTimeMs) {
        while (m?.isPlaying == true) { now = SystemClock.elapsedRealtime(); delay(500) }
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

