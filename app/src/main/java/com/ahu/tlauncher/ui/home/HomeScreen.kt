package com.ahu.tlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
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
) {
    var overlay by remember { mutableStateOf(initialOverlay) }
    var mapVisible by rememberSaveable { mutableStateOf(initialMapVisible) }
    var toast by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    // Bring the map back when Maps/Waze starts guiding.
    LaunchedEffect(state.nav != null) { if (state.nav != null) mapVisible = true }
    val toggle = { o: Overlay -> overlay = if (overlay == o) Overlay.NONE else o }
    val stageFraction by animateFloatAsState(if (mapVisible) SPLIT_FRACTION else 1f, TMotion.std(), label = "split")

    Column(Modifier.fillMaxSize().background(T.colors.appBg)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val stageW = maxWidth * stageFraction
            Row(Modifier.fillMaxSize()) {
                CarStage(
                    state, actions, expanded = !mapVisible, onToggleMap = { mapVisible = !mapVisible },
                    modifier = Modifier.width(stageW).fillMaxHeight(),
                )
                if (stageFraction < 0.999f) MapPane(state, actions, Modifier.weight(1f).fillMaxHeight())
            }
            StatusBar(state, Modifier.fillMaxWidth().height(STATUS_H))

            // overlays inside the body area
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.SETTINGS,
                enter = slideInVertically(TMotion.std()) { it / 8 } + fadeIn(TMotion.std()),
                exit = slideOutVertically(TMotion.std()) { it / 8 } + fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                com.ahu.tlauncher.ui.settings.SettingsScreen(
                    state, actions, onClose = { overlay = Overlay.NONE },
                    modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).height(maxHeight - STATUS_H).padding(8.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.MUSIC,
                enter = slideInVertically(TMotion.std()) { it } + fadeIn(TMotion.std()),
                exit = slideOutVertically(TMotion.std()) { it } + fadeOut(TMotion.std()),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                MusicPanel(state, actions, onClose = { overlay = Overlay.NONE }, modifier = Modifier.width(maxWidth * (1 - SPLIT_FRACTION)).padding(12.dp))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = overlay == Overlay.APPS,
                enter = slideInVertically(TMotion.std()) { it / 6 } + fadeIn(TMotion.std()),
                exit = slideOutVertically(TMotion.std()) { it / 6 } + fadeOut(TMotion.std()),
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
            onOpenSettings = { toggle(Overlay.SETTINGS) },
            onToggleApps = { toggle(Overlay.APPS) },
            onToggleMusic = { toggle(Overlay.MUSIC) },
            onLaunch = { app: AppEntry -> overlay = Overlay.NONE; actions.launch(app) },
            modifier = Modifier.fillMaxWidth().height(DOCK_H),
        )
    }
}

/** Full-width status row floating over the stage and map: link status · date · time. */
@Composable
private fun StatusBar(state: HomeUiState, modifier: Modifier) {
    val time = state.now.format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mma", Locale.US)).lowercase()
    Box(modifier.padding(horizontal = 20.dp)) {
        ObdBadge(state.obd.status, Modifier.align(Alignment.CenterStart))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.bluetoothOn) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, "Bluetooth",
                tint = T.colors.text, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(state.now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())), style = TType.button, color = T.colors.text)
        }
        Text(time, style = TType.title, color = T.colors.text, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
fun is24h(): Boolean {
    val ctx = LocalContext.current
    return if (LocalInspectionMode.current) false else android.text.format.DateFormat.is24HourFormat(ctx)
}

