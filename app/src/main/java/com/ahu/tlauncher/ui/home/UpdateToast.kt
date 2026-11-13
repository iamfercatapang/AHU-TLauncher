package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.Palette
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import com.ahu.tlauncher.update.UpdateState

/** Whether the update toast should be on screen for this state. */
fun showUpdateToast(s: UpdateState, dismissedCode: Int): Boolean = when (s) {
    is UpdateState.Available -> s.version.versionCode > dismissedCode
    is UpdateState.Downloading, is UpdateState.Installing -> true
    is UpdateState.Failed -> s.version != null
    else -> false
}

/** Dark toast above the dock: "Update available · Later / Update", then download progress. */
@Composable
fun UpdateToast(state: UpdateState, actions: HomeActions, modifier: Modifier = Modifier) {
    val white = Color.White
    val muted = Color.White.copy(alpha = 0.6f)
    Column(modifier.widthIn(min = 380.dp, max = 520.dp).clip(TShapes.surface).background(Palette.CarbonDark.copy(alpha = 0.97f))) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = white, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val (title, sub) = when (state) {
                    is UpdateState.Available -> "Update available" to "TLauncher ${state.version.versionName}"
                    is UpdateState.Downloading -> "Downloading update…" to "${(state.progress * 100).toInt()}%"
                    is UpdateState.Installing -> "Installing update…" to "TLauncher restarts when it's done"
                    is UpdateState.Failed -> "Update not installed" to state.message
                    else -> "" to ""
                }
                Text(title, style = TType.button, color = white, maxLines = 1)
                Text(sub, style = TType.label, color = muted, maxLines = 2)
            }
            when (state) {
                is UpdateState.Available, is UpdateState.Failed -> {
                    Spacer(Modifier.width(8.dp))
                    Text("Later", style = TType.button, color = muted, modifier = Modifier.clip(TShapes.button).pressable(onClick = actions::dismissUpdate).padding(horizontal = 12.dp, vertical = 10.dp))
                    PillButton(if (state is UpdateState.Failed) "Retry" else "Update", kind = ButtonKind.PRIMARY, onClick = actions::startUpdate)
                }
                else -> Unit
            }
        }
        if (state is UpdateState.Downloading) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.width(maxWidth * state.progress).fillMaxHeight().background(Palette.ElectricBlue))
            }
        }
    }
}
