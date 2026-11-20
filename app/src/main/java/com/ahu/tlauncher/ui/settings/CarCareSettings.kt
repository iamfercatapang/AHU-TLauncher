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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.system.Coding
import com.ahu.tlauncher.system.MaintenanceMath
import com.ahu.tlauncher.system.ServiceItem
import com.ahu.tlauncher.system.TripMath
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.Segmented
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A short text box with a Save action (on Done, or when the button beside it is pressed). */
@Composable
private fun SmallField(value: String, hint: String, number: Boolean, width: Int, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(width.dp).height(42.dp).clip(TShapes.inner).background(T.colors.card).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(hint, style = TType.body, color = T.colors.muted)
            BasicTextField(
                text, { text = it }, singleLine = true, textStyle = TType.body.copy(color = T.colors.text),
                cursorBrush = SolidColor(T.colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text != value) { Spacer(Modifier.width(8.dp)); PillButton("Save", kind = ButtonKind.PRIMARY, onClick = { onSave(text); focus.clearFocus() }) }
    }
}

// ------------------------------------------------------------------------------------------------ number coding

/** Settings → Navigation → Number coding. */
@Composable
internal fun CodingSettings(state: HomeUiState, a: HomeActions) {
    val s = state.settings
    SmallTitle("Number coding")
    Text(
        "Metro Manila coding by the plate's last digit: Monday 1–2, Tuesday 3–4, Wednesday 5–6, Thursday 7–8, Friday 9–0. " +
            "The status bar shows it on your coding day, and starting a trip during the hours warns you. Holidays aren't known to the app.",
        style = TType.label, color = T.colors.muted,
    )
    SettingRow("Plate ends in", if (s.codingDigit < 0) "Off" else Coding.day(s.codingDigit)?.let { "Coding on " + it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) {}
    // Off, then 1–9 and 0 in two rows of tappable digits.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(listOf(-1, 1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 0)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    val sel = d == s.codingDigit
                    Box(
                        Modifier.width(if (d < 0) 64.dp else 48.dp).height(40.dp).clip(TShapes.button)
                            .background(if (sel) T.colors.accent else T.colors.card).pressable { a.setCoding(d, s.codingHours) },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (d < 0) "Off" else "$d", style = TType.button, color = if (sel) T.colors.onAccent else T.colors.text) }
                }
            }
        }
    }
    SettingRow("Coding hours", "24-hour, e.g. 7-10, 17-20. Leave as is for the usual MMDA window.") {
        SmallField(s.codingHours, Coding.DEFAULT_HOURS, number = false, width = 150) { h ->
            a.setCoding(s.codingDigit, h.takeIf { Coding.parseHours(it).isNotEmpty() } ?: Coding.DEFAULT_HOURS)
        }
    }
}

// ------------------------------------------------------------------------------------------------ maintenance

/** Settings → Car → Maintenance: odometer, service items (due by km and date), engine fault codes. */
@Composable
internal fun MaintenanceSettings(state: HomeUiState, a: HomeActions) {
    SmallTitle("Maintenance")
    SettingRow(
        "Odometer",
        if (state.odometerKm == null) "Enter what the dashboard shows once; it then counts up with the trip log"
        else "%,d km now (counted from the trip log; enter it again to correct it)".format(Locale.US, state.odometerKm),
    ) {
        SmallField(state.odometerKm?.toString().orEmpty(), "km", number = true, width = 120) { t ->
            t.filter { it.isDigit() }.toIntOrNull()?.let(a::setOdometer)
        }
    }
    val tracked = state.serviceDue.associateBy { it.item }
    ServiceItem.entries.forEach { item ->
        Divider()
        val due = tracked[item]
        val rec = state.maintenance.records[item]
        SettingRow(
            item.label,
            when {
                due != null -> MaintenanceMath.text(due) + " · every %,d km".format(Locale.US, rec?.intervalKm ?: item.defaultKm) + if (item.months > 0) " or ${item.months} months" else ""
                state.odometerKm == null -> "Enter the odometer first"
                else -> "Not tracked. Press Done today after the next one (or now, if it was just done)."
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rec != null) PillButton("Stop", onClick = { a.stopService(item) })
                if (state.odometerKm != null) PillButton("Done today", kind = if (due?.soon == true) ButtonKind.PRIMARY else ButtonKind.SECONDARY, onClick = { a.markServiceDone(item) })
            }
        }
        if (rec != null) Segmented(item.choicesKm.map { it to "%,d km".format(Locale.US, it) }, rec.intervalKm, { a.setServiceInterval(item, it) }, segmentWidth = 96.dp)
    }
    Divider()
    val d = state.diag
    SettingRow(
        "Engine fault codes",
        when {
            d.stored == null && d.pending == null -> "Read them under Trouble codes below (needs the OBD adapter)"
            (d.stored.orEmpty() + d.pending.orEmpty()).isEmpty() -> "None stored at the last check"
            else -> listOfNotNull(d.stored?.takeIf { it.isNotEmpty() }?.let { "Stored: " + it.joinToString(", ") }, d.pending?.takeIf { it.isNotEmpty() }?.let { "Pending: " + it.joinToString(", ") }).joinToString(" · ")
        },
    ) {}
}

// ------------------------------------------------------------------------------------------------ trip log

/** Settings → Car → Trips: this week, this month, and the latest drives. */
@Composable
internal fun TripLogSettings(state: HomeUiState, a: HomeActions) {
    SmallTitle("Trips")
    val mph = state.settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH
    fun dist(km: Double) = if (mph) "%.1f mi".format(Locale.US, km * 0.621371) else "%.1f km".format(Locale.US, km)
    fun dur(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    fun econ(kmPerL: Double?) = kmPerL?.let { " · %.1f km/L".format(Locale.US, it) } ?: ""
    Text(
        "Every drive is logged from GPS: distance and time, and fuel used when the OBD adapter is connected." +
            (state.currentTripKm?.let { " Driving now: " + dist(it) + " so far." } ?: ""),
        style = TType.label, color = T.colors.muted,
    )
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val week = TripMath.totals(state.trips, TripMath.weekStartMs(now, zone))
    val month = TripMath.totals(state.trips, TripMath.monthStartMs(now, zone))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("This week" to week, "This month" to month).forEach { (label, t) ->
            Column(Modifier.weight(1f).clip(TShapes.card).background(T.colors.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, style = TType.caption, color = T.colors.muted)
                Text(dist(t.km), style = TType.button, color = T.colors.text)
                Text("${t.count} trip" + (if (t.count == 1) "" else "s") + " · " + dur(t.minutes) + econ(t.kmPerL), style = TType.caption, color = T.colors.muted)
            }
        }
    }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault()) }
    val latest = state.trips.takeLast(10).reversed()
    if (latest.isEmpty()) Text("No drives yet.", style = TType.body, color = T.colors.secondary, modifier = Modifier.padding(top = 10.dp))
    latest.forEach { t ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.format(Instant.ofEpochMilli(t.startMs).atZone(zone)), style = TType.body, color = T.colors.text, modifier = Modifier.weight(1f))
            Text(dist(t.km) + " · " + dur(t.minutes) + econ(t.kmPerL), style = TType.label, color = T.colors.muted)
        }
    }
    if (state.trips.isNotEmpty()) {
        var confirm by remember { mutableStateOf(false) }
        Row(Modifier.padding(top = 6.dp)) {
            PillButton(if (confirm) "Tap again to clear all trips" else "Clear trips", onClick = { if (confirm) { a.clearTrips(); confirm = false } else confirm = true })
        }
    }
}
