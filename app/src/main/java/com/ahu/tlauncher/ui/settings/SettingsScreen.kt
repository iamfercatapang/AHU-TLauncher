package com.ahu.tlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.ahu.tlauncher.ui.components.avoidKeyboard
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.outlined.LocationOn as LocationOnOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.BuildConfig
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.ahu.tlauncher.data.CarView
import com.ahu.tlauncher.data.NavApp
import com.ahu.tlauncher.ui.home.CarImages
import com.ahu.tlauncher.ui.home.CarSource
import com.ahu.tlauncher.data.ObdSourceType
import com.ahu.tlauncher.data.SpeedUnit
import com.ahu.tlauncher.data.TempUnit
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.Segmented
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.home.ObdBadge
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import com.ahu.tlauncher.ui.theme.ThemeMode

enum class Section(val label: String, val icon: ImageVector) {
    DISPLAY("Display", com.ahu.tlauncher.ui.components.TIcons.Display),
    HOME("Home screen", com.ahu.tlauncher.ui.components.TIcons.DockBar),
    NAVIGATION("Navigation", com.ahu.tlauncher.ui.components.TIcons.Navigate),
    CAR("Car", com.ahu.tlauncher.ui.components.TIcons.Wrench),
    CONNECTIONS("Connections", com.ahu.tlauncher.ui.components.TIcons.PhoneLink),
    ABOUT("About", com.ahu.tlauncher.ui.components.TIcons.Info),
}

@Composable
fun SettingsScreen(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier, initial: Section = Section.DISPLAY) {
    var section by rememberSaveable(initial) { mutableStateOf(initial) }
    // Prototype settings: navigation list on its own surface, content on a darker sheet.
    Row(modifier.clip(TShapes.surface).background(T.colors.sheet)) {
        Column(Modifier.width(168.dp).fillMaxHeight().background(if (T.colors.isDark) T.colors.card else T.colors.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Section.entries.forEach { s ->
                val sel = s == section
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clip(TShapes.card)
                        .background(if (sel) (if (T.colors.isDark) androidx.compose.ui.graphics.Color(0xFF6E6E6E).copy(alpha = 0.45f) else T.colors.card) else androidx.compose.ui.graphics.Color.Transparent)
                        .pressable(pressedScale = 0.97f) { section = s }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.icon, null, tint = if (sel) T.colors.text else T.colors.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(s.label, style = TType.button, color = if (sel) T.colors.text else T.colors.muted)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 22.dp, end = 12.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.label, style = TType.title, color = T.colors.text)
                Spacer(Modifier.weight(1f))
                IconButtonT(Icons.Rounded.Close, "Close", size = 40.dp, iconSize = 22.dp, onClick = onClose)
            }
            // The keyboard (Home / Work addresses) shrinks the list instead of covering it; the focused field stays in view.
            Column(
                Modifier.weight(1f).then(Modifier.avoidKeyboard()).verticalScroll(rememberScrollState()).padding(end = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (section) {
                    Section.DISPLAY -> DisplaySection(state, actions)
                    Section.HOME -> HomeScreenSection(state, actions)
                    Section.NAVIGATION -> NavigationSection(state, actions)
                    Section.CAR -> CarSection(state, actions)
                    Section.CONNECTIONS -> ConnectionsSection(state, actions)
                    Section.ABOUT -> AboutSection(state, actions)
                }
            }
        }
    }
}

@Composable
internal fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = TType.body, color = T.colors.text)
            if (subtitle != null) Text(subtitle, style = TType.label, color = T.colors.muted)
        }
        trailing()
    }
}

@Composable
internal fun SmallTitle(text: String) {
    Text(text, style = TType.title, color = T.colors.text, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
}

@Composable
internal fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(T.colors.divider)) }

/** Explains the Performance setting and what Auto picked for this head unit. */
private fun performanceNote(state: HomeUiState): String {
    val spec = state.deviceSpec?.label()?.let { " ($it)" } ?: ""
    val now = if (state.lowPerf) "Low-performance mode is on$spec" else "Full animations$spec"
    return "Low: fewer animations, slower engine readouts and a flat map, for slow head units. $now."
}

