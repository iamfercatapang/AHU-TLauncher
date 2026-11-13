package com.ahu.tlauncher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/**
 * Motion tokens.
 * - `std`: the DESIGN.md transition (0.33s cubic-bezier(0.5, 0, 0, 0.75)) for colour/opacity state changes.
 * - Strong curves for movement (Emil Kowalski's animation guidelines): ease-out for things entering,
 *   ease-in-out for things moving on screen. UI motion stays under ~300 ms.
 * Compose scales all of these by the system "animation duration scale", so turning animations off
 * in Android's developer/accessibility settings turns these off too.
 */
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

    /** Strong ease-out: entrances, exits, counters. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** Strong ease-in-out: an element moving from one place on screen to another. */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val ENTER_MS = 220
    const val MOVE_MS = 300
    const val COUNT_MS = 250

    fun <T> enter() = tween<T>(ENTER_MS, easing = EaseOut)
    fun <T> move() = tween<T>(MOVE_MS, easing = EaseInOut)
    fun <T> count() = tween<T>(COUNT_MS, easing = EaseOut)
}
