package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** Launcher popup: always a dark panel above the dock, like the in-car app launcher. */
private val POP_BG = androidx.compose.ui.graphics.Color(0xF21B1C1E)
private val POP_FIELD = androidx.compose.ui.graphics.Color(0xFF2A2C30)
private val POP_FG = androidx.compose.ui.graphics.Color.White
private val POP_MUTED = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)

@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    pinned: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onAppInfo: (AppEntry) -> Unit = {},
    onUninstall: (AppEntry) -> Unit = {},
    initialMenu: AppEntry? = null,
    /** Minimal line icons instead of the apps' own (Settings → Home screen → App icons). */
    minimalIcons: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    // Long-press menu for one app: Pin / App info / Uninstall.
    var menuFor by remember { mutableStateOf(initialMenu) }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Box(modifier.clip(TShapes.surface).background(POP_BG)) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", style = TType.title, color = POP_FG)
            Spacer(Modifier.width(16.dp))
            Row(
                Modifier.weight(1f).height(40.dp).clip(TShapes.button).background(POP_FIELD).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = POP_MUTED, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", style = TType.body, color = POP_MUTED)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TType.body.copy(color = POP_FG), cursorBrush = SolidColor(T.colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text("Long-press for more options", style = TType.label, color = POP_MUTED)
            Spacer(Modifier.width(8.dp))
            IconButtonT(Icons.Rounded.Close, "Close", size = 40.dp, iconSize = 22.dp, tint = POP_FG, onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.key }) { app ->
                Column(
                    Modifier.clip(TShapes.surface).background(if (menuFor?.key == app.key) POP_FIELD else androidx.compose.ui.graphics.Color.Transparent)
                        .pressable(onLongClick = { menuFor = app }) { if (menuFor != null) menuFor = null else onLaunch(app) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        AppIcon(app, minimalIcons, POP_FG, 52.dp, TShapes.inner)
                        if (app.key in pinned) {
                            Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(POP_FG))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label, style = TType.label, color = POP_FG, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
    menuFor?.let { app ->
        val pinnedNow = app.key in pinned
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(TShapes.surface).background(POP_FIELD).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(app, minimalIcons, POP_FG, 36.dp, TShapes.inner)
            Text(app.label, style = TType.button, color = POP_FG, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            Spacer(Modifier.width(8.dp))
            MenuButton(if (pinnedNow) "Unpin from dock" else "Pin to dock") { onTogglePin(app); menuFor = null }
            MenuButton("App info") { onAppInfo(app); menuFor = null }
            if (app.removable) MenuButton("Uninstall", danger = true) { onUninstall(app); menuFor = null }
            IconButtonT(Icons.Rounded.Close, "Close menu", size = 36.dp, iconSize = 18.dp, tint = POP_MUTED) { menuFor = null }
        }
    }
    }
}

@Composable
private fun MenuButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(TShapes.button).background(if (danger) androidx.compose.ui.graphics.Color(0xFFE5484D) else androidx.compose.ui.graphics.Color(0xFF3A3D44))
            .pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(label, style = TType.button, color = androidx.compose.ui.graphics.Color.White) }
}
