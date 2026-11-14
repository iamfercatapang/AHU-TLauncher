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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
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
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Design canvas: everything is laid out for 1024×600 dp and scaled to the real screen. */
const val CANVAS_W = 1024f
const val CANVAS_H = 600f
val DOCK_H = 64.dp
val STATUS_H = 40.dp
private const val SPLIT_FRACTION = 0.36f

enum class Overlay { NONE, APPS, SETTINGS, MUSIC, SEARCH, BRIGHTNESS }

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
    val actions = remember(rawActions, routesOn) {
        object : HomeActions by rawActions {
            override fun openNavApp() { if (routesOn) { mapVisible = true; overlay = Overlay.SEARCH } else rawActions.openNavApp() }
            override fun openMapSettings() { settingsStart = com.ahu.tlauncher.ui.settings.Section.NAVIGATION; overlay = Overlay.SETTINGS }
        }
    }
    val toggle = { o: Overlay -> overlay = if (overlay == o) Overlay.NONE else o }
    // Instant switch: animating the width would re-layout the map every frame (janky on head units).
    val stageFraction = if (mapVisible) SPLIT_FRACTION else 1f

    Column(Modifier.fillMaxSize().background(T.colors.appBg)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val stageW = maxWidth * stageFraction
            val carRight = state.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT && mapVisible
            Row(Modifier.fillMaxSize()) {
                if (carRight) MapReveal(Modifier.weight(1f).fillMaxHeight()) { MapPane(state, actions, Modifier.fillMaxSize(), controlsOnLeft = true) }
                CarStage(
                    state, actions, expanded = !mapVisible, onToggleMap = { mapVisible = !mapVisible },
                    handleOnLeft = carRight,
                    modifier = Modifier.width(stageW).fillMaxHeight(),
                )
                if (!carRight && mapVisible) MapReveal(Modifier.weight(1f).fillMaxHeight()) { MapPane(state, actions, Modifier.fillMaxSize()) }
            }
            StatusBar(
                state, Modifier.fillMaxWidth().height(STATUS_H),
                mapSpan = if (!mapVisible) null else if (carRight) (0.dp to maxWidth - stageW) else (stageW to maxWidth),
                onObdClick = { settingsStart = com.ahu.tlauncher.ui.settings.Section.DIAGNOSTICS; overlay = Overlay.SETTINGS },
            )

            // Prototype motion: panels rise 18 dp while fading in over a dimmed backdrop.
            val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.roundToPx() }
            val panelIn = fadeIn(TMotion.panel()) + androidx.compose.animation.slideInVertically(TMotion.panel()) { rise }
            val panelOut = fadeOut(androidx.compose.animation.core.tween(150))
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay != Overlay.NONE && overlay != Overlay.BRIGHTNESS,
                enter = fadeIn(TMotion.panel()), exit = panelOut,
            ) { Box(Modifier.fillMaxSize().background(T.colors.scrim)) }
            // Tap anywhere outside an open panel to close it (that tap does nothing else).
            if (overlay != Overlay.NONE) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { overlay = Overlay.NONE } })
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
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.SEARCH,
                enter = panelIn, exit = panelOut,
                modifier = Modifier.align(if (carRight) Alignment.TopStart else Alignment.TopEnd).padding(top = STATUS_H + 4.dp),
            ) {
                SearchPanel(state, actions, onClose = { overlay = Overlay.NONE }, modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).padding(horizontal = 14.dp))
            }
            // Android Auto ready (phone's head unit server found on its hotspot)
            androidx.compose.animation.AnimatedVisibility(
                visible = state.aa is com.ahu.tlauncher.system.AaState.Ready && !state.aaDismissed && state.call == null,
                enter = fadeIn(TMotion.enter()), exit = fadeOut(TMotion.std()),
                // Over the map (never over the speed); at the bottom when the car is full screen.
                modifier = if (!mapVisible) Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                else if (state.settings.carSide == com.ahu.tlauncher.data.CarSide.RIGHT) Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = STATUS_H + 6.dp)
                else Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = STATUS_H + 6.dp),
            ) {
                AndroidAutoPrompt(state.aaReceiver, actions)
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
            onOpenSettings = { settingsStart = com.ahu.tlauncher.ui.settings.Section.DISPLAY; toggle(Overlay.SETTINGS) },
            onToggleApps = { toggle(Overlay.APPS) },
            onToggleMusic = { toggle(Overlay.MUSIC) },
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
    LaunchedEffect(Unit) { cover.animateTo(0f, TMotion.enter()) }
    Box(modifier) {
        content()
        if (cover.value > 0f) Box(Modifier.fillMaxSize().background(T.colors.appBg.copy(alpha = cover.value)))
    }
}

