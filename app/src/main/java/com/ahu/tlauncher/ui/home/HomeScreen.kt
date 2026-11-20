package com.ahu.tlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.SignalWifi0Bar
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.WifiOff
import com.ahu.tlauncher.system.BtStatus
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.Palette
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Design canvas: everything is laid out for 1024×600 dp and scaled to the real screen. */
const val CANVAS_W = 1024f
const val CANVAS_H = 600f
val DOCK_H = 72.dp
val STATUS_H = 48.dp
/** Car panel width in split view: the prototype's 580 of 1920 px. */
private const val SPLIT_FRACTION = 0.30f
/**
 * The split width the panel's contents are laid out for. At [SPLIT_FRACTION] each part (speed, callouts,
 * car, cards) is drawn smaller in place, at the same height as in full screen, and grows back to full
 * size as the panel widens.
 */
private const val SPLIT_DESIGN_FRACTION = 0.36f
private const val SPLIT_SCALE = SPLIT_FRACTION / SPLIT_DESIGN_FRACTION

/** Contents scale for a panel progress (0 = split view, 1 = full screen). */
private fun panelScale(progress: Float): Float = SPLIT_SCALE + (1f - SPLIT_SCALE) * progress
/** A release faster than this (panel widths per second) settles in the swipe's direction. */
private const val FLING_PER_S = 0.8f
private const val MIN_SETTLE_MS = 150
private const val LOW_PERF_SETTLE_MS = 150
/** Strip of the map, next to the car panel, that also grabs the panel. */
private val MAP_GRAB_W = 24.dp

/**
 * Lays the panel out at [edge] × the available width. Its contents are measured 1 / [scale] times wider
 * (full height); [CarStage] then draws each part [scale] times smaller so it fits. The clip keeps the
 * wider layout from drawing or taking touches past the panel's edge.
 */
private fun Modifier.panelWidth(edge: () -> Float, scale: () -> Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val w = (constraints.maxWidth * edge()).roundToInt().coerceIn(0, constraints.maxWidth)
    val inner = (w / scale()).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = inner, maxWidth = inner))
    layout(w, placeable.height) { placeable.place(0, 0) }
}

/** What a horizontal swipe on the car panel does: start, move by dx px, end with a velocity in px/s. */
private class PanelSwipe(val onStart: () -> Unit, val onDrag: (Float) -> Unit, val onEnd: (Float) -> Unit)

/**
 * Horizontal swipes for [PanelSwipe]. Children that scroll sideways themselves (the card pager) get the
 * swipe first; a swipe also cancels a tap on whatever it started on. Velocity is tracked on the summed
 * movement, because the panel's own layout can move under the finger when a swipe starts from full screen.
 */
private fun Modifier.panelSwipe(swipe: PanelSwipe): Modifier = pointerInput(swipe) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var over = 0f
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, o ->
            change.consume(); over = o
        } ?: return@awaitEachGesture
        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
        var moved = over
        tracker.addPosition(start.uptimeMillis, androidx.compose.ui.geometry.Offset(moved, 0f))
        swipe.onStart()
        swipe.onDrag(over)
        val finished = horizontalDrag(start.id) { change ->
            val dx = change.positionChange().x
            change.consume()
            moved += dx
            tracker.addPosition(change.uptimeMillis, androidx.compose.ui.geometry.Offset(moved, 0f))
            swipe.onDrag(dx)
        }
        swipe.onEnd(if (finished) tracker.calculateVelocity().x else 0f)
    }
}

/** Width of the status bar popups (notifications, Bluetooth, Wi-Fi, mobile, weather). */
internal val STATUS_POPUP_W = 380.dp

enum class Overlay { NONE, APPS, SETTINGS, MUSIC, SEARCH, BRIGHTNESS, VOLUME, CARD_MUSIC, NOTIFICATIONS, BLUETOOTH, WIFI, CELL, WEATHER }

/** Small popups under the status bar: no dimmed backdrop, like the notifications list. */
private val STATUS_POPUPS = setOf(Overlay.NOTIFICATIONS, Overlay.BLUETOOTH, Overlay.WIFI, Overlay.CELL, Overlay.WEATHER)

