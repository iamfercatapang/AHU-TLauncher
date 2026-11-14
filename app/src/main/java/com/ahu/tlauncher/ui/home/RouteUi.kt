package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.route.Place
import com.ahu.tlauncher.route.RoutePhase
import com.ahu.tlauncher.route.RouteState
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val ROUTE_BLUE = Color(0xFF3E6AE1)
val ROUTE_OUTLINE = Color(0xFF1E3E9A)
val TRAFFIC_SLOW = Color(0xFFF29D38)
val TRAFFIC_JAM = Color(0xFFE5484D)
private val GOOD_GREEN = Color(0xFF25A06B)

fun fmtDuration(s: Int): String { val m = (s + 30) / 60; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
fun fmtDistance(m: Int, mph: Boolean): String = if (mph) "%.1f mi".format(m / 1609.34) else if (m < 1000) "$m m" else "%.1f km".format(m / 1000.0)

@Composable
private fun arrival(secondsLeft: Int, now: java.time.LocalDateTime): String =
    now.toLocalTime().plusSeconds(secondsLeft.toLong()).format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mma", Locale.US)).lowercase()

/** Destination search: Google suggestions as you type, plus Home / Work, favourites and recent trips. */
@Composable
fun SearchPanel(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val s = state.search
    val mph = state.settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH
    val focus = remember { FocusRequester() }
    val preview = LocalInspectionMode.current
    LaunchedEffect(Unit) { actions.refreshEtas(); if (!preview) runCatching { focus.requestFocus() } }
    Column(modifier.clip(TShapes.surface).background(T.colors.panel).padding(14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(TShapes.inner).background(T.colors.card).border(1.dp, ROUTE_BLUE, TShapes.inner).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = T.colors.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (s.query.isEmpty()) Text("Where to?", style = TType.body, color = T.colors.muted)
                BasicTextField(
                    s.query, actions::searchPlaces, singleLine = true, textStyle = TType.body.copy(color = T.colors.text),
                    cursorBrush = SolidColor(ROUTE_BLUE), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            IconButtonT(Icons.Rounded.Close, "Close search", size = 44.dp, iconSize = 20.dp, tint = T.colors.muted) { actions.clearSearch(); onClose() }
        }
        // shortcuts: Home, Work, favourites
        Row(Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val st = state.settings
            listOf(Triple(Icons.Rounded.Home, "Home", st.homeAddress), Triple(Icons.Rounded.Work, "Work", st.workAddress)).forEach { (icon, label, addr) ->
                val eta = state.etaMin[addr]?.let { " · $it min" } ?: ""
                Chip(icon, label + eta) { if (label == "Home") actions.navigateHome() else actions.navigateWork(); onClose() }
            }
            state.favourites.forEach { p -> Chip(Icons.Rounded.Star, p.name) { actions.previewPlace(p); onClose() } }
        }
        when {
            s.error != null -> Text(s.error, style = TType.label, color = TRAFFIC_JAM, modifier = Modifier.padding(8.dp))
            s.query.isNotBlank() -> {
                if (s.suggestions.isEmpty() && !s.loading) Text("No places found", style = TType.label, color = T.colors.muted, modifier = Modifier.padding(8.dp))
                s.suggestions.forEach { sg ->
                    PlaceRow(Icons.Rounded.Place, sg.title, sg.subtitle, sg.distanceM?.let { fmtDistance(it, mph) }) { actions.pickSuggestion(sg); onClose() }
                }
                Text("Powered by Google", style = TType.caption, color = T.colors.muted, modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
            }
            state.recents.isNotEmpty() -> {
                Text("Recent", style = TType.caption, color = T.colors.muted, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                state.recents.take(5).forEach { p -> PlaceRow(Icons.Rounded.History, p.name, p.address, null) { actions.previewPlace(p); onClose() } }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(TShapes.button).background(T.colors.card).pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = T.colors.text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(label, style = TType.label, color = T.colors.text, maxLines = 1)
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, title: String, subtitle: String, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(TShapes.card).pressable(onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = T.colors.muted, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TType.button, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = TType.caption, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); Text(it, style = TType.caption, color = T.colors.muted) }
    }
}

/** After picking a place: destination, time, distance, traffic note, Cancel / Start. */
@Composable
fun RoutePreviewCard(state: HomeUiState, r: RouteState, actions: HomeActions, modifier: Modifier = Modifier) {
    val mph = state.settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH
    val fav = state.favourites.any { it.sameAs(r.destination) }
    val navName = if (state.settings.navApp == com.ahu.tlauncher.data.NavApp.WAZE) "Waze" else "Google Maps"
    Column(modifier.widthIn(max = 420.dp).clip(TShapes.surface).background(T.colors.panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn, null, tint = TRAFFIC_JAM, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(r.destination.name, style = TType.title, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.destination.address.isNotBlank() && r.destination.address != r.destination.name)
                    Text(r.destination.address, style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButtonT(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "Remove favourite" else "Add to favourites", size = 40.dp, iconSize = 22.dp,
                tint = if (fav) TRAFFIC_SLOW else T.colors.muted) { actions.toggleFavourite(r.destination) }
        }
        Spacer(Modifier.height(8.dp))
        val route = r.route
        when {
            r.error != null -> Text(r.error, style = TType.label, color = TRAFFIC_JAM)
            route == null -> Text("Finding the best route…", style = TType.label, color = T.colors.muted)
            else -> {
                val jam = route.traffic.any { it.speed != com.ahu.tlauncher.route.Traffic.NORMAL }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtDuration(route.durationS), style = TType.title, color = if (jam) TRAFFIC_SLOW else GOOD_GREEN); Spacer(Modifier.width(8.dp))
                    Text(
                        fmtDistance(route.distanceM, mph) + (if (route.via.isNotBlank()) " · via ${route.via}" else "") + " · arrive ${arrival(route.durationS, state.now)}",
                        style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (jam) Text("Some slow traffic on the way", style = TType.caption, color = TRAFFIC_SLOW)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.clip(TShapes.button).background(T.colors.card).pressable(onClick = actions::endRoute).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Cancel", style = TType.button, color = T.colors.text)
            }
            val ready = route != null
            Row(
                Modifier.weight(1f).clip(TShapes.button).background(if (ready) ROUTE_BLUE else ROUTE_BLUE.copy(alpha = 0.4f))
                    .pressable { if (ready) actions.startRoute() }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(18.dp).rotate(45f)); Spacer(Modifier.width(6.dp))
                Text("Start · voice by $navName", style = TType.button, color = Color.White, maxLines = 1)
            }
        }
    }
}

/** During the trip: arrival time, time and distance left, End. */
@Composable
fun TripCard(state: HomeUiState, r: RouteState, actions: HomeActions, modifier: Modifier = Modifier) {
    val mph = state.settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH
    val left = r.remainingS ?: r.route?.durationS ?: 0
    Row(
        modifier.clip(TShapes.surface).background(T.colors.frost).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${arrival(left, state.now)} arrival", style = TType.title, color = T.colors.text)
            Text(
                "${fmtDuration(left)} · ${fmtDistance(r.remainingM ?: r.route?.distanceM ?: 0, mph)} · ${r.destination.name}",
                style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.clip(TShapes.button).background(TRAFFIC_JAM).pressable(onClick = actions::endRoute).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Text("End", style = TType.button, color = Color.White)
        }
    }
}

@Composable
fun TrafficLegend(modifier: Modifier = Modifier) {
    Row(modifier.clip(TShapes.button).background(T.colors.frost).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(TRAFFIC_SLOW)); Spacer(Modifier.width(4.dp)); Text("Slow", style = TType.caption, color = T.colors.muted)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(TRAFFIC_JAM)); Spacer(Modifier.width(4.dp)); Text("Heavy", style = TType.caption, color = T.colors.muted)
    }
}

/** Route overlays on the map pane: preview card, or trip card + legend. */
@Composable
fun RouteOverlays(state: HomeUiState, actions: HomeActions, modifier: Modifier) {
    val r = state.route ?: return
    Box(modifier) {
        if (r.phase == RoutePhase.PREVIEW) RoutePreviewCard(state, r, actions, Modifier.align(Alignment.BottomStart).padding(12.dp))
        else {
            TripCard(state, r, actions, Modifier.align(Alignment.BottomStart).padding(12.dp))
            if (r.route?.traffic?.any { it.speed != com.ahu.tlauncher.route.Traffic.NORMAL } == true)
                TrafficLegend(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 24.dp))
        }
    }
}
