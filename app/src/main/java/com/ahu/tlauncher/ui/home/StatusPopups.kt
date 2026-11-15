package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.data.SpeedUnit
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.system.BtDetails
import com.ahu.tlauncher.system.BtStatus
import com.ahu.tlauncher.system.CellDetails
import com.ahu.tlauncher.system.CellStatus
import com.ahu.tlauncher.system.HourForecast
import com.ahu.tlauncher.system.Weather
import com.ahu.tlauncher.system.WeatherKind
import com.ahu.tlauncher.system.WifiDetails
import com.ahu.tlauncher.system.WifiStatus
import com.ahu.tlauncher.ui.components.TIcons
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * Popups under the status bar's icons, built like the notifications list: one card with a title
 * and a close button. TLauncher can't switch Wi-Fi or Bluetooth itself (Android 10+ / 13+ block
 * apps from doing it), so each popup ends with a shortcut to the matching Android settings page.
 */

private const val REFRESH_MS = 5_000L
/** Wi-Fi scans are throttled by Android (4 per 2 minutes), so only every sixth refresh asks for one. */
private const val SCAN_EVERY = 6

// ---------------------------------------------------------------- Bluetooth

@Composable
fun BluetoothPopup(state: HomeUiState, actions: HomeActions, onClose: () -> Unit) {
    val details by produceState<BtDetails?>(null, state.bluetooth) {
        while (true) { value = actions.bluetoothDetails(); delay(REFRESH_MS) }
    }
    // The popup's own reading is the freshest; until it arrives, the status bar's.
    BluetoothPopupContent(details?.status ?: state.bluetooth, details, actions::openBluetoothSettings, onClose)
}

@Composable
internal fun BluetoothPopupContent(status: BtStatus, details: BtDetails?, openSettings: () -> Unit, onClose: () -> Unit) {
    StatusPopupFrame("Bluetooth", onClose) {
        val connectedName = details?.devices?.firstOrNull { it.connected }?.name
        when (status) {
            BtStatus.OFF -> StatusRow(TIcons.BluetoothOff, "Bluetooth is off", "Turn it on in Bluetooth settings", strong = false)
            BtStatus.ON -> StatusRow(TIcons.Bluetooth, "On", "No device connected", strong = false)
            BtStatus.CONNECTED -> StatusRow(TIcons.Bluetooth, connectedName?.let { "Connected to $it" } ?: "Connected", null, strong = true)
        }
        if (status != BtStatus.OFF) {
            when {
                details == null -> Unit
                !details.permitted -> Hint("Allow “Nearby devices” for TLauncher to list your paired devices.")
                details.devices.isEmpty() -> Hint("No paired devices")
                else -> {
                    SectionLabel("Paired devices")
                    LazyColumn(Modifier.heightIn(max = 232.dp)) {
                        // Android has no public way for an app to connect a device: the system page does it.
                        items(details.devices) { d ->
                            ListRow(TIcons.Bluetooth, d.name, strong = d.connected, onClick = openSettings, tag = if (d.connected) "Connected" else null)
                        }
                    }
                }
            }
        }
        SettingsButton("Bluetooth settings", openSettings)
    }
}

// ---------------------------------------------------------------- Wi-Fi

@Composable
fun WifiPopup(state: HomeUiState, actions: HomeActions, onClose: () -> Unit) {
    val details by produceState<WifiDetails?>(null, state.wifi.enabled) {
        var n = 0
        while (true) {
            val scan = n++ % SCAN_EVERY == 0
            value = withContext(Dispatchers.IO) { actions.wifiDetails(scan) }
            delay(REFRESH_MS)
        }
    }
    WifiPopupContent(state.wifi, details, actions::openWifiSettings, onClose)
}

