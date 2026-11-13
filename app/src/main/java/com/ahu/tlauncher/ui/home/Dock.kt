package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dock glyphs are monochrome: the palette allows no colour besides the blue CTA. */
private val monochrome = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })

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
    Box(modifier.background(T.colors.dock).padding(horizontal = 14.dp)) {
        // left: vehicle/settings + pinned apps
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButtonT(Icons.Rounded.DirectionsCar, "Vehicle & settings", size = 48.dp, iconSize = 28.dp, active = overlay == Overlay.SETTINGS, onClick = onOpenSettings)
            Spacer(Modifier.width(6.dp))
            pinned.forEach { app ->
                Box(Modifier.size(48.dp).pressable { onLaunch(app) }, contentAlignment = Alignment.Center) {
                    Image(app.icon, app.label, modifier = Modifier.size(32.dp).clip(TShapes.button), colorFilter = monochrome)
                }
            }
        }
        // centre: clock (takes the slot the reference used for climate)
        val time = state.now.format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mm", Locale.getDefault()))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
            Text(time, style = TType.promo, color = T.colors.text)
            if (!is24h()) {
                Text(
                    state.now.format(DateTimeFormatter.ofPattern(" a", Locale.US)),
                    style = TType.label, color = T.colors.muted, modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        // right: music, app drawer, volume
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButtonT(Icons.Rounded.MusicNote, "Music", size = 48.dp, iconSize = 28.dp, active = overlay == Overlay.MUSIC, onClick = onToggleMusic)
            IconButtonT(Icons.Rounded.Apps, "All apps", size = 48.dp, iconSize = 28.dp, active = overlay == Overlay.APPS, onClick = onToggleApps)
            Spacer(Modifier.width(4.dp))
            VolumeControl(state, actions)
        }
    }
}

@Composable
private fun VolumeControl(state: HomeUiState, actions: HomeActions) {
    val v = state.volume
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButtonT(Icons.Rounded.KeyboardArrowLeft, "Volume down", size = 36.dp, iconSize = 24.dp, tint = T.colors.muted) { actions.volumeStep(false) }
        Row(
            Modifier.fillMaxHeight().pressable { actions.toggleMute() }.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (v.muted || v.level == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                "Mute", tint = T.colors.text, modifier = Modifier.size(26.dp),
            )
            Text(if (v.muted) "  0" else " %2d".format(v.level), style = TType.body, color = T.colors.text)
        }
        IconButtonT(Icons.Rounded.KeyboardArrowRight, "Volume up", size = 36.dp, iconSize = 24.dp, tint = T.colors.muted) { actions.volumeStep(true) }
    }
}

@Composable
private fun is24h(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return if (androidx.compose.ui.platform.LocalInspectionMode.current) true else android.text.format.DateFormat.is24HourFormat(ctx)
}
