package com.ahu.tlauncher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.tlauncher.data.savedPlace
import com.ahu.tlauncher.route.FreeNav
import com.ahu.tlauncher.route.LatLon
import com.ahu.tlauncher.route.Place
import com.ahu.tlauncher.route.RouteData
import com.ahu.tlauncher.route.RouteMath
import com.ahu.tlauncher.route.RoutePhase
import com.ahu.tlauncher.route.RouteState
import com.ahu.tlauncher.route.Traffic
import com.ahu.tlauncher.ui.components.TIcons
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.LocalLowPerf
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TType
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Route line on the map: the prototype's lighter sky blue, with a darker casing. */
val ROUTE_LINE = Color(0xFF4C9BF5)
val ROUTE_LINE_CASING = Color(0xFF1D5FB8)
/** Routes not chosen in the preview. */
val ROUTE_ALT = Color(0xFF8A96A8)
val ROUTE_ALT_CASING = Color(0xFF5A6474)
val TRAFFIC_SLOW = Color(0xFFF29D38)
val TRAFFIC_JAM = Color(0xFFE5484D)

fun fmtDuration(s: Int): String { val m = (s + 30) / 60; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
/** Like [fmtDistance] but without decimals from 10 km / 10 mi up ("332 km"), for narrow list rows. */
fun fmtDistanceShort(m: Int, mph: Boolean): String = when {
    mph && m >= 16_093 -> "${(m / 1609.34).roundToInt()} mi"
    !mph && m >= 10_000 -> "${(m / 1000.0).roundToInt()} km"
    else -> fmtDistance(m, mph)
}
fun fmtDistance(m: Int, mph: Boolean): String = if (mph) "%.1f mi".format(m / 1609.34) else if (m < 1000) "$m m" else "%.1f km".format(m / 1000.0)

/** "6" + "min", or "1:20" + "h": the big number and its unit, for route tiles and the trip card. */
private fun durationParts(s: Int): Pair<String, String> { val m = (s + 30) / 60; return if (m >= 60) "%d:%02d".format(m / 60, m % 60) to "h" else "$m" to "min" }
/** Split "1.8 km" into "1.8" + "km". */
private fun distanceParts(m: Int, mph: Boolean): Pair<String, String> = fmtDistance(m, mph).split(' ').let { it[0] to it.getOrElse(1) { "" } }

@Composable
private fun arrival(secondsLeft: Int, now: java.time.LocalDateTime): String =
    now.toLocalTime().plusSeconds(secondsLeft.toLong()).format(DateTimeFormatter.ofPattern(if (is24h()) "HH:mm" else "h:mma", Locale.US)).lowercase()

private val HomeUiState.mph get() = settings.speedUnit == com.ahu.tlauncher.data.SpeedUnit.MPH
private val HomeUiState.here: LatLon? get() = locationLat?.let { lat -> locationLon?.let { lon -> LatLon(lat, lon) } }
private fun RouteData.hasTraffic() = traffic.any { it.speed != Traffic.NORMAL }

// ------------------------------------------------------------------------------------------------ look

/**
 * Colours for the navigation cards: frosted slate panels over the map, as in the Tesla UI prototype's
 * navigation screens, with a light variant for the day theme.
 */
@Immutable
data class NavColors(
    val panel: Color,
    val tile: Color,
    val tilePressed: Color,
    val tileSelected: Color,
    val tileSelectedBorder: Color,
    val border: Color,
    val text: Color,
    val sub: Color,
    val faint: Color,
    val badge: Color,
    val cta: Color,
    val onCta: Color,
    val good: Color,
    val danger: Color,
    val star: Color,
)

private val DarkNav = NavColors(
    panel = Color(0xF21B1E23), tile = Color(0xFF262A31), tilePressed = Color(0xFF2E333B),
    tileSelected = Color(0xFF243349), tileSelectedBorder = Color(0xFF5B86C0), border = Color.White.copy(alpha = 0.07f),
    text = Color.White, sub = Color.White.copy(alpha = 0.58f), faint = Color.White.copy(alpha = 0.36f), badge = Color(0xFF2C3139),
    cta = Color(0xFFE7EDF4), onCta = Color(0xFF15181D), good = Color(0xFF8FD3A0), danger = Color(0xFFF08A86), star = Color(0xFFF2C14E),
)

private val LightNav = NavColors(
    panel = Color(0xF7FFFFFF), tile = Color(0xFFF1F3F6), tilePressed = Color(0xFFE6E9EE),
    tileSelected = Color(0xFFE3ECF9), tileSelectedBorder = Color(0xFF6E96CF), border = Color.Black.copy(alpha = 0.07f),
    text = Color(0xFF171A20), sub = Color(0xFF5C5E62), faint = Color(0xFF8E8E8E), badge = Color(0xFFEDEFF2),
    cta = Color(0xFF171A20), onCta = Color.White, good = Color(0xFF1F8A4C), danger = Color(0xFFD03B3F), star = Color(0xFFE0A21B),
)

val navColors: NavColors @Composable get() = if (T.colors.isDark) DarkNav else LightNav

private val PanelShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(12.dp)
/** Width of the floating navigation cards (prototype: ~270 px of an 840 px map). */
val NAV_CARD_W = 340.dp
/** The search card is wider, so place names and addresses have room to tell results apart. */
val SEARCH_CARD_W = 440.dp

/** Floating card over the map: frosted panel, hairline border, soft shadow (none in low-performance mode). */
@Composable
fun Modifier.navPanel(): Modifier {
    val c = navColors
    val shadow = if (LocalLowPerf.current) this else this.shadow(14.dp, PanelShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
    return shadow.clip(PanelShape).background(c.panel).border(1.dp, c.border, PanelShape)
}

/** Prototype's navigation panel entrance: drops in from just above at 98.5 % scale, 230 ms. */
private val NavEase = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)
fun navEnter(): EnterTransition =
    fadeIn(tween(230, easing = NavEase)) + scaleIn(tween(230, easing = NavEase), initialScale = 0.985f) +
        slideInVertically(tween(230, easing = NavEase)) { -(it * 0.04f).toInt().coerceAtMost(24) }
fun navExit(): ExitTransition =
    fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.985f) + slideOutVertically(tween(150)) { -(it * 0.03f).toInt().coerceAtMost(18) }