@Composable
internal fun WifiPopupContent(status: WifiStatus, details: WifiDetails?, openSettings: () -> Unit, onClose: () -> Unit) {
    StatusPopupFrame("Wi-Fi", onClose) {
        val name = details?.ssid
        when {
            !status.enabled -> StatusRow(TIcons.WifiOff, "Wi-Fi is off", "Turn it on in Wi-Fi settings", strong = false)
            status.connected -> StatusRow(TIcons.wifi(bars(status.level)), name ?: "Connected", "Connected · ${signalWord(status.level)} signal", strong = true)
            else -> StatusRow(TIcons.wifi(0), "Not connected", null, strong = false)
        }
        if (status.enabled) {
            when {
                details == null -> Hint("Looking for networks…")
                !details.canScan -> Hint("Allow location for TLauncher to show network names and nearby networks.")
                details.nearby.isEmpty() -> Hint("No other networks found")
                else -> {
                    SectionLabel("Nearby networks")
                    LazyColumn(Modifier.heightIn(max = 232.dp)) {
                        items(details.nearby, key = { it.ssid }) { n ->
                            ListRow(TIcons.wifi(n.level), n.ssid, strong = false, onClick = openSettings, locked = n.secured)
                        }
                    }
                }
            }
        }
        SettingsButton("Wi-Fi settings", openSettings)
    }
}

/** Status bar levels 0–4 → icon bars 0–3 (same mapping as the status bar). */
private fun bars(level: Int) = level.coerceIn(1, 4).let { if (it >= 3) 3 else it }

private fun signalWord(level: Int) = when (level) { 4 -> "Excellent"; 3 -> "Good"; 2 -> "Fair"; else -> "Weak" }

// ---------------------------------------------------------------- Mobile

@Composable
fun CellPopup(state: HomeUiState, actions: HomeActions, onClose: () -> Unit) {
    val details by produceState<CellDetails?>(null, state.cell) {
        while (true) { value = withContext(Dispatchers.IO) { actions.cellDetails() }; delay(REFRESH_MS) }
    }
    CellPopupContent(state.cell, details, actions::openMobileSettings, onClose)
}

@Composable
internal fun CellPopupContent(status: CellStatus?, details: CellDetails?, openSettings: () -> Unit, onClose: () -> Unit) {
    StatusPopupFrame("Mobile network", onClose) {
        if (status == null) {
            StatusRow(TIcons.signalBars(0, true), "No SIM", "Insert a SIM card for mobile data", strong = false)
        } else {
            val parts = listOfNotNull(status.type ?: "Mobile data", "${status.level} of 4 bars", "Roaming".takeIf { details?.roaming == true })
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp)) {
                    Icon(TIcons.signalBars(status.level, false), null, tint = T.colors.muted, modifier = Modifier.size(24.dp))
                    Icon(TIcons.signalBars(status.level, true), "Signal ${status.level} of 4", tint = T.colors.text, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(details?.carrier ?: "Mobile network", style = TType.button, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(parts.joinToString(" · "), style = TType.label, color = T.colors.muted)
                }
            }
        }
        SettingsButton("Mobile network settings", openSettings)
    }
}

// ---------------------------------------------------------------- Weather

@Composable
fun WeatherPopup(state: HomeUiState, onClose: () -> Unit) {
    WeatherPopupContent(
        state.weather, state.now, state.settings.tempUnit == TempUnit.F, state.settings.speedUnit == SpeedUnit.MPH, is24h(), onClose,
    )
}

@Composable
internal fun WeatherPopupContent(w: Weather?, now: LocalDateTime, fahrenheit: Boolean, mph: Boolean, h24: Boolean, onClose: () -> Unit) {
    StatusPopupFrame("Weather", onClose) {
        if (w == null) { Hint("Weather appears once TLauncher has your location and internet."); return@StatusPopupFrame }
        fun deg(c: Double) = "${(if (fahrenheit) c * 9 / 5 + 32 else c).roundToInt()}°"
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(w.kind, w.isDay), null, tint = T.colors.text, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(deg(w.tempC) + if (fahrenheit) "F" else "C", style = TType.hero, color = T.colors.text)
                Text(conditionName(w.kind), style = TType.body, color = T.colors.muted)
            }
        }
        val extras = listOfNotNull(
            w.feelsC?.let { "Feels like ${deg(it)}" },
            w.humidityPct?.let { "Humidity $it%" },
            w.windKmh?.let { if (mph) "Wind ${(it * 0.621371).roundToInt()} mph" else "Wind ${it.roundToInt()} km/h" },
        )
        if (extras.isNotEmpty()) Text(extras.joinToString("  ·  "), style = TType.label, color = T.colors.body, modifier = Modifier.padding(top = 2.dp))
        val hours = upcomingHours(w, now)
        if (hours.isNotEmpty()) {
            SectionLabel("Next hours")
            Row(Modifier.fillMaxWidth().clip(TShapes.card).background(T.colors.card).padding(vertical = 10.dp)) {
                hours.forEach { h -> HourCell(h, ::deg, h24, Modifier.weight(1f)) }
            }
        }
        Text(
            "Updated " + ago(now, w.fetchedAtMs).let { if (it == "now") "just now" else "$it ago" },
            style = TType.caption, color = T.colors.muted, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun HourCell(h: HourForecast, deg: (Double) -> String, h24: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(hourLabel(h.hour, h24), style = TType.caption, color = T.colors.muted)
        Icon(weatherIcon(h.kind, h.isDay), conditionName(h.kind), tint = T.colors.text, modifier = Modifier.size(22.dp))
        Text(deg(h.tempC), style = TType.button, color = T.colors.text)
        // Only worth showing when there's a real chance of rain.
        Text(h.rainPct?.takeIf { it >= 10 }?.let { "$it%" } ?: " ", style = TType.caption, color = RAIN_BLUE)
    }
}

