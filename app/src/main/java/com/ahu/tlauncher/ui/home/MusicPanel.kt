package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

@Composable
fun MusicPanel(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val m = state.media
    Column(
        modifier.fillMaxWidth().clip(TShapes.surface).background(T.colors.panel).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Music", style = TType.title, color = T.colors.text)
            Spacer(Modifier.width(10.dp))
            Text(m?.let { mi -> state.apps.firstOrNull { it.packageName == mi.packageName }?.label } ?: "", style = TType.label, color = T.colors.muted)
            Spacer(Modifier.weight(1f))
            IconButtonT(Icons.Rounded.Close, "Close", size = 36.dp, iconSize = 22.dp, onClick = onClose)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(m, Modifier.size(132.dp).clip(TShapes.surface))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(m?.title?.ifBlank { null } ?: "Not playing", style = TType.promo, color = T.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(m?.artist ?: "", style = TType.body, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val pos = if (LocalInspectionMode.current) m?.positionMs ?: 0 else livePosition(m)
                var dragging by remember { mutableStateOf<Float?>(null) }
                val dur = (m?.durationMs ?: 0).coerceAtLeast(1)
                Slider(
                    value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { actions.seekTo((it * dur).toLong()) }; dragging = null },
                    enabled = m != null && m.durationMs > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = T.colors.text, activeTrackColor = T.colors.text, inactiveTrackColor = T.colors.divider,
                        disabledThumbColor = T.colors.muted, disabledInactiveTrackColor = T.colors.divider,
                    ),
                    modifier = Modifier.height(28.dp),
                )
                Row {
                    Text(fmt(pos), style = TType.caption, color = T.colors.muted)
                    Spacer(Modifier.weight(1f))
                    Text(if (m != null && m.durationMs > 0) fmt(m.durationMs) else "", style = TType.caption, color = T.colors.muted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    IconButtonT(Icons.Rounded.SkipPrevious, "Previous", size = 48.dp, iconSize = 30.dp, enabled = m != null) { actions.previous() }
                    Box(Modifier.size(56.dp).clip(TShapes.button).background(T.colors.card), contentAlignment = Alignment.Center) {
                        IconButtonT(if (m?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", size = 56.dp, iconSize = 34.dp, enabled = m != null) { actions.playPause() }
                    }
                    IconButtonT(Icons.Rounded.SkipNext, "Next", size = 48.dp, iconSize = 30.dp, enabled = m != null) { actions.next() }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PillButton("Open player app", icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = actions::openMediaApp)
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
