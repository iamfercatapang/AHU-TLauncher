package com.ahu.tlauncher.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.obd.Dtc
import com.ahu.tlauncher.obd.ObdData
import com.ahu.tlauncher.obd.ObdDisplay
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.home.ObdBadge
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlin.math.roundToInt

private val AMBER = Color(0xFFF5A623)
private val RED = Color(0xFFE5484D)
private val GREEN = Color(0xFF25CB55)

/** Trouble codes (read / clear, stored + pending) and extra live engine data. */
@Composable
internal fun DiagnosticsSection(state: HomeUiState, a: HomeActions) {
    val live = ObdDisplay.show(state.obd.status)
    // Poll the extra PIDs only while this page is on screen.
    DisposableEffect(Unit) { a.setObdExtended(true); onDispose { a.setObdExtended(false) } }

    SettingRow("Adapter", if (live) null else "Connect an OBD-II adapter in OBD-II settings, or choose Demo to try this page.") { ObdBadge(state.obd.status) }
    Divider()

    // -------- trouble codes
    val d = state.diag
    var confirmClear by remember { mutableStateOf(false) }
    SmallTitle("Trouble codes")
    SettingRow(
        "Check engine light",
        when (d.milOn) { true -> "On"; false -> "Off"; null -> "Read codes to check" },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enabled = live && !d.busy
            val dim = Modifier.alpha(if (enabled) 1f else 0.4f)
            PillButton(if (d.stored == null) "Read codes" else "Read again", kind = ButtonKind.PRIMARY, modifier = dim, onClick = { if (enabled) { confirmClear = false; a.readObdCodes() } })
            PillButton("Clear codes", modifier = dim, onClick = { if (enabled) confirmClear = true })
        }
    }
    if (confirmClear) {
        Column(Modifier.fillMaxWidth().clip(TShapes.card).background(T.colors.card).padding(14.dp)) {
            Text("Clear all trouble codes?", style = TType.button, color = T.colors.text)
            Text(
                "This turns the check engine light off and resets the emission readiness checks. It doesn't fix the fault: " +
                    "if the problem is still there, the code comes back. Best done with the engine off and ignition on.",
                style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Cancel", onClick = { confirmClear = false })
                PillButton("Clear codes", kind = ButtonKind.PRIMARY, onClick = { confirmClear = false; a.clearObdCodes() })
            }
        }
    }
    d.message?.let { Text(it, style = TType.label, color = if (d.error) RED else T.colors.muted, modifier = Modifier.padding(vertical = 6.dp)) }
    d.stored?.let { CodeList("Stored", "Confirmed faults; these turn the check engine light on.", it, RED) }
    d.pending?.let { CodeList("Pending", "Seen once but not confirmed yet. An early warning.", it, AMBER) }
    Divider()

    // -------- live data
    SmallTitle("Live data")
    LiveGrid(state.obd.data, state.settings.tempUnit == TempUnit.F, state.settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH, live)
    Text(
        "Values the car's computer doesn't report show as \"Not supported\".",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CodeList(title: String, hint: String, codes: List<String>, color: Color) {
    Text("$title (${codes.size})", style = TType.button, color = T.colors.text, modifier = Modifier.padding(top = 10.dp))
    Text(hint, style = TType.label, color = T.colors.muted)
    if (codes.isEmpty()) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(GREEN))
            Spacer(Modifier.width(8.dp))
            Text("None", style = TType.body, color = T.colors.text)
        }
    }
    codes.forEach { c ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(TShapes.card).background(T.colors.card).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(c, style = TType.button, color = T.colors.text, modifier = Modifier.width(64.dp))
            Text(Dtc.describe(c), style = TType.body, color = T.colors.text, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveGrid(d: ObdData, f: Boolean, mph: Boolean, live: Boolean) {
    fun temp(c: Int) = if (f) "${(c * 9 / 5.0 + 32).roundToInt()} °F" else "$c °C"
    fun pct(v: Double) = (if (v > 0) "+" else "") + "%.1f %%".format(v)
    val items = listOf(
        Triple("Speed", null, d.speedKmh?.let { if (mph) "${(it * 0.621371).roundToInt()} mph" else "$it km/h" }),
        Triple("Engine", null, d.rpm?.let { "%,d rpm".format(it) }),
        Triple("Coolant", null, d.coolantC?.let(::temp)),
        Triple("Battery", null, d.batteryV?.let { "%.1f V".format(it) }),
        Triple("Engine load", "04", d.loadPct?.let { "$it %" }),
        Triple("Throttle", "11", d.throttlePct?.let { "$it %" }),
        Triple("Intake air", "0F", d.intakeC?.let(::temp)),
        Triple("Air flow (MAF)", "10", d.mafGs?.let { "%.1f g/s".format(it) }),
        Triple("Timing advance", "0E", d.timingDeg?.let { "%.1f°".format(it) }),
        Triple("Fuel trim, short", "06", d.stftPct?.let(::pct)),
        Triple("Fuel trim, long", "07", d.ltftPct?.let(::pct)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, pid, value) ->
                    val text = when {
                        !live -> "—"
                        value != null -> value
                        pid != null && pid in d.unsupported -> "Not supported"
                        else -> "…"
                    }
                    Column(Modifier.weight(1f).clip(TShapes.card).background(T.colors.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(label, style = TType.caption, color = T.colors.muted)
                        Text(text, style = TType.button, color = if (value != null) T.colors.text else T.colors.muted)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
