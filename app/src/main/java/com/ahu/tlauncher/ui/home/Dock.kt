package com.ahu.tlauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.components.TIcons
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** The dock is always black, in both themes, like the in-car reference. */
private val DOCK_BG = Color.Black
private val DOCK_FG = Color.White
private val DOCK_MUTED = Color.White.copy(alpha = 0.6f)
/** Prototype's active tint (#c5d8ee12) and underline (#b9cbdd). */
private val ACTIVE_BG = Color(0x12C5D8EE)
private val ACTIVE_LINE = Color(0xFFB9CBDD)

/**
 * Laid out like the prototype's control bar: car on the left, then (where climate sits there) your
 * pinned apps, music, "…" for all apps, brightness, and a vertical volume control on the right.
 */
@Composable
fun Dock(
    state: HomeUiState,
    actions: HomeActions,
    overlay: Overlay,
    onOpenSettings: () -> Unit,
    onToggleApps: () -> Unit,
    onToggleMusic: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    onToggleBrightness: () -> Unit = {},
    onToggleVolume: () -> Unit = {},
) {
    val pinned = state.settings.pinnedApps.mapNotNull { key -> state.apps.firstOrNull { it.key == key } }.take(6)
    Row(
        modifier.background(DOCK_BG).padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(com.ahu.tlauncher.ui.components.MpvFrontIcon, "Vehicle & settings", overlay == Overlay.SETTINGS, onOpenSettings)
        pinned.forEach { app ->
            Box(Modifier.size(46.dp).pressable { onLaunch(app) }, contentAlignment = Alignment.Center) {
                AppIcon(app, state.settings.minimalIcons, DOCK_FG, 34.dp, TShapes.button, app.label)
            }
        }
        DockButton(TIcons.Music, "Music", overlay == Overlay.MUSIC, onToggleMusic)
        DockButton(TIcons.More, "All apps", overlay == Overlay.APPS, onToggleApps)
        DockButton(TIcons.Sun, "Brightness", overlay == Overlay.BRIGHTNESS, onToggleBrightness)
        VolumeControl(state, actions, overlay == Overlay.VOLUME, onToggleVolume)
    }
}

/** Icon button with the prototype's active state: soft tinted square plus a short underline. */
@Composable
private fun DockButton(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (active) 1f else 0f, TMotion.panel(), label = "dockActive")
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(46.dp).graphicsLayer { alpha = a }.clip(TShapes.surface).background(ACTIVE_BG))
        Box(Modifier.size(46.dp).clip(TShapes.surface).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = DOCK_FG, modifier = Modifier.size(28.dp))
        }
        // underline grows in from the centre
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).width(14.dp).height(2.dp)
                .graphicsLayer { alpha = a; scaleX = a }.clip(TShapes.button).background(ACTIVE_LINE),
        )
    }
}

/** Vertical volume like the prototype: up, speaker, down. Tap the speaker for the volume slider. */
@Composable
private fun VolumeControl(state: HomeUiState, actions: HomeActions, active: Boolean, onToggle: () -> Unit) {
    val v = state.volume
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 44.dp, height = 16.dp).pressable { actions.volumeStep(true) }, contentAlignment = Alignment.Center) {
            Icon(TIcons.ChevronUp, "Volume up", tint = DOCK_MUTED, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.size(width = 44.dp, height = 28.dp).clip(TShapes.surface).background(if (active) ACTIVE_BG else Color.Transparent).pressable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Icon(if (v.muted || v.level == 0) TIcons.SpeakerOff else TIcons.SpeakerWaves, "Volume", tint = DOCK_FG, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.size(width = 44.dp, height = 16.dp).pressable { actions.volumeStep(false) }, contentAlignment = Alignment.Center) {
            Icon(TIcons.ChevronDown, "Volume down", tint = DOCK_MUTED, modifier = Modifier.size(16.dp))
        }
    }
}