@Composable
fun HomeScreen(
    state: HomeUiState,
    rawActions: HomeActions,
    initialOverlay: Overlay = Overlay.NONE,
    initialMapVisible: Boolean = true,
    initialSettings: com.ahu.tlauncher.ui.settings.Section = com.ahu.tlauncher.ui.settings.Section.DISPLAY,
) {
    var overlay by remember { mutableStateOf(initialOverlay) }
    var mapVisible by rememberSaveable { mutableStateOf(initialMapVisible) }
    var toast by remember { mutableStateOf<String?>(null) }
    var settingsStart by remember { mutableStateOf(initialSettings) }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    // Bring the map back when Maps/Waze starts guiding.
    LaunchedEffect(state.nav != null) { if (state.nav != null) mapVisible = true }
    LaunchedEffect(state.route != null) { if (state.route != null) mapVisible = true }
    // With the Google map, "Navigate" opens the dashboard's own search (route drawn here, voice by Maps/Waze).
    val routesOn = state.routesEnabled
    val pickingNow = androidx.compose.runtime.rememberUpdatedState(state.picking)
    val actions = remember(rawActions, routesOn) {
        object : HomeActions by rawActions {
            override fun openNavApp() { if (routesOn) { mapVisible = true; overlay = Overlay.SEARCH } else rawActions.openNavApp() }
            override fun openMapSettings() { settingsStart = com.ahu.tlauncher.ui.settings.Section.NAVIGATION; overlay = Overlay.SETTINGS }
            // Fuel stations are listed in the search panel, over the map.
            override fun findFuel() { rawActions.findFuel(); if (routesOn) { mapVisible = true; overlay = Overlay.SEARCH } }
            override fun openCardPlayer() { overlay = if (overlay == Overlay.CARD_MUSIC) Overlay.NONE else Overlay.CARD_MUSIC }
            // Settings → "Pin on map": close Settings so the map is free; it opens again once the spot is saved or picking is cancelled.
            override fun pickOnMap(spot: com.ahu.tlauncher.data.SavedSpot): Boolean {
                if (!rawActions.pickOnMap(spot)) return false
                mapVisible = true; overlay = Overlay.NONE
                return true
            }
            override fun cancelPicking() { rawActions.cancelPicking(); backToPlaces() }
            // Saved from Settings' pin button: back to Settings. From any other pin: stay on the map (a toast confirms it).
            override fun savePinAs(spot: com.ahu.tlauncher.data.SavedSpot) {
                val fromSettings = pickingNow.value != null
                rawActions.savePinAs(spot)
                if (fromSettings) backToPlaces()
            }
            private fun backToPlaces() { settingsStart = com.ahu.tlauncher.ui.settings.Section.NAVIGATION; mapVisible = true; overlay = Overlay.SETTINGS }
        }
    }
    BackHandler(enabled = overlay == Overlay.NONE && state.picking != null) { actions.cancelPicking() }
    // "Find a gas station" said by voice: the stations show in the search panel.
    LaunchedEffect(state.searchRequest) { if (state.searchRequest > 0 && routesOn) { mapVisible = true; overlay = Overlay.SEARCH } }
    // Settings, the dock's Music panel and search live on the map side: only in split view.
    LaunchedEffect(mapVisible) {
        if (!mapVisible && overlay in setOf(Overlay.SETTINGS, Overlay.MUSIC, Overlay.SEARCH)) overlay = Overlay.NONE
    }
    val toggle = { o: Overlay -> overlay = if (overlay == o) Overlay.NONE else o }
    // Layout still switches instantly: resizing the map every frame is janky on head units.
    // The prototype's motion is drawn on top instead. The car panel's edge sweeps between split
    // and full screen (500 ms, ease-out cubic) by clipping the panel, while the map stays laid out
    // at its split size underneath and fades in 200 ms behind the edge.
    val stageFraction = if (mapVisible) SPLIT_FRACTION else 1f
    val edge = remember { androidx.compose.animation.core.Animatable(stageFraction) }
    // Swiping the panel: while a finger holds it, the edge follows the finger (fraction of the width, else -1).
    var dragEdge by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val panelEdge = { if (dragEdge >= 0f) dragEdge else edge.value }
    // 0 = split view, 1 = full screen: drives the car's turn and the cards' cross-fade during a swipe or sweep.
    val panelProgress = { ((panelEdge() - SPLIT_FRACTION) / (1f - SPLIT_FRACTION)).coerceIn(0f, 1f) }
    // Low-performance mode: the panel jumps (the map still fades in behind it).
    val lowPerf = com.ahu.tlauncher.ui.theme.LocalLowPerf.current
    LaunchedEffect(mapVisible) {
        // A swipe moves the edge itself, and its release animation is already heading here.
        if (dragEdge >= 0f || (edge.isRunning && edge.targetValue == stageFraction)) return@LaunchedEffect
        if (lowPerf) edge.snapTo(stageFraction) else edge.animateTo(stageFraction, TMotion.split())
    }
    // Keep the map while the panel is still sweeping over it. Read per frame only by this derived flag.
    val mapComposed by remember { androidx.compose.runtime.derivedStateOf { mapVisible || panelEdge() < 0.999f } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val panelBrush = stageBrush()

    Column(Modifier.fillMaxSize().background(T.colors.appBg)) {
        // Where each status icon is drawn (window coordinates), so its popup opens right under it.
        val anchors = remember { androidx.compose.runtime.mutableStateMapOf<Overlay, androidx.compose.ui.geometry.Rect>() }
        var bodyOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { bodyOrigin = it.positionInRoot() }) {
            val stageW = maxWidth * stageFraction
            val sideRight = state.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT
            val carRight = sideRight && mapVisible
            // Swipe the car panel between split and full screen. The edge follows the finger; on release
            // it settles on whichever side the swipe was heading (a quick flick) or is nearer to.
            val widthPx = constraints.maxWidth.toFloat()
            val swipe = remember(widthPx, sideRight, lowPerf) {
                val toward = if (sideRight) -1f else 1f // finger direction that widens the panel
                PanelSwipe(
                    onStart = {
                        scope.launch { edge.stop() }
                        dragEdge = edge.value
                        // From full screen the layout goes to split right away, so the map is there underneath.
                        mapVisible = true
                    },
                    onDrag = { dx -> dragEdge = (dragEdge + toward * dx / widthPx).coerceIn(SPLIT_FRACTION, 1f) },
                    onEnd = { vx ->
                        val from = dragEdge
                        val v = toward * vx / widthPx
                        val full = if (kotlin.math.abs(v) >= FLING_PER_S) v > 0 else from > (SPLIT_FRACTION + 1f) / 2
                        val target = if (full) 1f else SPLIT_FRACTION
                        val ms = if (lowPerf) LOW_PERF_SETTLE_MS
                        else (TMotion.SPLIT_MS * kotlin.math.abs(target - from) / (1f - SPLIT_FRACTION)).toInt().coerceAtLeast(MIN_SETTLE_MS)
                        scope.launch {
                            edge.snapTo(from)
                            dragEdge = -1f
                            mapVisible = !full
                            edge.animateTo(target, androidx.compose.animation.core.tween(ms, easing = TMotion.EaseOutCubic))
                        }
                    },
                )
            }
            if (mapComposed) MapReveal(Modifier.align(if (sideRight) Alignment.TopStart else Alignment.TopEnd).width(maxWidth * (1 - SPLIT_FRACTION)).fillMaxHeight()) {
                MapPane(state, actions, Modifier.fillMaxSize(), controlsOnLeft = sideRight)
            }
            // Panel: background and content clipped to the animated edge (draw phase only, no re-layout).
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    val w = size.width * panelEdge()
                    val left = if (sideRight) size.width - w else 0f
                    clipRect(left = left, right = left + w) {
                        drawRect(panelBrush)
                        this@drawWithContent.drawContent()
                    }
                },
            ) {
                // The panel is laid out at the edge's live width, so everything in it follows the finger
                // (and the sweep): speed, tell-tales, the car (turning with the progress) and the cards.
                CarStage(
                    state, actions, expanded = !mapVisible, onToggleMap = { mapVisible = !mapVisible },
                    handleOnLeft = carRight,
                    drawBackground = false,
                    modifier = Modifier.align(if (carRight) Alignment.TopEnd else Alignment.TopStart)
                        .panelWidth(panelEdge, scale = { panelScale(panelProgress()) }).fillMaxHeight(),
                    panelSwipe = Modifier.panelSwipe(swipe),
                    contentScale = { panelScale(panelProgress()) },
                    progress = panelProgress,
                )
            }
            // In split view, a thin strip of the map next to the panel grabs it too (the rest of the map pans as usual).
            if (mapVisible) Box(
                Modifier.align(Alignment.TopStart).padding(top = STATUS_H)
                    .offset(x = if (carRight) maxWidth - stageW - MAP_GRAB_W else stageW)
                    .width(MAP_GRAB_W).fillMaxHeight()
                    .panelSwipe(swipe),
            )
            StatusBar(
                state, Modifier.fillMaxWidth().height(STATUS_H),
                mapSpan = if (!mapVisible) null else if (carRight) (0.dp to maxWidth - stageW) else (stageW to maxWidth),
                onObdClick = { settingsStart = com.ahu.tlauncher.ui.settings.Section.CAR; overlay = Overlay.SETTINGS },
                onBellClick = { toggle(Overlay.NOTIFICATIONS) },
                onStatusClick = toggle,
                onAnchor = { o, r -> if (anchors[o] != r) anchors[o] = r },
            )

            // Prototype motion: panels rise 18 dp while fading in over a dimmed backdrop.
            val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.roundToPx() }
            val panelIn = fadeIn(TMotion.panel()) + androidx.compose.animation.slideInVertically(TMotion.panel()) { rise }
            val panelOut = fadeOut(androidx.compose.animation.core.tween(150))
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay !in setOf(Overlay.NONE, Overlay.BRIGHTNESS, Overlay.VOLUME, Overlay.CARD_MUSIC) + STATUS_POPUPS,
                enter = fadeIn(TMotion.panel()), exit = panelOut,
            ) { Box(Modifier.fillMaxSize().background(T.colors.scrim)) }
            // Tap anywhere outside an open panel to close it (that tap does nothing else).
            if (overlay != Overlay.NONE) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { overlay = Overlay.NONE } })
            }

            // Full-screen card row geometry (see CarStage: max 660 dp wide, music card 1.6 : 1 with 12 dp gap).
            val fullCardsW = androidx.compose.ui.unit.min(660.dp, maxWidth - 32.dp)
            val fullMusicW = (fullCardsW - 12.dp) * (1.6f / 2.6f)
            // notifications, under the bell in the status bar
            // Status popups open centred under their own icon (like the car panel's music player sits
            // exactly over its card), kept 8 dp inside the screen. Until an icon has been laid out,
            // fall back to the status bar's side of the screen.
            val popupW = STATUS_POPUP_W
            val under = { o: Overlay, fallback: Modifier ->
                if (o !in anchors) fallback
                else Modifier.align(Alignment.TopStart).offset {
                    val r = anchors[o] ?: return@offset IntOffset.Zero
                    val w = popupW.roundToPx()
                    val margin = 8.dp.roundToPx()
                    val x = (r.center.x - bodyOrigin.x - w / 2f).roundToInt().coerceIn(margin, maxOf(margin, constraints.maxWidth - w - margin))
                    IntOffset(x, (r.bottom - bodyOrigin.y).roundToInt() + 4.dp.roundToPx())
                }
            }
            val leftFallback = Modifier.align(Alignment.TopStart).padding(top = STATUS_H, start = (if (mapVisible && !carRight) stageW else 0.dp) + 12.dp)
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.NOTIFICATIONS,
                enter = panelIn, exit = panelOut,
                modifier = under(Overlay.NOTIFICATIONS, leftFallback),
            ) {
                NotificationsPopup(state, actions, onClose = { overlay = Overlay.NONE })
            }
            val linksFallback = if (mapVisible) leftFallback else Modifier.align(Alignment.TopEnd).padding(top = STATUS_H, end = 12.dp)
            val closeStatus = { overlay = Overlay.NONE }
            androidx.compose.animation.AnimatedVisibility(overlay == Overlay.BLUETOOTH, under(Overlay.BLUETOOTH, linksFallback), enter = panelIn, exit = panelOut) {
                BluetoothPopup(state, actions, onClose = closeStatus)
            }
            androidx.compose.animation.AnimatedVisibility(overlay == Overlay.WIFI, under(Overlay.WIFI, linksFallback), enter = panelIn, exit = panelOut) {
                WifiPopup(state, actions, onClose = closeStatus)
            }
            androidx.compose.animation.AnimatedVisibility(overlay == Overlay.CELL, under(Overlay.CELL, linksFallback), enter = panelIn, exit = panelOut) {
                CellPopup(state, actions, onClose = closeStatus)
            }
            androidx.compose.animation.AnimatedVisibility(
                overlay == Overlay.WEATHER,
                under(Overlay.WEATHER, Modifier.align(Alignment.TopEnd).padding(top = STATUS_H, end = (if (mapVisible && carRight) stageW else 0.dp) + 12.dp)),
                enter = panelIn, exit = panelOut,
            ) {
                WeatherPopup(state, onClose = closeStatus)
            }
            // volume slider, above the dock's speaker
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.VOLUME,
                enter = panelIn, exit = panelOut,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 10.dp),
            ) {
                VolumePopup(state, actions)
            }
            // "Now playing" over the car panel (split view), or over the car area when it's full screen
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.CARD_MUSIC,
                enter = panelIn, exit = panelOut,
                // Full screen: exactly over the music card (left of the Navigate card), growing upwards.
                modifier = if (!mapVisible) Modifier.align(Alignment.BottomCenter).offset(x = -(fullCardsW - fullMusicW) / 2)
                else Modifier.align(if (carRight) Alignment.BottomEnd else Alignment.BottomStart),
            ) {
                CarMusicPlayer(
                    state, actions, onClose = { overlay = Overlay.NONE },
                    modifier = if (!mapVisible) Modifier.width(fullMusicW).height(maxHeight - STATUS_H - 96.dp).padding(bottom = 10.dp)
                    else Modifier.width(stageW).height(maxHeight - STATUS_H - 80.dp).padding(horizontal = 12.dp).padding(bottom = 10.dp),
                )
            }
            // brightness slider, above the dock's sun button
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.BRIGHTNESS,
                enter = panelIn, exit = panelOut,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp),
            ) {
                BrightnessPopup(state, actions)
            }

            // overlays inside the body area
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.SETTINGS,
                enter = panelIn, exit = panelOut,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                com.ahu.tlauncher.ui.settings.SettingsScreen(
                    state, actions, onClose = { overlay = Overlay.NONE }, initial = settingsStart,
                    modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).height(maxHeight - STATUS_H).padding(8.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.MUSIC,
                enter = panelIn, exit = panelOut,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                MusicPanel(state, actions, onClose = { overlay = Overlay.NONE }, modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).height(maxHeight - STATUS_H).padding(10.dp))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.APPS,
                enter = panelIn, exit = panelOut,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                AppDrawer(
                    apps = state.apps,
                    pinned = state.settings.pinnedApps,
                    onLaunch = { overlay = Overlay.NONE; actions.launch(it) },
                    onTogglePin = { app ->
                        val wasPinned = app.key in state.settings.pinnedApps
                        actions.togglePin(app)
                        toast = if (wasPinned) "${app.label} removed from dock" else "${app.label} pinned to dock"
                    },
                    onClose = { overlay = Overlay.NONE },
                    onAppInfo = { overlay = Overlay.NONE; actions.appInfo(it) },
                    onUninstall = actions::uninstallApp,
                    modifier = Modifier.width(maxWidth * 0.74f).height(maxHeight * 0.8f).padding(bottom = 10.dp),
                )
            }
            // destination search, over the map
            // (a floating card on the map's free side, away from its buttons, like the prototype)
            val searchW = maxWidth * (1 - SPLIT_FRACTION)
            val searchMaxH = maxHeight - STATUS_H - 16.dp
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.SEARCH,
                enter = navEnter(), exit = navExit(),
                modifier = Modifier.align(if (carRight) Alignment.TopStart else Alignment.TopEnd).padding(top = STATUS_H + 4.dp),
            ) {
                Box(Modifier.width(searchW).padding(horizontal = 12.dp)) {
                    SearchPanel(
                        state, actions, onClose = { overlay = Overlay.NONE },
                        modifier = Modifier.align(if (carRight) Alignment.TopEnd else Alignment.TopStart).heightIn(max = searchMaxH),
                    )
                }
            }
            // Calls sit above everything else in the body.
            androidx.compose.animation.AnimatedVisibility(
                visible = state.call != null,
                enter = fadeIn(TMotion.enter()), exit = fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            ) {
                // Keep showing the last call while fading out.
                val last = remember { arrayOfNulls<CallUi>(1) }
                if (state.call != null) last[0] = state.call
                last[0]?.let { CallCard(it, actions) }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showUpdateToast(state.update, state.updateDismissedCode) && overlay == Overlay.NONE && state.call == null,
                enter = fadeIn(TMotion.std()), exit = fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                UpdateToast(state.update, actions)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = toast != null, enter = fadeIn(TMotion.std()), exit = fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            ) {
                Box(Modifier.background(Palette.CarbonDark, TShapes.button).padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(toast ?: "", style = TType.label, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Dock(
            state, actions,
            overlay = overlay,
            // Settings and the dock's Music panel open in split view: from full screen, glide back first.
            onOpenSettings = {
                settingsStart = com.ahu.tlauncher.ui.settings.Section.DISPLAY
                if (!mapVisible) { mapVisible = true; overlay = Overlay.SETTINGS } else toggle(Overlay.SETTINGS)
            },
            onToggleApps = { toggle(Overlay.APPS) },
            onToggleMusic = { if (!mapVisible) { mapVisible = true; overlay = Overlay.MUSIC } else toggle(Overlay.MUSIC) },
            onToggleVolume = { toggle(Overlay.VOLUME) },
            onToggleBrightness = { toggle(Overlay.BRIGHTNESS) },
            onLaunch = { app: AppEntry -> overlay = Overlay.NONE; actions.launch(app) },
            modifier = Modifier.fillMaxWidth().height(DOCK_H),
        )
    }
}

/**
 * When the map comes back, it fades in from the canvas colour. A cover drawn above the map is
 * faded out, because the map renders in its own surface and ignores alpha on itself.
 */
@Composable
private fun MapReveal(modifier: Modifier, content: @Composable () -> Unit) {
    // Previews/screenshots show the map straight away (they capture the first frame).
    val cover = remember { androidx.compose.animation.core.Animatable(1f) }
    if (LocalInspectionMode.current) { Box(modifier) { content() }; return }
    // Trails the car panel's edge, like the prototype's delayed map resize.
    // Low-performance mode: no lag (the panel edge jumps), just the short fade.
    val lag = if (com.ahu.tlauncher.ui.theme.LocalLowPerf.current) 0 else TMotion.MAP_LAG_MS
    LaunchedEffect(Unit) { cover.animateTo(0f, androidx.compose.animation.core.tween(TMotion.ENTER_MS, delayMillis = lag, easing = TMotion.EaseOut)) }
    Box(modifier) {
        content()
        if (cover.value > 0f) Box(Modifier.fillMaxSize().background(T.colors.appBg.copy(alpha = cover.value)))
    }
}

/**
 * A soft shadow behind every icon and letter drawn inside, so the status bar stays readable over the map and
 * the 3D scene: dark at night, a light halo in the day theme. Blurred on Android 12+; before that, a ring of
 * faint offset copies gives a similar soft edge. (Not in previews and screenshot tests.)
 */
@Composable
private fun Modifier.statusShadow(): Modifier {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) return this
    val color = if (T.colors.isDark) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        layer.colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color)
        val r = 2.dp.toPx()
        val down = 1.dp.toPx()
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            layer.renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
            translate(top = down) { drawLayer(layer) }
        } else {
            layer.alpha = 0.22f
            for (i in 0 until 8) {
                val a = i * Math.PI / 4
                translate(left = (kotlin.math.cos(a) * r * 0.8).toFloat(), top = (kotlin.math.sin(a) * r * 0.8).toFloat() + down) {
                    drawLayer(layer)
                }
            }
        }
        drawContent()
    }
}

