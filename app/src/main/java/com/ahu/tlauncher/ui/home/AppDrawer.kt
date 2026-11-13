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

@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    pinned: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().background(T.colors.panel).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", style = TType.display, color = T.colors.text)
            Spacer(Modifier.width(16.dp))
            Row(
                Modifier.weight(1f).height(42.dp).clip(TShapes.inner).background(T.colors.card).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = T.colors.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", style = TType.body, color = T.colors.muted)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TType.body.copy(color = T.colors.text), cursorBrush = SolidColor(T.colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text("Long-press to pin to dock", style = TType.label, color = T.colors.muted)
            Spacer(Modifier.width(8.dp))
            IconButtonT(Icons.Rounded.Close, "Close", size = 40.dp, iconSize = 22.dp, onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.key }) { app ->
                Column(
                    Modifier.clip(TShapes.surface).pressable( onLongClick = { onTogglePin(app) }) { onLaunch(app) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Image(app.icon, null, modifier = Modifier.size(52.dp).clip(TShapes.inner))
                        if (app.key in pinned) {
                            Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(T.colors.text))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label, style = TType.label, color = T.colors.text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}
