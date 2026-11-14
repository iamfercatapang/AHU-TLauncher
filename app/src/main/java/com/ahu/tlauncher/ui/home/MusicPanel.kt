package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.notify.QueueEntry
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** What "Up next" shows: the playing song (if it's in the queue) followed by the rest. Pure, unit tested. */
object UpNext {
    fun rows(queue: List<QueueEntry>, activeId: Long): List<QueueEntry> {
        val i = queue.indexOfFirst { it.id == activeId }
        return if (i < 0) queue else queue.drop(i)
    }

    /** Songs still to come after the current one. */
    fun remaining(queue: List<QueueEntry>, activeId: Long): Int {
        val i = queue.indexOfFirst { it.id == activeId }
        return if (i < 0) queue.size else queue.size - i - 1
    }
}

/**
 * Music panel: big artwork, title, seek bar and controls on the left; the app's queue ("Up next")
 * on the right. Apps that don't share a queue (e.g. Bluetooth audio) get the player alone, centred.
 */
@Composable
fun MusicPanel(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val live = state.media != null
    val m = state.media ?: state.lastMedia
    val rows = state.media?.let { UpNext.rows(it.queue, it.activeQueueId) }.orEmpty()
    Column(modifier.fillMaxWidth().clip(TShapes.surface).background(T.colors.panel).padding(start = 22.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Music", style = TType.title, color = T.colors.text)
            Spacer(Modifier.width(10.dp))
            Text(m?.let { mi -> state.apps.firstOrNull { it.packageName == mi.packageName }?.label } ?: "", style = TType.label, color = T.colors.muted)
            Spacer(Modifier.weight(1f))
            if (m != null) IconButtonT(Icons.AutoMirrored.Rounded.OpenInNew, "Open player app", size = 36.dp, iconSize = 20.dp, onClick = actions::openMediaApp)
            IconButtonT(Icons.Rounded.Close, "Close", size = 36.dp, iconSize = 22.dp, onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            val player = Modifier.fillMaxHeight().width(if (rows.isEmpty()) 340.dp else 300.dp)
            if (rows.isEmpty()) Spacer(Modifier.weight(1f))
            Player(m, actions, player, live, state.musicSubtitle(m?.let { mi -> state.apps.firstOrNull { it.packageName == mi.packageName }?.label }), state.resumingMedia)
            if (rows.isEmpty()) Spacer(Modifier.weight(1f))
            else {
                Spacer(Modifier.width(28.dp))
                QueueList(m!!, rows, actions, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Player(m: MediaInfo?, actions: HomeActions, modifier: Modifier, live: Boolean = true, subtitle: String? = m?.artist, resuming: Boolean = false) {
    BoxWithConstraints(modifier) {
        // Artwork takes what's left after the text, seek bar and controls (about 190 dp).
        val art = min(maxWidth, maxHeight - 190.dp).coerceAtLeast(96.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumArt(m, Modifier.size(art).clip(TShapes.surface))
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(m?.title?.ifBlank { null } ?: "Not playing", style = TType.title, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle?.ifBlank { null } ?: "Start music in any app", style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pos = if (LocalInspectionMode.current) m?.positionMs ?: 0 else livePosition(m)
                var dragging by remember { mutableStateOf<Float?>(null) }
                val dur = (m?.durationMs ?: 0).coerceAtLeast(1)
                Slider(
                    value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { actions.seekTo((it * dur).toLong()) }; dragging = null },
                    enabled = live && m != null && m.durationMs > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = T.colors.text, activeTrackColor = T.colors.text, inactiveTrackColor = T.colors.divider,
                        disabledThumbColor = T.colors.muted, disabledInactiveTrackColor = T.colors.divider,
                    ),
                    modifier = Modifier.height(26.dp),
                )
                Row {
                    Text(fmt(dragging?.let { (it * dur).toLong() } ?: pos), style = TType.caption, color = T.colors.muted)
                    Spacer(Modifier.weight(1f))
                    Text(if (m != null && m.durationMs > 0) fmt(m.durationMs) else "", style = TType.caption, color = T.colors.muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                m?.shuffleOn?.let { on ->
                    IconButtonT(Icons.Rounded.Shuffle, if (on) "Shuffle on" else "Shuffle off", size = 44.dp, iconSize = 22.dp,
                        tint = if (on) T.colors.accent else T.colors.muted, onClick = actions::toggleShuffle)
                }
                IconButtonT(Icons.Rounded.SkipPrevious, "Previous", size = 48.dp, iconSize = 30.dp, enabled = live) { actions.previous() }
                Box(Modifier.size(56.dp).clip(CircleShape).background(T.colors.card), contentAlignment = Alignment.Center) {
                    IconButtonT(if (m?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", size = 56.dp, iconSize = 32.dp, enabled = m != null && !resuming) { actions.playPause() }
                }
                IconButtonT(Icons.Rounded.SkipNext, "Next", size = 48.dp, iconSize = 30.dp, enabled = live) { actions.next() }
                m?.repeatMode?.let { mode ->
                    IconButtonT(if (mode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (mode) { 1 -> "Repeat one"; 2 -> "Repeat all"; else -> "Repeat off" }, size = 44.dp, iconSize = 22.dp,
                        tint = if (mode != 0) T.colors.accent else T.colors.muted, onClick = actions::cycleRepeat)
                }
            }
        }
    }
}

@Composable
private fun QueueList(m: MediaInfo, rows: List<QueueEntry>, actions: HomeActions, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = TType.button, color = T.colors.text)
            Spacer(Modifier.weight(1f))
            val n = UpNext.remaining(m.queue, m.activeQueueId)
            Text(if (n == 1) "1 song" else "$n songs", style = TType.caption, color = T.colors.muted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.id }) { q ->
                val current = q.id == m.activeQueueId
                Row(
                    Modifier.fillMaxWidth().clip(TShapes.card)
                        .background(if (current) T.colors.card else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (current) Modifier else Modifier.pressable { actions.playQueueItem(q.id) })
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QueueArt(q, Modifier.size(44.dp).clip(TShapes.inner))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.title, style = TType.body, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (q.subtitle.isNotBlank()) Text(q.subtitle, style = TType.caption, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    when {
                        current -> Icon(Icons.Rounded.MusicNote, "Playing", tint = T.colors.text, modifier = Modifier.size(18.dp))
                        q.durationMs != null -> Text(fmt(q.durationMs), style = TType.caption, color = T.colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueArt(q: QueueEntry, modifier: Modifier) {
    Box(modifier.background(T.colors.card), contentAlignment = Alignment.Center) {
        when {
            q.art != null -> Image(q.art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            q.artUri != null && !LocalInspectionMode.current -> AsyncImage(q.artUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(Icons.Rounded.MusicNote, null, tint = T.colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
