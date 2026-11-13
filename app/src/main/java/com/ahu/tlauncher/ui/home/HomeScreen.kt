package com.ahu.tlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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

enum class Overlay { NONE, APPS, SETTINGS, MUSIC }

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
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
                onObdClick = { settingsStart = com.ahu.tlauncher.ui.settings.Section.DIAGNOSTICS; overlay = Overlay.SETTINGS },
            )

            // overlays inside the body area
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.SETTINGS,
                enter = fadeIn(TMotion.std()),
                exit = fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                com.ahu.tlauncher.ui.settings.SettingsScreen(
                    state, actions, onClose = { overlay = Overlay.NONE }, initial = settingsStart,
                    modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).height(maxHeight - STATUS_H).padding(8.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.MUSIC,
                enter = fadeIn(TMotion.std()),
                exit = fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                MusicPanel(state, actions, onClose = { overlay = Overlay.NONE }, modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).padding(12.dp))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.APPS,
                enter = fadeIn(TMotion.std()),
                exit = fadeOut(TMotion.std()),
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
                    modifier = Modifier.width(maxWidth * 0.74f).height(maxHeight * 0.8f).padding(bottom = 10.dp),
                )
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
    val cover = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) { cover.animateTo(0f, TMotion.enter()) }
    Box(modifier) {
        content()
        if (cover.value > 0f) Box(Modifier.fillMaxSize().background(T.colors.appBg.copy(alpha = cover.value)))
    }
}

/** Full-width status row floating over the stage and map: link status · date · time. */
@Composable
private fun StatusBar(state: HomeUiState, modifier: Modifier, onObdClick: () -> Unit = {}) {
    val time = state.now.format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mma", Locale.US)).lowercase()
    Box(modifier.padding(horizontal = 20.dp)) {
        if (com.ahu.tlauncher.obd.ObdDisplay.show(state.obd.status)) {
            ObdBadge(state.obd.status, Modifier.align(Alignment.CenterStart).pressable(onClick = onObdClick))
        }
        // right: weather · date
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            state.weather?.let { w ->
                val f = state.settings.tempUnit == com.ahu.tlauncher.data.TempUnit.F
                val t = if (f) w.tempC * 9 / 5 + 32 else w.tempC
                Icon(weatherIcon(w), w.kind.name.lowercase().replace('_', ' '), tint = T.colors.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("${Math.round(t)}°", style = TType.button, color = T.colors.text)
                Spacer(Modifier.width(14.dp))
            }
            Text(state.now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())), style = TType.button, color = T.colors.text)
        }
        // centre: Bluetooth · time · Wi-Fi
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            val (btIcon, btDesc) = when (state.bluetooth) {
                BtStatus.CONNECTED -> Icons.Rounded.BluetoothConnected to "Bluetooth connected"
                BtStatus.ON -> Icons.Rounded.Bluetooth to "Bluetooth on"
                BtStatus.OFF -> Icons.Rounded.BluetoothDisabled to "Bluetooth off"
            }
            Icon(btIcon, btDesc, tint = if (state.bluetooth == BtStatus.OFF) T.colors.secondary else T.colors.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(time, style = TType.title, color = T.colors.text)
            Spacer(Modifier.width(12.dp))
            val w = state.wifi
            val wifiIcon = when {
                !w.enabled -> Icons.Rounded.WifiOff
                !w.connected -> Icons.Rounded.SignalWifi0Bar
                w.level >= 4 -> Icons.Rounded.SignalWifi4Bar
                w.level == 3 -> Icons.Rounded.NetworkWifi3Bar
                w.level == 2 -> Icons.Rounded.NetworkWifi2Bar
                else -> Icons.Rounded.NetworkWifi1Bar
            }
            Icon(
                wifiIcon, if (w.connected) "Wi-Fi connected" else if (w.enabled) "Wi-Fi not connected" else "Wi-Fi off",
                tint = if (w.connected) T.colors.text else T.colors.secondary, modifier = Modifier.size(20.dp),
            )
        }
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

@Composable
fun is24h(): Boolean {
    val ctx = LocalContext.current
    return if (LocalInspectionMode.current) false else android.text.format.DateFormat.is24HourFormat(ctx)
}

