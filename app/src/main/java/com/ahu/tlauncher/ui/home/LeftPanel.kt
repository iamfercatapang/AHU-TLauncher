package com.ahu.tlauncher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.data.SpeedUnit
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.notify.MediaInfo
import com.ahu.tlauncher.obd.ObdStatus
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.Stat
import com.ahu.tlauncher.ui.components.TCard
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt


@Composable
fun LeftPanel(state: HomeUiState, actions: HomeActions, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.background(T.colors.panel).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TopInfo(state, onOpenSettings)
        SpeedBlock(state)
        // The car is the hero: it takes all remaining space.
        CarImage(state.settings.carImages, Modifier.weight(1f).fillMaxWidth())
        EngineStats(state)
        Spacer(Modifier.height(2.dp))
        MusicCard(state, actions)
        NavShortcuts(state, actions)
    }
}

@Composable
private fun TopInfo(state: HomeUiState, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())),
            style = TType.label, color = T.colors.muted,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            if (state.bluetoothOn) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, "Bluetooth",
            tint = if (state.bluetoothOn) T.colors.text else T.colors.muted, modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        ObdBadge(state.obd.status, Modifier.pressable(onClick = onOpenSettings))
    }
}

@Composable
fun ObdBadge(status: ObdStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        ObdStatus.DEMO -> "OBD demo"
        ObdStatus.CONNECTED -> "OBD connected"
        ObdStatus.CONNECTING -> "OBD connecting"
        ObdStatus.ERROR -> "OBD not responding"
        ObdStatus.DISCONNECTED -> "OBD off"
    }
    val live = status == ObdStatus.CONNECTED
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (live) T.colors.text else T.colors.secondary))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TType.label, color = T.colors.muted)
    }
}

@Composable
private fun SpeedBlock(state: HomeUiState) {
    val d = state.obd.data
    val mph = state.settings.speedUnit == SpeedUnit.MPH
    val speed = d.speedKmh?.let { if (mph) (it * 0.621371).roundToInt() else it }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(speed?.toString() ?: "--", style = TType.speed, color = T.colors.text, textAlign = TextAlign.Center)
        Text(if (mph) "mph" else "km/h", style = TType.label, color = T.colors.muted, modifier = Modifier.offset(y = (-4).dp))
    }
}

@Composable
private fun EngineStats(state: HomeUiState) {
    val d = state.obd.data
    val f = state.settings.tempUnit == TempUnit.F
    val coolant = d.coolantC?.let { if (f) (it * 9 / 5.0 + 32).roundToInt() else it }
    // Only critical conditions get colour (in-car safety exception to the monochrome palette).
    val coolantColor = if ((d.coolantC ?: 0) >= 105) T.colors.danger else T.colors.text
    val v = d.batteryV
    val vColor = if (v != null && (v < 11.8 || v > 15.2)) T.colors.danger else T.colors.text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Stat("Engine", d.rpm?.let { "%,d".format(it) } ?: "--", "rpm")
        Stat("Coolant", coolant?.toString() ?: "--", if (f) "°F" else "°C", valueColor = coolantColor)
        Stat("Battery", v?.let { "%.1f".format(it) } ?: "--", "V", valueColor = vColor)
    }
}

@Composable
fun MusicCard(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    TCard(modifier.fillMaxWidth().height(88.dp)) {
        if (!state.notificationAccess) {
            Row(
                Modifier.fillMaxSize().pressable(pressedScale = 0.97f) { actions.requestNotificationAccess() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = T.colors.text, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Allow notification access", style = TType.button, color = T.colors.text)
                    Text("Shows music and Maps/Waze directions here", style = TType.label, color = T.colors.muted, maxLines = 2)
                }
            }
            return@TCard
        }
        val m = state.media
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).pressable { actions.openMediaApp() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(m, Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(m?.title?.ifBlank { null } ?: "Not playing", style = TType.button, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m?.artist?.ifBlank { null } ?: "Start music in any app", style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TrackProgress(m, Modifier.fillMaxWidth().height(2.dp))
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButtonT(Icons.Rounded.SkipPrevious, "Previous", size = 36.dp, iconSize = 24.dp, enabled = m != null) { actions.previous() }
                IconButtonT(if (m?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", size = 36.dp, iconSize = 26.dp, enabled = m != null) { actions.playPause() }
                IconButtonT(Icons.Rounded.SkipNext, "Next", size = 36.dp, iconSize = 24.dp, enabled = m != null) { actions.next() }
            }
        }
    }
}

@Composable
fun AlbumArt(m: MediaInfo?, modifier: Modifier) {
    Box(modifier.background(T.colors.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        val art = m?.art
        if (art != null) {
            Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = T.colors.muted, modifier = Modifier.size(24.dp))
        }
    }
}

/** Live position = last reported position + elapsed time × speed (while playing). */
@Composable
fun livePosition(m: MediaInfo?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(m?.isPlaying, m?.updateTimeMs) {
        while (m?.isPlaying == true) { now = SystemClock.elapsedRealtime(); delay(500) }
    }
    if (m == null) return 0
    if (!m.isPlaying || m.updateTimeMs <= 0) return m.positionMs
    val p = m.positionMs + ((now - m.updateTimeMs) * m.speed).toLong()
    return if (m.durationMs > 0) p.coerceIn(0, m.durationMs) else p
}

@Composable
private fun TrackProgress(m: MediaInfo?, modifier: Modifier) {
    val pos = if (LocalInspectionMode.current) m?.positionMs ?: 0 else livePosition(m)
    val frac = if (m == null || m.durationMs <= 0) 0f else (pos.toFloat() / m.durationMs).coerceIn(0f, 1f)
    BoxWithConstraints(modifier.background(T.colors.divider)) {
        Box(Modifier.width(maxWidth * frac).fillMaxHeight().background(T.colors.text))
    }
}

@Composable
private fun NavShortcuts(state: HomeUiState, actions: HomeActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Navigate", icon = Icons.Rounded.Search, kind = ButtonKind.PRIMARY, modifier = Modifier.weight(1.3f), onClick = actions::openNavApp)
        PillButton("Home", icon = Icons.Rounded.Home, modifier = Modifier.weight(1f), onClick = actions::navigateHome)
        PillButton("Work", icon = Icons.Rounded.Work, modifier = Modifier.weight(1f), onClick = actions::navigateWork)
    }
}

