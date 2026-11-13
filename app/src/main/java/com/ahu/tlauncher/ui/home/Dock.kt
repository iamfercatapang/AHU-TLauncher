package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** The dock is always dark, in both themes, like the in-car reference. */
private val DOCK_BG = Color(0xFF0C0D0E)
private val DOCK_FG = Color.White
private val DOCK_MUTED = Color.White.copy(alpha = 0.45f)
private val DOCK_ACTIVE = Color.White.copy(alpha = 0.12f)

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
) {
    val pinned = state.settings.pinnedApps.mapNotNull { key -> state.apps.firstOrNull { it.key == key } }.take(6)
    Box(modifier.background(DOCK_BG).padding(horizontal = 18.dp)) {
        DockIcon(Icons.Rounded.DirectionsCar, "Vehicle & settings", overlay == Overlay.SETTINGS, onOpenSettings, Modifier.align(Alignment.CenterStart))

        // centre: pinned apps in full colour, then music + launcher
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            pinned.forEach { app ->
                Box(Modifier.size(48.dp).pressable { onLaunch(app) }, contentAlignment = Alignment.Center) {
                    Image(app.icon, app.label, modifier = Modifier.size(38.dp).clip(TShapes.button))
                }
            }
            DockIcon(Icons.Rounded.MusicNote, "Music", overlay == Overlay.MUSIC, onToggleMusic)
            DockIcon(Icons.Rounded.Apps, "All apps", overlay == Overlay.APPS, onToggleApps)
        }

        // right: volume
        val v = state.volume
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButtonT(Icons.Rounded.KeyboardArrowLeft, "Volume down", size = 36.dp, iconSize = 22.dp, tint = DOCK_MUTED) { actions.volumeStep(false) }
            Row(Modifier.fillMaxHeight().pressable { actions.toggleMute() }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (v.muted || v.level == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Mute", tint = DOCK_FG, modifier = Modifier.size(28.dp))
                Text(if (v.muted) " 0" else " ${v.level}", style = TType.title, color = DOCK_FG, modifier = Modifier.width(30.dp))
            }
            IconButtonT(Icons.Rounded.KeyboardArrowRight, "Volume up", size = 36.dp, iconSize = 22.dp, tint = DOCK_MUTED) { actions.volumeStep(true) }
        }
    }
}

@Composable
private fun DockIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(TShapes.button).background(if (active) DOCK_ACTIVE else Color.Transparent).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = DOCK_FG, modifier = Modifier.size(30.dp)) }
}