/** Status bar text: a bit larger than buttons, readable at arm's length on a 9–10" screen. */
private val STATUS_TEXT = TType.button.copy(fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))

/** Full-width status row floating over the stage and map: link status · date · time. */
@Composable
private fun StatusBar(
    state: HomeUiState,
    modifier: Modifier,
    onObdClick: () -> Unit = {},
    onBellClick: () -> Unit = {},
    onStatusClick: (Overlay) -> Unit = {},
    /** Reports where each tappable status item is drawn (window coordinates). */
    onAnchor: (Overlay, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
    /** Map area (start, end) when the map is visible; null when the car fills the screen. */
    mapSpan: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>? = null,
) {
    val time = state.now.format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mm a", Locale.US))
    // Only the connection status, so engine readings don't redraw the status bar.
    val obdStatus by remember(state.live) { androidx.compose.runtime.derivedStateOf { state.obd.status } }
    BoxWithConstraints(modifier.statusShadow()) {
        // car panel, top-left: OBD badge
        if (com.ahu.tlauncher.obd.ObdDisplay.show(obdStatus)) {
            val obdX = if (mapSpan != null && mapSpan.first.value == 0f) mapSpan.second + 20.dp else 20.dp
            ObdBadge(obdStatus, Modifier.align(Alignment.CenterStart).padding(start = obdX).pressable(onClick = onObdClick))
        }
        val links = @Composable { Connectivity(state, onBellClick, onStatusClick, onAnchor) }
        val clock = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CodingChip(state)
                state.weather?.let { w ->
                    val f = state.settings.tempUnit == com.ahu.tlauncher.data.TempUnit.F
                    val t = if (f) w.tempC * 9 / 5 + 32 else w.tempC
                    // Tap for the weather popup (details and the next hours).
                    Row(
                        Modifier.onGloballyPositioned { onAnchor(Overlay.WEATHER, it.boundsInRoot()) }
                            .pressable { onStatusClick(Overlay.WEATHER) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(weatherIcon(w), w.kind.name.lowercase().replace('_', ' '), tint = T.colors.text, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${Math.round(t)} °${if (f) "F" else "C"}", style = STATUS_TEXT, color = T.colors.text)
                    }
                    Spacer(Modifier.width(20.dp))
                }
                Text(state.now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())), style = STATUS_TEXT, color = T.colors.muted)
                Spacer(Modifier.width(12.dp))
                Text(time, style = STATUS_TEXT, color = T.colors.text)
            }
        }
        if (mapSpan != null) {
            // prototype: Bluetooth · Wi-Fi at the map's top-left, temperature + time at its top-right
            Box(Modifier.align(Alignment.CenterStart).padding(start = mapSpan.first + 20.dp)) { links() }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = maxWidth - mapSpan.second + 20.dp)) { clock() }
        } else {
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                links(); Spacer(Modifier.width(24.dp)); clock()
            }
        }
    }
}

