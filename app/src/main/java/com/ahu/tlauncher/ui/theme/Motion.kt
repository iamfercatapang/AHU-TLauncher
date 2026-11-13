package com.ahu.tlauncher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/** One timing everywhere: 0.33s cubic-bezier(0.5, 0, 0, 0.75). */
object TMotion {
    val easing = CubicBezierEasing(0.5f, 0f, 0f, 0.75f)
    const val DURATION = 330
    fun <T> std() = tween<T>(DURATION, easing = easing)
    // Kept for call sites; all resolve to the single standard transition.
    fun <T> fast() = std<T>()
    fun <T> normal() = std<T>()
    fun <T> medium() = std<T>()
    fun <T> slow() = std<T>()
    fun <T> card() = std<T>()
}