private val RAIN_BLUE = Color(0xFF69A8EA)

/** "14:00" or "2 PM". */
internal fun hourLabel(hour: Int, h24: Boolean): String =
    if (h24) "%02d:00".format(hour) else when (hour) { 0 -> "12 AM"; 12 -> "12 PM"; in 1..11 -> "$hour AM"; else -> "${hour - 12} PM" }

/** Forecast hours still ahead: drops the hours that have started since the forecast was fetched (it refreshes every 30 min). */
internal fun upcomingHours(w: Weather, now: LocalDateTime): List<HourForecast> {
    val fetched = Instant.ofEpochMilli(w.fetchedAtMs).atZone(ZoneId.systemDefault()).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
    val passed = Duration.between(fetched, now.truncatedTo(ChronoUnit.HOURS)).toHours().coerceAtLeast(0)
    return w.hourly.drop(passed.toInt())
}

internal fun conditionName(k: WeatherKind) = when (k) {
    WeatherKind.CLEAR -> "Clear"
    WeatherKind.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherKind.CLOUDY -> "Cloudy"
    WeatherKind.FOG -> "Fog"
    WeatherKind.DRIZZLE -> "Drizzle"
    WeatherKind.RAIN -> "Rain"
    WeatherKind.SNOW -> "Snow"
    WeatherKind.STORM -> "Thunderstorm"
}

// ---------------------------------------------------------------- shared pieces

/** Same card as the notifications list: [STATUS_POPUP_W] wide, title row with a close button. */
@Composable
private fun StatusPopupFrame(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val surface = if (T.colors.isDark) Color(0xFF22252B) else T.colors.panel
    Column(Modifier.width(STATUS_POPUP_W).clip(TShapes.surface).background(surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TType.title, color = T.colors.text, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(TIcons.Close, "Close", tint = T.colors.text, modifier = Modifier.size(16.dp))
            }
        }
        content()
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, detail: String?, strong: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (strong) T.colors.text else T.colors.muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TType.button, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = TType.label, color = T.colors.muted)
        }
    }
}

@Composable
private fun ListRow(icon: ImageVector, title: String, strong: Boolean, onClick: () -> Unit, tag: String? = null, locked: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(TShapes.card).pressable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (strong) T.colors.text else T.colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = TType.body, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (tag != null) Text(tag, style = TType.label, color = T.colors.green, modifier = Modifier.padding(start = 8.dp))
        if (locked) Icon(Icons.Rounded.Lock, "Password protected", tint = T.colors.muted, modifier = Modifier.padding(start = 8.dp).size(14.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = TType.caption, color = T.colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = TType.label, color = T.colors.muted, modifier = Modifier.padding(vertical = 10.dp))
}

/** Full-width shortcut to an Android settings page. */
@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(TShapes.button).background(T.colors.card)
            .pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TType.button, color = T.colors.text, modifier = Modifier.weight(1f))
        Icon(TIcons.ChevronRight, null, tint = T.colors.muted, modifier = Modifier.size(18.dp))
    }
}
