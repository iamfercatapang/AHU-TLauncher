package com.ahu.tlauncher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.map.AreaStatus
import com.ahu.tlauncher.map.OfflineArea
import com.ahu.tlauncher.map.OfflineMath
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.Segmented
import com.ahu.tlauncher.ui.components.TSwitch
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TType

/** Settings → Navigation: what happens when the car starts. */
@Composable
internal fun OnStartSettings(state: HomeUiState, a: HomeActions) {
    val s = state.settings
    SmallTitle("When the car starts")
    SettingRow("Resume music", "Plays YouTube Music or Spotify again if it was playing when the car was switched off") {
        TSwitch(s.resumeMusic, a::setResumeMusic)
    }
    SettingRow("Offer to continue the trip", "If the car was switched off before arriving, the map asks whether to continue") {
        TSwitch(s.offerTrip, a::setOfferTrip)
    }
}

/** Settings → Navigation: offline areas for the free map. */
@Composable
internal fun OfflineMapsSettings(state: HomeUiState, a: HomeActions) {
    LaunchedEffect(Unit) { a.loadOfflineAreas() }
    SmallTitle("Offline maps")
    Text(
        "Save the free map (OpenFreeMap) around a spot so it still shows with no data connection. Each area is up to 50 MB. " +
            "Areas update themselves weekly on Wi-Fi that isn't a phone hotspot; otherwise press Update now and then." +
            if (state.settings.mapEngine == com.ahu.tlauncher.data.MapEngine.GOOGLE && state.googleMapsAvailable) " Only used when Map is set to Free." else "",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(bottom = 4.dp),
    )
    state.offlineAreas.forEach { AreaRow(it, a) }

    // New area: around the dropped pin if there is one, else around the car.
    val pin = state.pin?.latLon
    val lat = pin?.lat ?: state.locationLat
    val lon = pin?.lon ?: state.locationLon
    if (lat == null || lon == null) {
        SettingRow("Save an area", "Waiting for GPS position… (or long-press the map to drop a pin)") {}
        return
    }
    val radii = remember(Math.round(lat * 10), Math.round(lon * 10)) { OfflineMath.radiiFor(lat, lon) }
    if (radii.isEmpty()) return
    var radius by remember(radii) { mutableIntStateOf(radii.firstOrNull { it == 25 } ?: radii.last()) }
    val name = state.pin?.name?.takeIf { pin != null && it.isNotBlank() } ?: "Around %.3f, %.3f".format(lat, lon)
    SettingRow("Save an area", if (pin != null) "Around the dropped pin: ${state.pin?.name}" else "Around where the car is now. Long-press the map to drop a pin elsewhere.") {}
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Segmented(radii.map { it to "$it km" }, radius, { radius = it })
        PillButton("Save", kind = ButtonKind.PRIMARY) { a.saveOfflineArea(name, lat, lon, radius) }
    }
}

@Composable
private fun AreaRow(area: OfflineArea, a: HomeActions) {
    val mb = "%.0f MB".format(area.sizeBytes / 1_048_576.0)
    val days = ((System.currentTimeMillis() - area.savedAtMs) / 86_400_000L).toInt()
    val updated = when (days) { 0 -> "updated today"; 1 -> "updated yesterday"; else -> "updated $days days ago" }
    val sub = when (area.status) {
        AreaStatus.DOWNLOADING -> "Downloading ${(area.progress * 100).toInt()}% · $mb"
        AreaStatus.READY -> listOfNotNull("${area.radiusKm} km radius · $mb · $updated", area.error).joinToString(" · ")
        AreaStatus.FAILED, AreaStatus.TOO_LARGE -> area.error ?: "Download failed"
    }
    SettingRow(area.name, sub) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (area.status != AreaStatus.DOWNLOADING && area.status != AreaStatus.TOO_LARGE) PillButton(if (area.status == AreaStatus.READY) "Update" else "Retry") { a.updateOfflineArea(area.id) }
            if (area.status != AreaStatus.DOWNLOADING) PillButton("Delete") { a.deleteOfflineArea(area.id) }
        }
    }
}