@Composable
private fun DisplaySection(state: HomeUiState, a: HomeActions) {
    val s = state.settings
    SettingRow("Appearance", "Auto switches at sunrise and sunset") {
        Segmented(listOf(ThemeMode.AUTO to "Auto", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light"), s.themeMode, a::setTheme)
    }
    Divider()
    SettingRow("Speed") { Segmented(listOf(SpeedUnit.KMH to "km/h", SpeedUnit.MPH to "mph"), s.speedUnit, a::setSpeedUnit) }
    SettingRow("Temperature") { Segmented(listOf(TempUnit.C to "°C", TempUnit.F to "°F"), s.tempUnit, a::setTempUnit) }
    Divider()
    SettingRow("Performance", performanceNote(state)) {
        Segmented(
            listOf(com.ahu.tlauncher.data.PerfMode.AUTO to "Auto", com.ahu.tlauncher.data.PerfMode.ON to "Low", com.ahu.tlauncher.data.PerfMode.OFF to "Full"),
            s.perfMode, a::setPerfMode,
        )
    }
    Divider()
    SmallTitle("Car")
    val ctx = LocalContext.current
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    val can3d = preview || remember(s.car3d) { com.ahu.tlauncher.ui.home.Car3d.available(ctx) }
    val crashed = !preview && remember(s.car3d) { com.ahu.tlauncher.ui.home.Car3d.crashed(ctx) }
    SettingRow(
        "Car on the dashboard",
        when {
            can3d -> "Seen from above in split view, turning to the 3/4 view in full screen. In full screen, drag the car to look around it"
            crashed -> "The 3D model stopped the app last time, so the photos are shown. Choose 3D to try again"
            else -> "This head unit's graphics can't draw the 3D model (needs OpenGL ES 3), so the photos are shown"
        },
    ) {
        Segmented(listOf(true to "3D", false to "Photos"), s.car3d, a::setCar3d)
    }
    if (s.car3d && can3d) {
        SettingRow("Paint colour", s.carPaint.label) {}
        PaintPicker(s.carPaint, a::setCarPaint)
        Text(
            "3D model: \"2015 Toyota Avanza G 1.3\" by Raysh on Sketchfab, wheels \"Volk Racing CE28 Rims\" by MrDogeSuper and tyres from \"Car wheels and tire\" by Márcio Meireles on Sketchfab, all licensed CC BY 4.0, simplified and recoloured for this app.",
            style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 10.dp),
        )
    } else {
        Text(
            "Split view shows the front of the car; full screen turns it to the 3/4 view. Plain white photo backgrounds are removed automatically.",
            style = TType.label, color = T.colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarSlot(CarView.FRONT, s.carImages[CarView.FRONT], a, Modifier.weight(1f), title = "Front · split view")
            CarSlot(CarView.SIDE, s.carImages[CarView.SIDE], a, Modifier.weight(1f), title = "3/4 · full screen")
        }
    }
}

/** Paint presets as swatches; the chosen one has a ring. */
@Composable
private fun PaintPicker(selected: com.ahu.tlauncher.data.CarPaint, onSelect: (com.ahu.tlauncher.data.CarPaint) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        com.ahu.tlauncher.data.CarPaint.entries.forEach { p ->
            val sel = p == selected
            Box(
                Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .then(if (sel) Modifier.border(2.dp, T.colors.text, androidx.compose.foundation.shape.CircleShape) else Modifier)
                    .pressable(pressedScale = 0.92f) { onSelect(p) }.padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF000000 or p.rgb))
                        .border(1.dp, T.colors.divider, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}

@Composable
private fun CarSlot(view: CarView, customUri: String?, a: HomeActions, modifier: Modifier, title: String = view.label) {
    val ctx = LocalContext.current
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    val src = remember(customUri) {
        when {
            customUri != null -> CarSource.Custom(customUri)
            preview -> CarSource.Bundled(when (view) { CarView.SIDE -> com.ahu.tlauncher.R.drawable.car_side; CarView.FRONT -> com.ahu.tlauncher.R.drawable.car_front; CarView.REAR -> com.ahu.tlauncher.R.drawable.car_rear })
            else -> CarImages.source(ctx, view, emptyMap())
        }
    }
    Column(modifier.clip(TShapes.surface).background(T.colors.card).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            when {
                src == null -> Text("Not set", style = TType.label, color = T.colors.muted)
                preview && src is CarSource.Bundled -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(src.resId), null, Modifier.fillMaxSize())
                src is CarSource.Illustration -> androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.ahu.tlauncher.R.drawable.car_mpv), null, Modifier.fillMaxSize(),
                )
                else -> {
                    var bmp by androidx.compose.runtime.remember(src) { androidx.compose.runtime.mutableStateOf<ImageBitmap?>(null) }
                    androidx.compose.runtime.LaunchedEffect(src) { bmp = CarImages.load(ctx, src) }
                    bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(title, style = TType.button, color = T.colors.text)
            Text(if (customUri != null) "Custom photo" else if (src == null) "Optional" else "Built-in", style = TType.label, color = T.colors.muted)
        }
        Spacer(Modifier.height(8.dp))
        PillButton("Change", height = 34.dp, modifier = Modifier.fillMaxWidth(), onClick = { a.pickCarImage(view) })
        if (customUri != null) {
            Spacer(Modifier.height(6.dp))
            PillButton("Reset", height = 34.dp, modifier = Modifier.fillMaxWidth(), onClick = { a.resetCarImage(view) })
        }
    }
}