/** Full-width status row floating over the stage and map: link status · date · time. */
@Composable
private fun StatusBar(
    state: HomeUiState,
    modifier: Modifier,
    onObdClick: () -> Unit = {},
    /** Map area (start, end) when the map is visible; null when the car fills the screen. */
    mapSpan: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>? = null,
) {
    val time = state.now.format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mm a", Locale.US))
    BoxWithConstraints(modifier) {
        // car panel, top-left: OBD badge
        if (com.ahu.tlauncher.obd.ObdDisplay.show(state.obd.status)) {
            val obdX = if (mapSpan != null && mapSpan.first.value == 0f) mapSpan.second + 20.dp else 20.dp
            ObdBadge(state.obd.status, Modifier.align(Alignment.CenterStart).padding(start = obdX).pressable(onClick = onObdClick))
        }
        val links = @Composable { Connectivity(state) }
        val clock = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.weather?.let { w ->
                    val f = state.settings.tempUnit == com.ahu.tlauncher.data.TempUnit.F
                    val t = if (f) w.tempC * 9 / 5 + 32 else w.tempC
                    Icon(weatherIcon(w), w.kind.name.lowercase().replace('_', ' '), tint = T.colors.text, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${Math.round(t)} °${if (f) "F" else "C"}", style = TType.button, color = T.colors.text)
                    Spacer(Modifier.width(20.dp))
                }
                Text(state.now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())), style = TType.button, color = T.colors.muted)
                Spacer(Modifier.width(12.dp))
                Text(time, style = TType.button, color = T.colors.text)
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

/** Bluetooth and Wi-Fi status icons (TLauncher's own line icons). */
@Composable
private fun Connectivity(state: HomeUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val (btIcon, btDesc) = when (state.bluetooth) {
            BtStatus.CONNECTED -> com.ahu.tlauncher.ui.components.TIcons.Bluetooth to "Bluetooth connected"
            BtStatus.ON -> com.ahu.tlauncher.ui.components.TIcons.Bluetooth to "Bluetooth on"
            BtStatus.OFF -> com.ahu.tlauncher.ui.components.TIcons.BluetoothOff to "Bluetooth off"
        }
        Icon(btIcon, btDesc, tint = if (state.bluetooth == BtStatus.CONNECTED) T.colors.text else T.colors.muted, modifier = Modifier.size(20.dp))
        val w = state.wifi
        val icon = when {
            !w.enabled -> com.ahu.tlauncher.ui.components.TIcons.WifiOff
            !w.connected -> com.ahu.tlauncher.ui.components.TIcons.wifi(0)
            else -> com.ahu.tlauncher.ui.components.TIcons.wifi(w.level.coerceIn(1, 4).let { if (it >= 3) 3 else it })
        }
        Icon(icon, if (w.connected) "Wi-Fi connected" else if (w.enabled) "Wi-Fi not connected" else "Wi-Fi off",
            tint = if (w.connected) T.colors.text else T.colors.muted, modifier = Modifier.size(20.dp))
    }
}

private fun weatherIcon(w: com.ahu.tlauncher.system.Weather) = when (w.kind) {
    com.ahu.tlauncher.system.WeatherKind.CLEAR -> if (w.isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    com.ahu.tlauncher.system.WeatherKind.PARTLY_CLOUDY -> if (w.isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    com.ahu.tlauncher.system.WeatherKind.CLOUDY -> Icons.Rounded.Cloud
    com.ahu.tlauncher.system.WeatherKind.FOG -> Icons.Rounded.Dehaze
    com.ahu.tlauncher.system.WeatherKind.DRIZZLE, com.ahu.tlauncher.system.WeatherKind.RAIN -> Icons.Rounded.Umbrella
    com.ahu.tlauncher.system.WeatherKind.SNOW -> Icons.Rounded.AcUnit
    com.ahu.tlauncher.system.WeatherKind.STORM -> Icons.Rounded.Thunderstorm
}

/** Screen brightness for the current time of day (day level / night level). */
@Composable
private fun BrightnessPopup(state: HomeUiState, actions: HomeActions) {
    var value by remember(state.brightness) { mutableStateOf(state.brightness.toFloat()) }
    Column(Modifier.width(360.dp).clip(TShapes.surface).background(Palette.CarbonDark).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", style = TType.button, color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (state.brightnessNight) "Night level" else "Day level", style = TType.label, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}%", style = TType.button, color = androidx.compose.ui.graphics.Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BrightnessLow, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            androidx.compose.material3.Slider(
                value = value, onValueChange = { value = it; actions.setBrightness(it.toInt()) }, valueRange = 1f..100f,
                enabled = state.brightnessAllowed,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = androidx.compose.ui.graphics.Color.White, activeTrackColor = androidx.compose.ui.graphics.Color.White,
                    inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                ),
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

