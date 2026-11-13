package com.ahu.tlauncher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The standard "dipped beam / lights on" tell-tale: a lamp with downward-slanted rays. 24×24 grid. */
val LowBeamIcon: ImageVector by lazy {
    ImageVector.Builder("LowBeam", 24.dp, 24.dp, 24f, 24f).apply {
        // lamp: flat face on the left, rounded back on the right
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.4f, 5f)
            curveTo(18.6f, 5f, 21.5f, 8.2f, 21.5f, 12f)
            curveTo(21.5f, 15.8f, 18.6f, 19f, 12.4f, 19f)
            curveTo(11.6f, 19f, 11.2f, 18.6f, 11.2f, 17.8f)
            lineTo(11.2f, 6.2f)
            curveTo(11.2f, 5.4f, 11.6f, 5f, 12.4f, 5f)
            close()
        }
        // rays, slanting down to the road
        val rays = floatArrayOf(7f, 10.3f, 13.6f, 16.9f)
        for (y in rays) {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(9.3f, y); lineTo(2.8f, y + 2.2f)
            }
        }
    }.build()
}