@Composable
private fun HomeScreenSection(state: HomeUiState, a: HomeActions) {
    SettingRow("Car panel", "Side of the car next to the map") {
        Segmented(listOf(com.ahu.tlauncher.data.CarSide.LEFT to "Left", com.ahu.tlauncher.data.CarSide.RIGHT to "Right"), state.settings.carSide, a::setCarSide)
    }
    Divider()
    SmallTitle("Dock apps")
    val pinned = state.settings.pinnedApps.mapNotNull { k -> state.apps.firstOrNull { it.key == k } }
    Text("Long-press any app in the app drawer to pin or unpin it. Up to 6 apps are shown.", style = TType.label, color = T.colors.muted)
    Spacer(Modifier.height(8.dp))
    if (pinned.isEmpty()) Text("No apps pinned yet.", style = TType.body, color = T.colors.secondary)
    pinned.forEachIndexed { i, app ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(app.icon, null, Modifier.size(36.dp).clip(TShapes.card))
            Spacer(Modifier.width(12.dp))
            Text(app.label, style = TType.body, color = T.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButtonT(Icons.Rounded.Close, "Unpin", size = 36.dp, iconSize = 18.dp, tint = T.colors.secondary) { a.togglePin(app) }
            PillButton("↑", height = 32.dp, onClick = { a.movePin(app.key, -1) }, modifier = Modifier.padding(horizontal = 2.dp).alphaIf(i > 0))
            PillButton("↓", height = 32.dp, onClick = { a.movePin(app.key, 1) }, modifier = Modifier.alphaIf(i < pinned.size - 1))
        }
    }
}

private fun Modifier.alphaIf(visible: Boolean) = this.alpha(if (visible) 1f else 0.25f)