/** Number coding on the plate's day: "Coding today 7 AM–10 AM, 5 PM–8 PM", amber "Coding until 10 AM" during the hours. */
@Composable
private fun CodingChip(state: HomeUiState) {
    val s = state.settings
    val hours = remember(s.codingHours) { com.ahu.tlauncher.system.Coding.parseHours(s.codingHours) }
    val st = com.ahu.tlauncher.system.Coding.status(s.codingDigit, hours, state.now)
    val text = com.ahu.tlauncher.system.Coding.label(st, is24h()) ?: return
    val now = st is com.ahu.tlauncher.system.Coding.Status.Now
    val amber = androidx.compose.ui.graphics.Color(0xFFF5A623)
    Row(
        Modifier.clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (now) amber.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Block, null, tint = if (now) amber else T.colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = TType.label, color = if (now) amber else T.colors.muted, maxLines = 1)
    }
    Spacer(Modifier.width(16.dp))
}

/** Bluetooth and Wi-Fi status icons (TLauncher's own line icons). */
@Composable
private fun Connectivity(
    state: HomeUiState,
    onBellClick: () -> Unit = {},
    onStatusClick: (Overlay) -> Unit = {},
    onAnchor: (Overlay, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
) {
    // Each item sits in a 40 dp tap target (about 7.5 mm on a 9" screen); the icons end up 16 dp apart.
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (btIcon, btDesc) = when (state.bluetooth) {
            BtStatus.CONNECTED -> com.ahu.tlauncher.ui.components.TIcons.Bluetooth to "Bluetooth connected"
            BtStatus.ON -> com.ahu.tlauncher.ui.components.TIcons.Bluetooth to "Bluetooth on"
            BtStatus.OFF -> com.ahu.tlauncher.ui.components.TIcons.BluetoothOff to "Bluetooth off"
        }
        Box(Modifier.size(40.dp).onGloballyPositioned { onAnchor(Overlay.BLUETOOTH, it.boundsInRoot()) }.pressable { onStatusClick(Overlay.BLUETOOTH) }, contentAlignment = Alignment.Center) {
            Icon(btIcon, btDesc, tint = if (state.bluetooth == BtStatus.CONNECTED) T.colors.text else T.colors.muted, modifier = Modifier.size(24.dp))
        }
        val w = state.wifi
        val icon = when {
            !w.enabled -> com.ahu.tlauncher.ui.components.TIcons.WifiOff
            !w.connected -> com.ahu.tlauncher.ui.components.TIcons.wifi(0)
            else -> com.ahu.tlauncher.ui.components.TIcons.wifi(w.level.coerceIn(1, 4).let { if (it >= 3) 3 else it })
        }
        Box(Modifier.size(40.dp).onGloballyPositioned { onAnchor(Overlay.WIFI, it.boundsInRoot()) }.pressable { onStatusClick(Overlay.WIFI) }, contentAlignment = Alignment.Center) {
            Icon(icon, if (w.connected) "Wi-Fi connected" else if (w.enabled) "Wi-Fi not connected" else "Wi-Fi off",
                tint = if (w.connected) T.colors.text else T.colors.muted, modifier = Modifier.size(24.dp))
        }
        // mobile signal (only with a SIM): type label + four bars
        state.cell?.let { c ->
            Row(Modifier.height(40.dp).onGloballyPositioned { onAnchor(Overlay.CELL, it.boundsInRoot()) }.pressable { onStatusClick(Overlay.CELL) }.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                c.type?.let { Text(it, style = TType.label, color = T.colors.text, modifier = Modifier.padding(end = 1.dp)) }
                Box(Modifier.size(24.dp)) {
                    Icon(com.ahu.tlauncher.ui.components.TIcons.signalBars(c.level, false), null, tint = T.colors.muted, modifier = Modifier.size(24.dp))
                    Icon(com.ahu.tlauncher.ui.components.TIcons.signalBars(c.level, true), "Mobile signal ${c.level} of 4", tint = T.colors.text, modifier = Modifier.size(24.dp))
                }
            }
        }
        // notification bell, with a dot when there's something unread
        Box(Modifier.size(40.dp).onGloballyPositioned { onAnchor(Overlay.NOTIFICATIONS, it.boundsInRoot()) }.pressable(onClick = onBellClick), contentAlignment = Alignment.Center) {
            Icon(com.ahu.tlauncher.ui.components.TIcons.Bell, "Notifications", tint = if (state.inbox.isEmpty()) T.colors.muted else T.colors.text, modifier = Modifier.size(24.dp))
            if (state.inbox.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(T.colors.accent))
        }
    }
}

