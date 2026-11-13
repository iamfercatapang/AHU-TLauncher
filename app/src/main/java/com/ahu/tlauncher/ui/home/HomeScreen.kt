package com.ahu.tlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlinx.coroutines.delay

/** Design canvas: everything is laid out for 1024×600 dp and scaled to the real screen. */
const val CANVAS_W = 1024f
const val CANVAS_H = 600f
val LEFT_PANEL_W = 380.dp
val DOCK_H = 64.dp

enum class Overlay { NONE, APPS, SETTINGS, MUSIC }

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, initialOverlay: Overlay = Overlay.NONE) {
    var overlay by remember { mutableStateOf(initialOverlay) }
    var toast by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    val toggle = { o: Overlay -> overlay = if (overlay == o) Overlay.NONE else o }

    Box(Modifier.fillMaxSize().background(T.colors.appBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LeftPanel(
                    state, actions,
                    onOpenSettings = { toggle(Overlay.SETTINGS) },
                    modifier = Modifier.width(LEFT_PANEL_W).fillMaxHeight(),
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    MapPane(state, actions, Modifier.fillMaxSize())
                    androidx.compose.animation.AnimatedVisibility(
                        visible = overlay == Overlay.MUSIC,
                        enter = slideInVertically(TMotion.medium()) { it } + fadeIn(TMotion.medium()),
                        exit = slideOutVertically(TMotion.normal()) { it } + fadeOut(TMotion.normal()),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        MusicPanel(state, actions, onClose = { overlay = Overlay.NONE }, modifier = Modifier.padding(12.dp))
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = overlay == Overlay.SETTINGS,
                        enter = slideInVertically(TMotion.medium()) { it / 6 } + fadeIn(TMotion.medium()),
                        exit = slideOutVertically(TMotion.normal()) { it / 6 } + fadeOut(TMotion.normal()),
                    ) {
                        com.ahu.tlauncher.ui.settings.SettingsScreen(
                            state, actions, onClose = { overlay = Overlay.NONE },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
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

        AnimatedVisibility(
            visible = overlay == Overlay.APPS,
            enter = slideInVertically(TMotion.medium()) { it / 4 } + fadeIn(TMotion.medium()),
            exit = slideOutVertically(TMotion.normal()) { it / 4 } + fadeOut(TMotion.normal()),
            modifier = Modifier.fillMaxSize().padding(bottom = DOCK_H),
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
            )
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(TMotion.normal()), exit = fadeOut(TMotion.medium()),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = DOCK_H + 16.dp),
        ) {
            Box(Modifier.background(com.ahu.tlauncher.ui.theme.Palette.CarbonDark, TShapes.button).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(toast ?: "", style = TType.label, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
