package com.ahu.tlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Radius scale: 4dp for interactive elements, ~12dp for large surfaces, circles for dots. */
object TShapes {
    val button = RoundedCornerShape(4.dp)
    val surface = RoundedCornerShape(12.dp)
    // Aliases used across the UI
    val inner = button
    val card = surface
    val pill = button
    val iconSm = button
    val toast = button
    val frame = surface
}