@Composable
private fun NavIconButton(icon: ImageVector, desc: String, size: Dp = 36.dp, tint: Color = navColors.sub, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Round icon badge at the start of a place row. */
@Composable
private fun Badge(icon: ImageVector, tint: Color = navColors.sub) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(navColors.badge), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionHeader(left: String, right: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
        Text(left, style = TType.caption, color = navColors.faint, modifier = Modifier.weight(1f))
        right?.let { Text(it, style = TType.caption, color = navColors.faint) }
    }
}

// ------------------------------------------------------------------------------------------------ search

/** Destination search: Home / Work, then favourites and recent trips, or results as you type. */
@Composable
fun SearchPanel(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = navColors
    val s = state.search
    val focus = remember { FocusRequester() }
    val preview = LocalInspectionMode.current
    LaunchedEffect(Unit) { actions.refreshEtas(); if (!preview) runCatching { focus.requestFocus() } }
    Column(modifier.widthIn(max = SEARCH_CARD_W).navPanel().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(TileShape).background(c.tile).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (s.query.isEmpty()) Text("Where to?", style = TType.body, color = c.faint)
                BasicTextField(
                    s.query, actions::searchPlaces, singleLine = true, textStyle = TType.body.copy(color = c.text, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(ROUTE_LINE), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            NavIconButton(Icons.Rounded.Close, "Close search", size = 44.dp) { actions.clearSearch(); onClose() }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (s.query.isBlank()) SearchHome(state, actions, onClose) else SearchResults(state, actions, onClose)
        }
        SearchFooter(state)
    }
}

@Composable
private fun ColumnScope.SearchHome(state: HomeUiState, actions: HomeActions, onClose: () -> Unit) {
    val st = state.settings
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShortcutTile(Icons.Rounded.Home, "Home", st.homeAddress, state.etaMin[st.homeAddress]) { actions.navigateHome(); onClose() }
        ShortcutTile(Icons.Rounded.Work, "Work", st.workAddress, state.etaMin[st.workAddress]) { actions.navigateWork(); onClose() }
    }
    if (state.favourites.isNotEmpty()) {
        SectionHeader("Favourites")
        state.favourites.take(4).forEach { p ->
            PlaceRow(Icons.Rounded.Star, navColors.star, p.name, p.address, distanceTo(state, p)) { actions.previewPlace(p); onClose() }
        }
    }
    if (state.recents.isNotEmpty()) {
        SectionHeader("Recent")
        state.recents.filterNot { r -> state.favourites.any { it.sameAs(r) } }.take(5).forEach { p ->
            PlaceRow(Icons.Rounded.History, navColors.sub, p.name, p.address, distanceTo(state, p)) { actions.previewPlace(p); onClose() }
        }
    }
    if (state.favourites.isEmpty() && state.recents.isEmpty()) {
        Text(
            "Places you go and ★ favourites will show here", style = TType.caption, color = navColors.faint,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RowScope.ShortcutTile(icon: ImageVector, label: String, address: String, etaMin: Int?, onClick: () -> Unit) {
    val c = navColors
    Row(
        Modifier.weight(1f).height(58.dp).clip(TileShape).background(c.tile).pressable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.sub, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = TType.button, color = c.text, maxLines = 1)
            Text(
                when { address.isBlank() -> "Set address"; etaMin != null -> "$etaMin min"; else -> "Saved" },
                style = TType.caption, color = c.faint, maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColumnScope.SearchResults(state: HomeUiState, actions: HomeActions, onClose: () -> Unit) {
    val s = state.search
    val c = navColors
    SectionHeader("Search results", if (s.loading) "Searching…" else "${s.suggestions.size} place" + if (s.suggestions.size == 1) "" else "s")
    when {
        s.error != null -> EmptyState(s.error, "Check the connection and try again", tint = c.danger)
        s.suggestions.isEmpty() && !s.loading -> EmptyState("No places found", "Try a place name, a street or an address")
        else -> s.suggestions.forEach { sg ->
            PlaceRow(Icons.Rounded.Place, c.sub, sg.title, sg.subtitle, sg.distanceM?.let { fmtDistanceShort(it, state.mph) }) { actions.pickSuggestion(sg); onClose() }
        }
    }
}

@Composable
private fun EmptyState(title: String, hint: String, tint: Color = navColors.text) {
    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Search, null, tint = navColors.faint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = TType.button, color = tint, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = TType.caption, color = navColors.faint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchFooter(state: HomeUiState) {
    val c = navColors
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val fix = state.here != null
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (fix) TRAFFIC_JAM else c.faint))
        Spacer(Modifier.width(8.dp))
        Text(if (fix) "Current location" else "Waiting for GPS…", style = TType.caption, color = c.faint, modifier = Modifier.weight(1f))
        Text(if (state.freeNav) "Photon · © OpenStreetMap" else "Powered by Google", style = TType.caption, color = c.faint)
    }
}

private fun distanceTo(state: HomeUiState, p: Place): String? =
    state.here?.let { h -> p.latLon?.let { fmtDistanceShort(RouteMath.distanceM(h, it).toInt(), state.mph) } }

@Composable
private fun PlaceRow(icon: ImageVector, iconTint: Color, title: String, subtitle: String, trailing: String?, onClick: () -> Unit) {
    val c = navColors
    Row(
        Modifier.fillMaxWidth().clip(TileShape).pressable(onClick = onClick).padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(icon, iconTint); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Two lines each: similar names ("Iglesia Ni Cristo - Lokal ng …") differ at the end.
            Text(title, style = TType.button, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank() && subtitle != title) Text(subtitle, style = TType.caption, color = c.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); Text(it, style = TType.caption, color = c.faint, maxLines = 1) }
    }
}

// ------------------------------------------------------------------------------------------------ route preview

/** After picking a place: where from and to, route choices, arrival, Start navigation. */
@Composable
fun RoutePreviewCard(state: HomeUiState, r: RouteState, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = navColors
    val fav = state.favourites.any { it.sameAs(r.destination) }
    val navName = if (r.guided) "TLauncher" else if (state.settings.navApp == com.ahu.tlauncher.data.NavApp.WAZE) "Waze" else "Google Maps"
    Column(modifier.width(NAV_CARD_W).navPanel().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to search") { actions.endRoute(); actions.openNavApp() }
            Text("Routes", style = TType.label, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            NavIconButton(Icons.Rounded.Close, "Cancel route", onClick = actions::endRoute)
        }
        // from → to
        Row(Modifier.padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).border(1.5.dp, c.faint, CircleShape))
            Spacer(Modifier.width(14.dp))
            Text("Current location", style = TType.label, color = c.sub)
        }
        Column(Modifier.padding(start = 9.5.dp, top = 3.dp, bottom = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) { Box(Modifier.size(1.dp, 3.dp).background(c.faint)) }
        }
        Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(ROUTE_LINE))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.destination.name, style = TType.title.copy(fontSize = 22.sp, lineHeight = 26.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.destination.address.isNotBlank() && r.destination.address != r.destination.name)
                    Text(r.destination.address, style = TType.caption, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NavIconButton(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "Remove favourite" else "Add to favourites",
                tint = if (fav) c.star else c.faint) { actions.toggleFavourite(r.destination) }
        }
        Spacer(Modifier.height(12.dp))
        // route choices
        val route = r.route
        val all = r.alternatives.ifEmpty { listOfNotNull(route) }
        when {
            r.error != null -> Column(Modifier.fillMaxWidth().clip(TileShape).background(c.tile).padding(14.dp)) {
                Text(r.error, style = TType.label, color = c.danger)
                Spacer(Modifier.height(6.dp))
                Text("Try again", style = TType.button, color = c.text, modifier = Modifier.clip(TileShape).pressable { actions.previewPlace(r.destination) }.padding(vertical = 4.dp))
            }
            route == null -> Box(Modifier.fillMaxWidth().height(96.dp).clip(TileShape).background(c.tile), contentAlignment = Alignment.Center) {
                Text("Finding routes…", style = TType.label, color = c.sub)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEachIndexed { i, alt -> RouteTile(state, alt, i, selected = i == r.selected, count = all.size) { actions.selectRoute(i) } }
            }
        }
        // arrival and range
        if (route != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(arrival(route.durationS, state.now), style = TType.caption.copy(fontWeight = FontWeight.Medium), color = c.text)
                Text(" Arrival", style = TType.caption, color = c.faint, modifier = Modifier.weight(1f))
                val range = state.fuel.rangeKm
                if (range != null) {
                    val left = (range - route.distanceM / 1000).coerceAtLeast(0)
                    Text("Range at arrival ", style = TType.caption, color = c.faint)
                    Text(if (state.mph) "${(left / 1.609).toInt()} mi" else "$left km", style = TType.caption.copy(fontWeight = FontWeight.Medium), color = c.text)
                } else if (route.hasTraffic()) {
                    Text("Some slow traffic", style = TType.caption, color = TRAFFIC_SLOW)
                }
            }
        } else Spacer(Modifier.height(10.dp))
        // start
        val ready = route != null
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(TileShape).background(if (ready) c.cta else c.cta.copy(alpha = 0.35f))
                .pressable { if (ready) actions.startRoute() },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Navigation, null, tint = c.onCta, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Start navigation", style = TType.button, color = c.onCta, maxLines = 1)
        }
        Text(
            "Voice guidance by $navName", style = TType.caption.copy(fontSize = 10.sp, letterSpacing = 1.sp), color = c.faint,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** One route choice: time, distance, and "Recommended ✓" or how it differs. */
@Composable
private fun RowScope.RouteTile(state: HomeUiState, route: RouteData, index: Int, selected: Boolean, count: Int, onClick: () -> Unit) {
    val c = navColors
    val (num, unit) = durationParts(route.durationS)
    val jam = route.hasTraffic()
    Column(
        Modifier.weight(1f).clip(TileShape).background(if (selected) c.tileSelected else c.tile)
            .then(if (selected) Modifier.border(1.dp, c.tileSelectedBorder, TileShape) else Modifier)
            .pressable(enabled = count > 1, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num, style = TType.display.copy(fontSize = if (count > 2) 24.sp else 28.sp), color = if (jam) TRAFFIC_SLOW else c.text, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Text(unit, style = TType.caption, color = c.sub, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(fmtDistance(route.distanceM, state.mph), style = TType.label, color = if (selected) c.text else c.sub, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                index == 0 -> "Recommended"
                route.via.isNotBlank() -> "via ${route.via}"
                else -> "Alternative"
            }
            Text(label, style = TType.caption.copy(fontSize = 11.sp), color = if (selected) c.text else c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (selected) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Check, null, tint = c.text, modifier = Modifier.size(14.dp)) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ dropped pin

/** Long-pressed spot: its address (or coordinates while that loads), how far it is, Directions / favourite / close. */
@Composable
fun PinCard(state: HomeUiState, pin: Place, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = navColors
    val fav = state.favourites.any { it.sameAs(pin) }
    val away = state.here?.let { h -> pin.latLon?.let { RouteMath.distanceM(h, it).toInt() } }
    Column(modifier.width(NAV_CARD_W).navPanel().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Rounded.LocationOn, TRAFFIC_JAM); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pin.name, style = TType.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(away?.let { fmtDistance(it, state.mph) + " away" }, pin.address.takeIf { it.isNotBlank() && it != pin.name }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = TType.caption, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NavIconButton(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "Remove favourite" else "Add to favourites",
                tint = if (fav) c.star else c.faint) { actions.toggleFavourite(pin) }
            NavIconButton(Icons.Rounded.Close, "Remove pin", onClick = actions::clearPin)
        }
        Spacer(Modifier.height(12.dp))
        // Pinning Home / Work from Settings: save the spot instead of asking for directions.
        val picking = state.picking
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(TileShape).background(c.cta)
                .pressable(onClick = { if (picking != null) actions.savePinAs(picking) else actions.pinDirections() }),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            when (picking) {
                null -> Icon(Icons.Rounded.Navigation, null, tint = c.onCta, modifier = Modifier.size(18.dp).rotate(45f))
                com.ahu.tlauncher.data.SavedSpot.HOME -> Icon(Icons.Rounded.Home, null, tint = c.onCta, modifier = Modifier.size(18.dp))
                com.ahu.tlauncher.data.SavedSpot.WORK -> Icon(Icons.Rounded.Work, null, tint = c.onCta, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (picking != null) "Set as ${picking.label}" else "Directions", style = TType.button, color = c.onCta, maxLines = 1)
        }
        // Any pin: offer it as Home / Work, but only for the ones not saved yet.
        val unset = if (picking != null) emptyList()
            else com.ahu.tlauncher.data.SavedSpot.entries.filter { state.settings.savedPlace(it) == null }
        if (unset.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unset.forEach { spot ->
                    Row(
                        Modifier.weight(1f).height(40.dp).clip(TileShape).background(c.tile).pressable(onClick = { actions.savePinAs(spot) }),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (spot == com.ahu.tlauncher.data.SavedSpot.HOME) Icons.Rounded.Home else Icons.Rounded.Work, null, tint = c.text, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Set as ${spot.label}", style = TType.button, color = c.text, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Top of the map while Home / Work is being pinned: what to do, and Cancel (back to Settings). */
@Composable
fun PickSpotBanner(spot: com.ahu.tlauncher.data.SavedSpot, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = navColors
    Row(modifier.width(NAV_CARD_W).navPanel().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge(if (spot == com.ahu.tlauncher.data.SavedSpot.HOME) Icons.Rounded.Home else Icons.Rounded.Work, c.cta); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Pin ${spot.label}", style = TType.title, color = c.text, maxLines = 1)
            Text("Long-press the exact spot on the map", style = TType.caption, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NavIconButton(Icons.Rounded.Close, "Cancel", onClick = onCancel)
    }
}

/** After the car starts: the trip that wasn't finished, with Continue and ✕ (forget it). */
@Composable
fun ResumeTripCard(state: HomeUiState, trip: com.ahu.tlauncher.system.SavedTrip, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = navColors
    val p = trip.destination
    val away = state.here?.let { h -> p.latLon?.let { RouteMath.distanceM(h, it).toInt() } }
    Column(modifier.width(NAV_CARD_W).navPanel().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Rounded.Navigation, c.good); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Continue to ${p.name}?", style = TType.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull("Trip not finished", away?.let { fmtDistance(it, state.mph) + " away" }).joinToString(" · ")
                Text(sub, style = TType.caption, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NavIconButton(Icons.Rounded.Close, "Forget this trip", onClick = actions::dismissTrip)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(TileShape).background(c.cta).pressable(onClick = actions::continueTrip),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Navigation, null, tint = c.onCta, modifier = Modifier.size(18.dp).rotate(45f)); Spacer(Modifier.width(8.dp))
            Text("Continue", style = TType.button, color = c.onCta, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------------------------------------ trip

/** During the trip: destination, time / distance left, arrival, End navigation and Change destination. */
@Composable
fun TripCard(state: HomeUiState, r: RouteState, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = navColors
    val left = r.remainingS ?: r.route?.durationS ?: 0
    val leftM = r.remainingM ?: r.route?.distanceM ?: 0
    Column(modifier.width(NAV_CARD_W - 40.dp).navPanel()) {
        Row(Modifier.padding(start = 16.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.destination.name, style = TType.button, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val status = when {
                leftM < 300 -> "Arriving"
                r.progressSeg == 0 && (state.gpsSpeedKmh ?: 0) < 5 -> "Ready to depart"
                r.route?.via?.isNotBlank() == true -> "via ${r.route.via}"
                else -> "On route"
            }
            Spacer(Modifier.width(8.dp))
            Text(status, style = TType.caption.copy(fontSize = 11.sp), color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            val (n, u) = durationParts(left)
            val jam = r.route?.hasTraffic() == true
            Stat(n, u, if (jam) TRAFFIC_SLOW else c.good, Modifier.weight(1f))
            val (dn, du) = distanceParts(leftM, state.mph)
            Stat(dn, du, c.text, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(arrival(left, state.now), style = TType.title, color = c.text, maxLines = 1)
                Text("Arrival", style = TType.caption.copy(fontSize = 11.sp), color = c.faint)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(46.dp).pressable(onClick = actions::endRoute), contentAlignment = Alignment.Center) {
                Text("End navigation", style = TType.label, color = c.danger, maxLines = 1)
            }
            Box(Modifier.size(1.dp, 22.dp).background(c.border))
            Row(Modifier.weight(1f).height(46.dp).pressable(onClick = actions::openNavApp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text("Change destination", style = TType.label, color = c.text, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Stat(value: String, unit: String, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = TType.display, color = color, maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Text(unit, style = TType.caption, color = navColors.sub, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
fun TrafficLegend(modifier: Modifier = Modifier) {
    val c = navColors
    Row(modifier.clip(CircleShape).background(c.panel).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(TRAFFIC_SLOW)); Spacer(Modifier.width(4.dp)); Text("Slow", style = TType.caption, color = c.sub)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(TRAFFIC_JAM)); Spacer(Modifier.width(4.dp)); Text("Heavy", style = TType.caption, color = c.sub)
    }
}

/**
 * Route overlays on the map pane. The route preview sits where search was (top, on the free side); the trip card
 * sits at the bottom; the pin card at the bottom. [start] is the side away from the map buttons.
 */
@Composable
fun RouteOverlays(state: HomeUiState, actions: HomeActions, modifier: Modifier, controlsOnLeft: Boolean = false) {
    val r = state.route
    val top = if (controlsOnLeft) Alignment.TopEnd else Alignment.TopStart
    val bottom = if (controlsOnLeft) Alignment.BottomEnd else Alignment.BottomStart
    Box(modifier) {
        val previewing = r?.takeIf { it.phase == RoutePhase.PREVIEW }
        val lastPreview = remember { arrayOfNulls<RouteState>(1) }
        if (previewing != null) lastPreview[0] = previewing
        AnimatedVisibility(previewing != null, enter = navEnter(), exit = navExit(),
            modifier = Modifier.align(top).padding(horizontal = 12.dp).padding(top = STATUS_H + 4.dp)) {
            lastPreview[0]?.let { RoutePreviewCard(state, it, actions) }
        }
        val active = r?.takeIf { it.phase == RoutePhase.ACTIVE }
        val lastActive = remember { arrayOfNulls<RouteState>(1) }
        if (active != null) lastActive[0] = active
        AnimatedVisibility(active != null, enter = navEnter(), exit = navExit(), modifier = Modifier.align(bottom).padding(12.dp).padding(bottom = 4.dp)) {
            lastActive[0]?.let { TripCard(state, it, actions) }
        }
        if (active?.route?.hasTraffic() == true)
            TrafficLegend(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
        val pin = state.pin?.takeIf { r == null }
        val lastPin = remember { arrayOfNulls<Place>(1) }
        if (pin != null) lastPin[0] = pin
        AnimatedVisibility(pin != null, enter = navEnter(), exit = navExit(), modifier = Modifier.align(bottom).padding(12.dp).padding(bottom = 4.dp)) {
            lastPin[0]?.let { PinCard(state, it, actions) }
        }
        val offer = state.tripOffer?.takeIf { r == null && pin == null && state.picking == null }
        val lastOffer = remember { arrayOfNulls<com.ahu.tlauncher.system.SavedTrip>(1) }
        if (offer != null) lastOffer[0] = offer
        AnimatedVisibility(offer != null, enter = navEnter(), exit = navExit(), modifier = Modifier.align(bottom).padding(12.dp).padding(bottom = 4.dp)) {
            lastOffer[0]?.let { ResumeTripCard(state, it, actions) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ turn cards

/** The shared look of the next-turn card: big manoeuvre icon, distance, instruction; a footer line below. */
@Composable
fun TurnCardFrame(
    icon: @Composable () -> Unit,
    distance: String?,
    instruction: String,
    footer: String?,
    footerRight: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = navColors
    Column(modifier.widthIn(min = 260.dp, max = NAV_CARD_W - 40.dp).navPanel().then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)) {
        Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f, fill = false)) {
                if (distance != null) Text(distance, style = TType.display.copy(fontSize = 30.sp, lineHeight = 34.sp), color = c.text, maxLines = 1)
                Text(instruction, style = TType.label, color = c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (footer != null || footerRight != null) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.border))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(footer.orEmpty(), style = TType.caption, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (footerRight != null) { Spacer(Modifier.width(10.dp)); Text(footerRight, style = TType.caption, color = c.faint, maxLines = 1) }
            }
        }
    }
}

/** Next-turn card for TLauncher's own guidance (free map). */
@Composable
fun FreeTurnCard(state: HomeUiState, r: RouteState, modifier: Modifier = Modifier) {
    val turn = r.turn ?: return
    val c = navColors
    val shown = turn.distanceM.let { if (it >= 1000) it else (it / 10) * 10 }
    val road = turn.step.road
    TurnCardFrame(
        icon = { Icon(TIcons.turn(turn.step.type, turn.step.modifier), null, tint = c.text, modifier = Modifier.size(40.dp)) },
        distance = fmtDistance(shown, state.mph),
        instruction = FreeNav.instruction(turn.step.copy(road = "")),
        footer = road.ifBlank { r.destination.name },
        footerRight = null,
        modifier = modifier,
    )
}
