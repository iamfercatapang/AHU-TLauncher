package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.ahu.tlauncher.launcher.AppEntry

/**
 * An app's icon in the dock or the drawer. [minimal]: its line icon tinted [tint] (Arcticons, or its own icon in
 * one colour), or a grey version when neither exists; otherwise the app's own icon, clipped to [shape].
 */
@Composable
internal fun AppIcon(
    app: AppEntry,
    minimal: Boolean,
    tint: Color,
    size: Dp,
    shape: Shape,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    when {
        !minimal -> Image(app.icon, contentDescription, modifier.size(size).clip(shape))
        app.minimalIsMask -> Image(app.minimal, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
        else -> Image(app.minimal, contentDescription, modifier.size(size).clip(shape), alpha = 0.85f)
    }
}
