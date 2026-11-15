package com.ahu.tlauncher.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.ui.components.TIcons
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/**
 * "Now playing" over the car panel (prototype screenshot): art with a level meter, title, seek bar,
 * controls with a collapse button, and Up next. Separate from the dock's Music panel; only one is open.
 */
@Composable
fun CarMusicPlayer(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val live = state.media != null
    val m = state.media ?: state.lastMedia
    val appLabel = m?.let { mi -> state.apps.firstOrNull { it.packageName == mi.packageName }?.label }
    val dark = T.colors.isDark
    val surface = if (dark) Color(0xFF22252B) else T.colors.panel
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Now playing", style = TType.title, color = T.colors.text)
            Spacer(Modifier.width(8.dp))
            Text(state.musicSubtitle(appLabel)?.takeIf { !live } ?: appLabel.orEmpty(), style = TType.caption, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(TIcons.Close, "Close", tint = T.colors.text, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        // feature: art, level meter, title (soft gradient behind, like the prototype)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf((if (dark) Color.White else Color.Black).copy(alpha = 0.06f), Color.Transparent)))
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumArt(m, Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(4.dp))
            LevelMeter(m?.isPlaying == true)
            Text(m?.title?.ifBlank { null } ?: "Not playing", style = TType.button, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            Text(m?.artist.orEmpty(), style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // seek bar + times
        val pos = if (LocalInspectionMode.current) m?.positionMs ?: 0 else livePosition(state.media)
        val dur = m?.durationMs ?: 0
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(T.colors.divider)) {
            val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.width(maxWidth * frac).fillMaxHeight().background(T.colors.text))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(fmt(pos), style = TType.caption, color = T.colors.muted)
            Spacer(Modifier.weight(1f))
            Text(if (dur > 0) fmt(dur) else "", style = TType.caption, color = T.colors.muted)
        }
        // controls: previous · play · next, and collapse on the right
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Ctrl(TIcons.Previous, "Previous", enabled = live) { actions.previous() }
            Ctrl(if (m?.isPlaying == true) TIcons.Pause else TIcons.Play, "Play/Pause", enabled = m != null && !state.resumingMedia) { actions.playPause() }
            Ctrl(TIcons.Next, "Next", enabled = live) { actions.next() }
            Ctrl(TIcons.ChevronDown, "Collapse", onClick = onClose)
        }
        // Up next
        val media = state.media
        val rows = media?.let { UpNext.rows(it.queue, it.activeQueueId) }.orEmpty()
        if (media != null && rows.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            QueueList(media, rows, actions, Modifier.weight(1f).fillMaxWidth(), compact = true)
        }
    }
}

@Composable
private fun Ctrl(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(TShapes.surface).pressable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = T.colors.text, modifier = Modifier.size(19.dp))
    }
}

/** Four small bars that bounce while music plays (prototype's playing meter). */
@Composable
private fun LevelMeter(playing: Boolean) {
    // The bouncing animation only exists while music plays (and not in low-performance mode),
    // so a paused player draws nothing frame after frame.
    val preview = LocalInspectionMode.current
    val animate = playing && !preview && !com.ahu.tlauncher.ui.theme.LocalLowPerf.current
    Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        if (animate) {
            val t = rememberInfiniteTransition(label = "meter")
            listOf(0, 400, 200, 600).forEach { delay ->
                val h by t.animateFloat(4f, 14f, infiniteRepeatable(tween(750, delayMillis = delay), RepeatMode.Reverse), label = "bar")
                MeterBar(Modifier.height(h.dp))
            }
        } else {
            // Still bars: uneven while playing (low-performance mode) so it still reads as "playing".
            listOf(9, 13, 7, 11).forEach { h -> MeterBar(Modifier.height(if (playing && !preview) h.dp else 5.dp)) }
        }
    }
}

@Composable
private fun MeterBar(modifier: Modifier) {
    Box(modifier.width(3.dp).clip(RoundedCornerShape(2.dp)).background(T.colors.text.copy(alpha = 0.8f)))
}
