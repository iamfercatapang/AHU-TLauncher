package com.ahu.tlauncher.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Close
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
    DISPLAY("Display", Icons.Rounded.Tune),
    DOCK("Dock", Icons.Rounded.Dock),
    NAVIGATION("Navigation", Icons.Rounded.Map),
    OBD("OBD-II", Icons.Rounded.Bluetooth),
    DIAGNOSTICS("Diagnostics", Icons.Rounded.Build),
    ANDROID_AUTO("Android Auto", Icons.Rounded.PhoneAndroid),
    PERMISSIONS("Permissions", Icons.Rounded.Security),
    ABOUT("About", Icons.Rounded.Info),
}

@Composable
fun SettingsScreen(state: HomeUiState, actions: HomeActions, onClose: () -> Unit, modifier: Modifier = Modifier, initial: Section = Section.DISPLAY) {
    var section by rememberSaveable(initial) { mutableStateOf(initial) }
    Row(modifier.clip(TShapes.surface).background(T.colors.panel)) {
        Column(Modifier.width(168.dp).fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Section.entries.forEach { s ->
                val sel = s == section
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clip(TShapes.card)
                        .background(if (sel) T.colors.card else androidx.compose.ui.graphics.Color.Transparent)
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
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (section) {
                    Section.DISPLAY -> DisplaySection(state, actions)
                    Section.DOCK -> DockSection(state, actions)
                    Section.NAVIGATION -> NavigationSection(state, actions)
                    Section.OBD -> ObdSection(state, actions)
                    Section.DIAGNOSTICS -> DiagnosticsSection(state, actions)
                    Section.ANDROID_AUTO -> AndroidAutoSection(state, actions)
                    Section.PERMISSIONS -> PermissionsSection(state, actions)
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

@Composable
private fun DisplaySection(state: HomeUiState, a: HomeActions) {
    val s = state.settings
    SettingRow("Appearance", "Auto switches at sunrise and sunset") {
        Segmented(listOf(ThemeMode.AUTO to "Auto", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light"), s.themeMode, a::setTheme)
    }
    SettingRow("Car panel", "Side of the car next to the map") {
        Segmented(listOf(com.ahu.tlauncher.data.CarSide.LEFT to "Left", com.ahu.tlauncher.data.CarSide.RIGHT to "Right"), s.carSide, a::setCarSide)
    }
    Divider()
    HeadlightSetup(state, a)
    Divider()
    SettingRow("Speed") { Segmented(listOf(SpeedUnit.KMH to "km/h", SpeedUnit.MPH to "mph"), s.speedUnit, a::setSpeedUnit) }
    SettingRow("Temperature") { Segmented(listOf(TempUnit.C to "°C", TempUnit.F to "°F"), s.tempUnit, a::setTempUnit) }
    Divider()
    SmallTitle("Car images")
    Text(
        "Choose which views appear on the dashboard; tap the car there to switch between them. Plain white photo backgrounds are removed automatically.",
        style = TType.label, color = T.colors.muted,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CarView.entries.forEach { v ->
            val on = v in s.carViews
            // The last ticked view can't be turned off.
            CarSlot(v, s.carImages[v], a, Modifier.weight(1f), shown = on, canToggle = !on || s.carViews.size > 1)
        }
    }
}

@Composable
private fun CarSlot(view: CarView, customUri: String?, a: HomeActions, modifier: Modifier, shown: Boolean = true, canToggle: Boolean = true) {
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
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(view.label, style = TType.button, color = T.colors.text)
                Text(if (customUri != null) "Custom photo" else if (src == null) "Optional" else "Built-in", style = TType.label, color = T.colors.muted)
            }
            com.ahu.tlauncher.ui.components.TSwitch(
                shown, { if (canToggle) a.setCarViewShown(view, it) },
                Modifier.alpha(if (canToggle) 1f else 0.4f),
            )
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
private fun DockSection(state: HomeUiState, a: HomeActions) {
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
    SmallTitle("Saved places")
    AddressField("Home", s.homeAddress, a::setHome)
    AddressField("Work", s.workAddress, a::setWork)
    Text(
        "While Google Maps or Waze is navigating, the next turn appears on the dashboard map. Requires notification access.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun AddressField(label: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(text) { if (text != value) { kotlinx.coroutines.delay(800); onSave(text) } }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TType.body, color = T.colors.text, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(42.dp).clip(TShapes.inner).background(T.colors.card).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("Address or place name", style = TType.body, color = T.colors.muted)
            BasicTextField(
                text, { text = it }, singleLine = true, textStyle = TType.body.copy(color = T.colors.text),
                cursorBrush = SolidColor(T.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ObdSection(state: HomeUiState, a: HomeActions) {
    val s = state.settings
    SettingRow("Data source", "Without a connected adapter, engine data is hidden and speed comes from GPS. Demo shows simulated data.") {
        Segmented(listOf(ObdSourceType.DEMO to "Demo", ObdSourceType.BLUETOOTH to "Bluetooth"), s.obdSource, a::setObdSource)
    }
    SettingRow("Status", state.obd.message) { ObdBadge(state.obd.status) }
    if (s.obdSource == ObdSourceType.BLUETOOTH) {
        Divider()
        SmallTitle("Paired adapters")
        Text("Pair your ELM327 in Android Bluetooth settings first (PIN is usually 1234 or 0000).", style = TType.label, color = T.colors.muted)
        var devices by remember { mutableStateOf(a.bondedDevices()) }
        LaunchedEffect(Unit) { devices = a.bondedDevices() }
        if (devices.isEmpty()) {
            Text("No paired devices found.", style = TType.body, color = T.colors.secondary, modifier = Modifier.padding(vertical = 8.dp))
        }
        devices.forEach { (addr, name) ->
            val sel = addr == s.obdDeviceAddress
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(TShapes.card)
                    .background(if (sel) T.colors.cardPressed else T.colors.card)
                    .pressable(pressedScale = 0.98f) { a.pickObdDevice(addr, name) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bluetooth, null, tint = if (sel) T.colors.text else T.colors.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = TType.body, color = T.colors.text)
                    Text(addr, style = TType.caption, color = T.colors.muted)
                }
                if (sel) Text("Selected", style = TType.button, color = T.colors.text)
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Refresh list", onClick = { devices = a.bondedDevices() })
            PillButton("Bluetooth settings", onClick = a::openAndroidSettings)
        }
    }
    Divider()
    SmallTitle("Fuel")
    SettingRow("Tank size", "For the range estimate. 2018 Avanza: 43 L") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("−", height = 34.dp, onClick = { a.setTankL(s.tankL - 1) })
            Text("${s.tankL} L", style = TType.button, color = T.colors.text, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            PillButton("+", height = 34.dp, onClick = { a.setTankL(s.tankL + 1) })
        }
    }
    SettingRow("Engine", "Only used for consumption if the car has no air-flow sensor") {
        Segmented(listOf(1329 to "1.3 L", 1496 to "1.5 L"), s.engineCc, a::setEngineCc)
    }
    Text(
        "Shown: speed, engine RPM, coolant temperature, battery voltage (standard OBD-II PIDs supported by petrol cars from ~2008). Trouble codes and more live data are under Diagnostics.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun PermissionsSection(state: HomeUiState, a: HomeActions) {
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
        "Map data © OpenStreetMap contributors, tiles by OpenFreeMap / OpenMapTiles. Inter typeface © The Inter Project Authors (SIL OFL 1.1). " +
            "Design language inspired by minimalist in-car displays; not affiliated with any car maker.",
        style = TType.label, color = T.colors.muted, modifier = Modifier.padding(vertical = 10.dp),
    )
    PillButton("Android settings", onClick = a::openAndroidSettings)
}
