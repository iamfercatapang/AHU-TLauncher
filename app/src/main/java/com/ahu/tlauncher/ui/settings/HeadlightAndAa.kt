package com.ahu.tlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.system.AaState
import com.ahu.tlauncher.system.LightsSource
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.TSwitch
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** Lights-on tell-tale: learns which signal follows the head unit's ILL wire. */
@Composable
internal fun HeadlightSetup(state: HomeUiState, a: HomeActions) {
    val src = LightsSource.decode(state.settings.lightsSource)
    var step by remember { mutableIntStateOf(0) } // 0 idle, 1 lights off, 2 lights on
    var result by remember { mutableStateOf<String?>(null) }
    SmallTitle("Headlight indicator")
    SettingRow(
        "Lights-on symbol",
        if (src == LightsSource.Off) "Not set up. Uses the head unit's ILL wire." else "${src.label()} · lights ${if (state.lights.on) "on" else "off"}",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (src != LightsSource.Off && step == 0) PillButton("Turn off", onClick = { a.lightsDisable(); result = null })
            if (step == 0) PillButton("Set up", kind = ButtonKind.PRIMARY, onClick = { step = 1; result = null })
        }
    }
    if (step > 0) {
        Column(Modifier.fillMaxWidth().clip(TShapes.card).background(T.colors.card).padding(14.dp)) {
            Text("Step $step of 2", style = TType.caption, color = T.colors.muted)
            Text(
                if (step == 1) "Switch the headlights OFF, then tap Next." else "Now switch the headlights ON, wait 2 seconds, then tap Next.",
                style = TType.button, color = T.colors.text, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Cancel", onClick = { step = 0 })
                PillButton("Next", kind = ButtonKind.PRIMARY, onClick = {
                    if (step == 1) { a.lightsLearnOff(); step = 2 } else { result = a.lightsLearnOn(); step = 0 }
                })
            }
        }
    }
    result?.let { Text(it, style = TType.label, color = T.colors.text, modifier = Modifier.padding(vertical = 6.dp)) }
    // Live readout, to see what the head unit changes when the lights switch.
    val snap = state.lights.snapshot
    Text(
        "Head unit reports: night mode ${if (snap.night) "on" else "off"} · brightness ${snap.brightness ?: "?"}" +
            (if (state.lights.recentChanges.isNotEmpty()) "\nRecent setting changes: " + state.lights.recentChanges.joinToString(", ") else ""),
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

/** Wireless Android Auto through Headunit Reloaded. */
@Composable
internal fun AndroidAutoSection(state: HomeUiState, a: HomeActions) {
    SettingRow(
        "Receiver app",
        state.aaReceiver?.let { "$it is installed" } ?: "Headunit Reloaded is not installed. It shows Android Auto on the head unit.",
    ) {
        if (state.aaReceiver == null) PillButton("Get it", kind = ButtonKind.PRIMARY, onClick = a::getHeadunitReloaded)
        else PillButton("Open now", onClick = a::openAndroidAuto)
    }
    Divider()
    SettingRow("Offer Android Auto", "Show a prompt when the phone's head unit server is found on Wi-Fi") {
        TSwitch(state.settings.aaDetect, a::setAaDetect)
    }
    SettingRow(
        "Status",
        when (val s = state.aa) {
            AaState.Idle -> if (!state.settings.aaDetect) "Off" else if (state.aaReceiver == null) "Waiting for the receiver app" else "Starting…"
            AaState.NoWifi -> "Wi-Fi not connected. Connect the head unit to your phone's hotspot."
            is AaState.Searching -> "Looking for the head unit server on ${s.gateway}…"
            is AaState.Ready -> "Head unit server found on ${s.gateway}"
        },
    ) {}
    Divider()
    SmallTitle("How to start it")
    listOf(
        "1. On the phone, turn on the Wi-Fi hotspot and connect the head unit to it.",
        "2. In the phone's Android Auto settings, tap Version 10 times to unlock developer settings.",
        "3. From the ⋮ menu, choose Start head unit server.",
        "4. The dashboard shows \"Android Auto is ready\". Tap Open. In Headunit Reloaded, choose Wi-Fi (or turn on its Wi-Fi autostart).",
    ).forEach { Text(it, style = TType.label, color = T.colors.muted, modifier = Modifier.padding(vertical = 2.dp)) }
}
