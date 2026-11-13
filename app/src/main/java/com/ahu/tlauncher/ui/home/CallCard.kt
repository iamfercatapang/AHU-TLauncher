package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.Palette
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlinx.coroutines.delay

private val CALL_GREEN = Color(0xFF25CB55)
private val CALL_RED = Color(0xFFE5484D)

/** Incoming / ongoing call card, always dark like the dock. */
@Composable
fun CallCard(call: CallUi, actions: HomeActions, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(min = 420.dp).clip(TShapes.surface).background(Palette.CarbonDark).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF3A3D44)), contentAlignment = Alignment.Center) {
            val initial = call.caller.firstOrNull { it.isLetter() }?.uppercaseChar()
            if (initial != null) Text(initial.toString(), style = TType.title, color = Color.White)
            else Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f, fill = false).widthIn(max = 260.dp)) {
            Text(call.caller, style = TType.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (call.ringing) "Incoming call" else callTimer(call.startedAtMs), style = TType.label, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (call.ringing) {
                RoundButton(Icons.Rounded.Close, "Decline", CALL_RED, onClick = actions::endCall)
                RoundButton(Icons.Rounded.Call, "Answer", CALL_GREEN, onClick = actions::answerCall)
            } else {
                RoundButton(if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (call.muted) "Unmute" else "Mute",
                    if (call.muted) Color.White else Color(0xFF3A3D44), iconTint = if (call.muted) Palette.CarbonDark else Color.White, size = 44.dp, onClick = actions::toggleCallMute)
                if (call.canSwitchAudio) RoundButton(Icons.Rounded.PhoneAndroid, "Use phone audio", Color(0xFF3A3D44), size = 44.dp, onClick = actions::switchCallAudio)
                RoundButton(Icons.Rounded.CallEnd, "Hang up", CALL_RED, onClick = actions::endCall)
            }
        }
    }
}

@Composable
private fun callTimer(startMs: Long?): String {
    if (startMs == null) return "On call"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val preview = LocalInspectionMode.current
    LaunchedEffect(startMs) { while (!preview) { now = System.currentTimeMillis(); delay(1000) } }
    val s = ((now - startMs) / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun RoundButton(icon: ImageVector, desc: String, bg: Color, iconTint: Color = Color.White, size: Dp = 52.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = iconTint, modifier = Modifier.size(size * 0.48f))
    }
}

/** "Android Auto ready" prompt, shown when the phone's head unit server is found. */
@Composable
fun AndroidAutoPrompt(receiver: String?, actions: HomeActions, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(TShapes.surface).background(Palette.CarbonDark).padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Android Auto is ready", style = TType.button, color = Color.White)
            Text(receiver?.let { "Opens in $it" } ?: "Phone's head unit server is on", style = TType.label, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.clip(TShapes.button).pressable(onClick = actions::dismissAndroidAuto).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Not now", style = TType.button, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(TShapes.button).background(Color(0xFF3E6AE1)).pressable(onClick = actions::openAndroidAuto).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("Open", style = TType.button, color = Color.White)
        }
    }
}
