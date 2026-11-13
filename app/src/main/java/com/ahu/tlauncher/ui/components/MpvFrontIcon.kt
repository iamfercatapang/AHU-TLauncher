package com.ahu.tlauncher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Front view of a tall compact 7-seat MPV (Avanza-like proportions, no badge): big windshield,
 * slim swept headlights, wide lower grille, mirrors and tyres. 24×24 grid, filled like Material icons.
 */
val MpvFrontIcon: ImageVector by lazy {
    ImageVector.Builder("MpvFront", 24.dp, 24.dp, 24f, 24f).apply {
        // Body + cabin outline with cut-outs for the windshield, headlights and grille.
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(7.4f, 3.6f); lineTo(16.6f, 3.6f)
            quadTo(17.4f, 3.6f, 17.7f, 4.3f)
            lineTo(19.5f, 9.6f)
            quadTo(21f, 10.2f, 21f, 11.6f)
            lineTo(21f, 17.2f)
            quadTo(21f, 18.2f, 20f, 18.2f)
            lineTo(20f, 19.8f); quadTo(20f, 20.4f, 19.4f, 20.4f)
            lineTo(17.6f, 20.4f); quadTo(17f, 20.4f, 17f, 19.8f)
            lineTo(17f, 18.2f); lineTo(7f, 18.2f)
            lineTo(7f, 19.8f); quadTo(7f, 20.4f, 6.4f, 20.4f)
            lineTo(4.6f, 20.4f); quadTo(4f, 20.4f, 4f, 19.8f)
            lineTo(4f, 18.2f)
            quadTo(3f, 18.2f, 3f, 17.2f)
            lineTo(3f, 11.6f)
            quadTo(3f, 10.2f, 4.5f, 9.6f)
            lineTo(6.3f, 4.3f)
            quadTo(6.6f, 3.6f, 7.4f, 3.6f)
            close()
            // windshield
            moveTo(7.9f, 5.2f); lineTo(16.1f, 5.2f); lineTo(17.6f, 9.4f); lineTo(6.4f, 9.4f); close()
            // headlights, swept towards the grille
            moveTo(4.5f, 11.6f); lineTo(9f, 12.5f); lineTo(8.5f, 13.9f); lineTo(4.5f, 13.4f); close()
            moveTo(19.5f, 11.6f); lineTo(15f, 12.5f); lineTo(15.5f, 13.9f); lineTo(19.5f, 13.4f); close()
            // upper grille slot
            moveTo(10.2f, 12.5f); lineTo(13.8f, 12.5f); lineTo(13.8f, 13.3f); lineTo(10.2f, 13.3f); close()
            // wide lower grille
            moveTo(8.4f, 14.8f); lineTo(15.6f, 14.8f); lineTo(16.6f, 16.8f); lineTo(7.4f, 16.8f); close()
        }
        // mirrors
        path(fill = SolidColor(Color.Black)) {
            moveTo(1.6f, 9.4f); lineTo(4.8f, 9.6f); lineTo(4.5f, 11.2f); lineTo(1.6f, 11.2f); close()
            moveTo(22.4f, 9.4f); lineTo(19.2f, 9.6f); lineTo(19.5f, 11.2f); lineTo(22.4f, 11.2f); close()
        }
    }.build()
}