private fun weatherIcon(w: com.ahu.tlauncher.system.Weather) = weatherIcon(w.kind, w.isDay)

internal fun weatherIcon(kind: com.ahu.tlauncher.system.WeatherKind, isDay: Boolean) = when (kind) {
    com.ahu.tlauncher.system.WeatherKind.CLEAR -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    com.ahu.tlauncher.system.WeatherKind.PARTLY_CLOUDY -> if (isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    com.ahu.tlauncher.system.WeatherKind.CLOUDY -> Icons.Rounded.Cloud
    com.ahu.tlauncher.system.WeatherKind.FOG -> Icons.Rounded.Dehaze
    com.ahu.tlauncher.system.WeatherKind.DRIZZLE, com.ahu.tlauncher.system.WeatherKind.RAIN -> Icons.Rounded.Umbrella
    com.ahu.tlauncher.system.WeatherKind.SNOW -> Icons.Rounded.AcUnit
    com.ahu.tlauncher.system.WeatherKind.STORM -> Icons.Rounded.Thunderstorm
}

/** Volume slider (prototype popup): "Volume 70%", tap the speaker to mute. */
@Composable
private fun VolumePopup(state: HomeUiState, actions: HomeActions) {
    val v = state.volume
    val white = androidx.compose.ui.graphics.Color.White
    var value by remember(v.level, v.muted) { mutableStateOf(if (v.muted) 0f else v.level.toFloat()) }
    val pct = if (v.max == 0) 0 else (value * 100 / v.max).toInt()
    Column(Modifier.width(300.dp).clip(TShapes.surface).background(androidx.compose.ui.graphics.Color(0xFF22252B)).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Volume", style = TType.button, color = white)
            Spacer(Modifier.weight(1f))
            Text("$pct%", style = TType.label, color = white.copy(alpha = 0.6f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).pressable(onClick = actions::toggleMute), contentAlignment = Alignment.Center) {
                Icon(if (v.muted || v.level == 0) com.ahu.tlauncher.ui.components.TIcons.SpeakerOff else com.ahu.tlauncher.ui.components.TIcons.Speaker,
                    if (v.muted) "Unmute" else "Mute", tint = white, modifier = Modifier.size(20.dp))
            }
            com.ahu.tlauncher.ui.components.ThinSlider(
                value, { value = it; actions.setVolume(Math.round(it)) }, range = 0f..v.max.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
        }
    }
}

/** Screen brightness for the current time of day (day level / night level). */
@Composable
private fun BrightnessPopup(state: HomeUiState, actions: HomeActions) {
    var value by remember(state.brightness) { mutableStateOf(state.brightness.toFloat()) }
    Column(Modifier.width(360.dp).clip(TShapes.surface).background(androidx.compose.ui.graphics.Color(0xFF22252B)).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", style = TType.button, color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (state.brightnessNight) "Night level" else "Day level", style = TType.label, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}%", style = TType.button, color = androidx.compose.ui.graphics.Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BrightnessLow, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            com.ahu.tlauncher.ui.components.ThinSlider(
                value, { value = it; actions.setBrightness(it.toInt()) }, range = 1f..100f, enabled = state.brightnessAllowed,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Icon(Icons.Rounded.BrightnessHigh, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
        }
        if (!state.brightnessAllowed) {
            Row(
                Modifier.fillMaxWidth().clip(TShapes.button).background(androidx.compose.ui.graphics.Color(0xFF3E6AE1)).pressable(onClick = actions::requestBrightnessPermission).padding(10.dp),
                horizontalArrangement = Arrangement.Center,
            ) { Text("Allow TLauncher to change brightness", style = TType.button, color = androidx.compose.ui.graphics.Color.White) }
        } else {
            Text(
                if (state.brightnessNight) "Used from sunset to sunrise. The day level comes back in the morning." else "Used during the day. Set a separate level after sunset.",
                style = TType.caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun is24h(): Boolean {
    val ctx = LocalContext.current
    return if (LocalInspectionMode.current) false else android.text.format.DateFormat.is24HourFormat(ctx)
}

