package com.ahu.tlauncher.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahu.tlauncher.notify.NavCardState
import com.ahu.tlauncher.notify.NavParser
import com.ahu.tlauncher.ui.components.IconButtonT
import com.ahu.tlauncher.ui.components.TCard
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun cartoSource(dark: Boolean) = XYTileSource(
    if (dark) "CartoDark" else "CartoLight", 1, 19, 256, ".png",
    (if (dark) "dark_all" else "light_all").let { style ->
        arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/$style/" }.toTypedArray()
    },
    "© OpenStreetMap contributors © CARTO",
)

private class MapHolder(val map: MapView, val me: MyLocationNewOverlay?)

@Composable
fun MapPane(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val dark = T.colors.isDark
    val preview = LocalInspectionMode.current
    val holder = if (preview) null else rememberMap(state.locationPermission)

    Box(modifier.background(T.colors.appBg)) {
        if (holder == null) {
            MapPlaceholder(Modifier.fillMaxSize())
        } else {
            androidx.compose.runtime.key(holder) { AndroidView(factory = { holder.map }, modifier = Modifier.fillMaxSize(), update = { m ->
                val name = if (dark) "CartoDark" else "CartoLight"
                if (m.tileProvider.tileSource.name() != name) m.setTileSource(cartoSource(dark))
                if (holder.me == null && state.locationLat != null && state.locationLon != null && m.tag == null) {
                    m.controller.setCenter(GeoPoint(state.locationLat, state.locationLon)); m.tag = "centered"
                }
            }) }
        }

        // top-left: live turn-by-turn card while Google Maps / Waze is navigating
        state.nav?.let { nav ->
            Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = STATUS_H + 4.dp)) { NavCard(nav, onClick = actions::openNavCard) }
        }

        // right edge: map controls
        TCard(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            color = T.colors.frost, shape = TShapes.button,
        ) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButtonT(Icons.Rounded.Add, "Zoom in", size = 40.dp, iconSize = 22.dp) { holder?.map?.controller?.zoomIn() }
                IconButtonT(Icons.Rounded.Remove, "Zoom out", size = 40.dp, iconSize = 22.dp) { holder?.map?.controller?.zoomOut() }
                IconButtonT(Icons.Rounded.MyLocation, "Recenter", size = 40.dp, iconSize = 20.dp) {
                    if (!state.locationPermission) actions.requestLocation()
                    holder?.me?.let { it.enableFollowLocation(); it.myLocation?.let { p -> holder.map.controller.animateTo(p) } }
                }
            }
        }

        Text(
            "© OpenStreetMap contributors © CARTO", style = TType.caption, color = T.colors.muted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}

@Composable
private fun rememberMap(hasLocation: Boolean): MapHolder {
    val ctx = LocalContext.current
    val holder = remember(hasLocation) {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = ctx.cacheDir.resolve("osmdroid")
            osmdroidTileCache = ctx.cacheDir.resolve("osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 200L * 1024 * 1024
        }
        val map = MapView(ctx).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(14.5995, 120.9842))
        }
        val me = if (hasLocation) MyLocationNewOverlay(GpsMyLocationProvider(ctx), map).apply {
            setDirectionIcon(arrowBitmap(ctx.resources.displayMetrics.density))
            setPersonIcon(dotBitmap(ctx.resources.displayMetrics.density))
            setPersonAnchor(0.5f, 0.5f); setDirectionAnchor(0.5f, 0.5f)
            enableMyLocation(); enableFollowLocation()
            runOnFirstFix { map.post { myLocation?.let { map.controller.animateTo(it) } } }
            map.overlays.add(this)
        } else null
        MapHolder(map, me)
    }
    DisposableEffect(holder) {
        holder.map.onResume()
        onDispose { holder.me?.disableMyLocation(); holder.map.onPause(); holder.map.onDetach() }
    }
    return holder
}

private fun dotBitmap(d: Float): Bitmap {
    val s = (24 * d).toInt()
    return Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(s / 2f, s / 2f, s / 2.2f, p)
        p.color = 0xFF3E6AE1.toInt(); c.drawCircle(s / 2f, s / 2f, s / 3.2f, p)
    }
}

private fun arrowBitmap(d: Float): Bitmap {
    val s = (34 * d).toInt()
    return Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(s / 2f, s * 0.08f); lineTo(s * 0.86f, s * 0.9f); lineTo(s / 2f, s * 0.7f); lineTo(s * 0.14f, s * 0.9f); close()
        }
        p.color = 0xFFFFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, p)
        p.color = 0xFF3E6AE1.toInt(); p.style = Paint.Style.FILL; c.drawPath(path, p)
    }
}

/** Stylised street grid used in previews/screenshots (no network). */
@Composable
private fun MapPlaceholder(modifier: Modifier) {
    val road = T.colors.text.copy(alpha = 0.10f)
    val major = T.colors.text.copy(alpha = 0.18f)
    val accent = T.colors.accent
    ComposeCanvas(modifier) {
        val step = 46.dp.toPx()
        var x = -step / 2; while (x < size.width) { drawLine(road, Offset(x, 0f), Offset(x + 60f, size.height), 2f); x += step }
        var y = step / 3; while (y < size.height) { drawLine(road, Offset(0f, y), Offset(size.width, y - 40f), 2f); y += step }
        drawLine(major, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.38f), 10f)
        drawLine(major, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.55f, size.height), 8f)
        drawCircle(androidx.compose.ui.graphics.Color.White, 11.dp.toPx(), Offset(size.width * 0.46f, size.height * 0.5f))
        drawCircle(accent, 8.dp.toPx(), Offset(size.width * 0.46f, size.height * 0.5f))
    }
}

@Composable
fun NavCard(nav: NavCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val i = nav.info
    Column(
        modifier.widthIn(min = 280.dp, max = 340.dp).clip(TShapes.surface).background(T.colors.frost)
            .pressable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val icon = nav.icon
                if (icon != null) {
                    Image(
                        icon.asImageBitmap(), null, modifier = Modifier.size(48.dp),
                        colorFilter = if (i.packageName == NavParser.MAPS) ColorFilter.tint(T.colors.text) else null,
                    )
                } else {
                    Icon(Icons.Rounded.Navigation, null, tint = T.colors.text, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                if (i.distance != null) Text(i.distance, style = TType.display, color = T.colors.text, maxLines = 1)
                Text(i.instruction, style = TType.button, color = T.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (i.detail != null) Text(i.detail, style = TType.label, color = T.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.height(1.dp).widthIn(min = 280.dp, max = 340.dp).background(T.colors.divider))
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(i.eta ?: "Navigating", style = TType.label, color = T.colors.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(i.appLabel, style = TType.label, color = T.colors.muted)
        }
    }
}