@Composable
private fun NavigationSection(state: HomeUiState, a: HomeActions) {
    val s = state.settings
    SettingRow("Navigation app", "Used by Navigate, Home and Work") {
        Segmented(listOf(NavApp.MAPS to "Maps", NavApp.WAZE to "Waze", NavApp.ASK to "Ask"), s.navApp, a::setNavApp)
    }
    Divider()
    SettingRow(
        "Map",
        when {
            s.mapEngine == com.ahu.tlauncher.data.MapEngine.OPENFREEMAP -> "OpenFreeMap (free, no traffic)"
            state.googleMapsAvailable -> "Google Maps with live traffic"
            !BuildConfig.HAS_MAPS_KEY -> "Google Maps isn't set up in this build (no API key), so OpenFreeMap is shown"
            else -> "Google Play services is missing on this head unit, so OpenFreeMap is shown"
        },
    ) {
        Segmented(listOf(com.ahu.tlauncher.data.MapEngine.GOOGLE to "Google", com.ahu.tlauncher.data.MapEngine.OPENFREEMAP to "Free"), s.mapEngine, a::setMapEngine)
    }
    if (s.mapEngine == com.ahu.tlauncher.data.MapEngine.GOOGLE && state.googleMapsAvailable) {
        SettingRow("Live traffic", "Traffic colours on the Google map") { com.ahu.tlauncher.ui.components.TSwitch(s.mapTraffic, a::setMapTraffic) }
    }
    Divider()
    SmallTitle("Saved places")
    AddressField("Home", s.homeAddress, s.homePos != null, a::setHome) { a.pickOnMap(com.ahu.tlauncher.data.SavedSpot.HOME) }
    AddressField("Work", s.workAddress, s.workPos != null, a::setWork) { a.pickOnMap(com.ahu.tlauncher.data.SavedSpot.WORK) }
    Text(
        "Type an address, or tap the pin and long-press the exact spot on the map. A pinned spot is used for directions; typing a new address replaces it.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
    )
    Divider()
    SettingRow("Offer to continue the trip", "If the car was switched off before arriving, the map asks whether to continue when it starts again") {
        com.ahu.tlauncher.ui.components.TSwitch(s.offerTrip, a::setOfferTrip)
    }
    Divider()
    OfflineMapsSettings(state, a)
    Text(
        "While Google Maps or Waze is navigating, the next turn appears on the dashboard map. Requires notification access.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 10.dp),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddressField(label: String, value: String, pinned: Boolean, onSave: (String) -> Unit, onPickOnMap: () -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(text) { if (text != value) { kotlinx.coroutines.delay(800); onSave(text) } }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // Once the keyboard is up, scroll this row into the space left above it.
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboardUp = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(focused, keyboardUp) { if (focused && keyboardUp) { kotlinx.coroutines.delay(150); bring.bringIntoView() } }
    Row(
        Modifier.fillMaxWidth().bringIntoViewRequester(bring).onFocusChanged { focused = it.hasFocus }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TType.body, color = T.colors.text, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(42.dp).clip(TShapes.inner).background(T.colors.card).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("Address or place name", style = TType.body, color = T.colors.muted)
            BasicTextField(
                text, { text = it }, singleLine = true, textStyle = TType.body.copy(color = T.colors.text),
                cursorBrush = SolidColor(T.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text); keyboard?.hide(); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Pin on map; filled with the accent colour while a pinned spot is saved.
        IconButtonT(
            if (pinned) Icons.Rounded.LocationOn else Icons.Outlined.LocationOnOutline,
            if (pinned) "$label is pinned on the map: pin again" else "Pin $label on the map",
            size = 42.dp, iconSize = 20.dp, tint = if (pinned) T.colors.accent else T.colors.secondary,
        ) { keyboard?.hide(); focus.clearFocus(); onPickOnMap() }
    }
}

@Composable
private fun ConnectionsSection(state: HomeUiState, a: HomeActions) {
    SmallTitle("Android Auto")
    AndroidAutoSettings(state, a)
    Divider()
    SmallTitle("Permissions")
    PermissionsSettings(state, a)
}

@Composable
private fun PermissionsSettings(state: HomeUiState, a: HomeActions) {
    SettingRow("Default home app", if (state.isDefaultLauncher) "This launcher opens when you press Home" else "Not set — press Home and choose TLauncher → Always") {
        if (!state.isDefaultLauncher) PillButton("Set", kind = com.ahu.tlauncher.ui.components.ButtonKind.PRIMARY, onClick = a::setDefaultLauncher) else Text("On", style = TType.button, color = T.colors.muted)
    }
    Divider()
    SettingRow("Notification access", "Needed for the music card and Maps/Waze turn-by-turn") {
        if (!state.notificationAccess) PillButton("Allow", kind = com.ahu.tlauncher.ui.components.ButtonKind.PRIMARY, onClick = a::requestNotificationAccess) else Text("On", style = TType.button, color = T.colors.muted)
    }
    Divider()
    SettingRow("Location", "Map position and automatic day/night") {
        if (!state.locationPermission) PillButton("Allow", onClick = a::requestLocation) else Text("On", style = TType.button, color = T.colors.muted)
    }
    Divider()
    SettingRow("Return to dashboard", "Come back to the dashboard after Start opens Google Maps, and when the car starts (Android calls this \"Display over other apps\")") {
        if (!state.returnPermission) PillButton("Allow", onClick = a::requestReturnPermission) else Text("On", style = TType.button, color = T.colors.muted)
    }
    Divider()
    SettingRow("Phone calls", "Answer and end Bluetooth calls from the dashboard") {
        if (!state.callPermission) PillButton("Allow", onClick = a::requestCallPermission) else Text("On", style = TType.button, color = T.colors.muted)
    }
    Text(
        "Calls are read from the head unit's phone app notification when it has Answer / Hang up buttons, otherwise from Android's call system. " +
            "Last call notification came from: ${state.callSource ?: "none yet"}.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun AboutSection(state: HomeUiState, a: HomeActions) {
    val u = state.update
    val status = when (u) {
        is com.ahu.tlauncher.update.UpdateState.Checking -> "Checking for updates…"
        is com.ahu.tlauncher.update.UpdateState.UpToDate -> "You have the latest version"
        is com.ahu.tlauncher.update.UpdateState.Available -> "Version ${u.version.versionName} is available"
        is com.ahu.tlauncher.update.UpdateState.Downloading -> "Downloading ${(u.progress * 100).toInt()}%"
        is com.ahu.tlauncher.update.UpdateState.Installing -> "Installing…"
        is com.ahu.tlauncher.update.UpdateState.Failed -> u.message
        else -> "Checks GitHub for new versions every 6 hours"
    }
    SettingRow("TLauncher ${BuildConfig.VERSION_NAME}", status) {
        if (u is com.ahu.tlauncher.update.UpdateState.Available) PillButton("Update", kind = com.ahu.tlauncher.ui.components.ButtonKind.PRIMARY, onClick = a::startUpdate)
        else PillButton("Check for updates", onClick = a::checkForUpdates)
    }
    Divider()
    Text(
        "Map data © OpenStreetMap contributors, tiles by OpenFreeMap / OpenMapTiles. Free-map search by Photon (komoot), routes by OSRM on FOSSGIS servers. Inter typeface © The Inter Project Authors (SIL OFL 1.1). " +
            "3D car: \"2015 Toyota Avanza G 1.3\" by Raysh (Sketchfab), CC BY 4.0, simplified and recoloured; wheels \"Volk Racing CE28 Rims\" by MrDogeSuper (Sketchfab), CC BY 4.0, recoloured; tyres from \"Car wheels and tire\" by Márcio Meireles (Sketchfab), CC BY 4.0, resized; rendered with Filament. " +
            "Design language inspired by minimalist in-car displays; not affiliated with any car maker.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(vertical = 10.dp),
    )
}
