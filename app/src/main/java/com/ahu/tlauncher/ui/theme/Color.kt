package com.ahu.tlauncher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from the Tesla-inspired DESIGN.md: a monochrome palette plus one Electric Blue
 * reserved for primary actions. No gradients, no shadows, no borders.
 * Light mode is the spec as written; dark mode uses Carbon Dark surfaces for night driving.
 */
@Immutable
data class TColors(
    val isDark: Boolean,
    val appBg: Color,       // canvas
    val panel: Color,       // left panel / sheets
    val card: Color,        // alternate surface (Light Ash)
    val cardPressed: Color,
    val dock: Color,
    val frost: Color,       // frosted-glass overlays on the map
    val text: Color,        // Carbon Dark / white – headings, primary
    val body: Color,        // Graphite – body copy
    val muted: Color,       // Pewter – tertiary
    val secondary: Color,   // Silver Fog – placeholders, disabled
    val divider: Color,     // Cloud Gray / Pale Silver
    val accent: Color,      // Electric Blue – primary CTA only
    val onAccent: Color,
    val danger: Color,      // in-car safety exception: critical engine alerts only
    val scrim: Color,       // modal overlay
) {
    // Spec has no semantic colours: "ok" states are just the primary text colour.
    val green: Color get() = text
    val warning: Color get() = text
}

object Palette {
    val ElectricBlue = Color(0xFF3E6AE1)
    val PureWhite = Color(0xFFFFFFFF)
    val LightAsh = Color(0xFFF4F4F4)
    val CarbonDark = Color(0xFF171A20)
    val Graphite = Color(0xFF393C41)
    val Pewter = Color(0xFF5C5E62)
    val SilverFog = Color(0xFF8E8E8E)
    val CloudGray = Color(0xFFEEEEEE)
    val PaleSilver = Color(0xFFD0D1D2)
    val AlertRed = Color(0xFFE5484D)
}

val LightColors = TColors(
    isDark = false,
    appBg = Palette.PureWhite,
    panel = Palette.PureWhite,
    card = Palette.LightAsh,
    cardPressed = Palette.CloudGray,
    dock = Palette.PureWhite,
    frost = Color.White.copy(alpha = 0.75f),
    text = Palette.CarbonDark,
    body = Palette.Graphite,
    muted = Palette.Pewter,
    secondary = Palette.SilverFog,
    divider = Palette.CloudGray,
    accent = Palette.ElectricBlue,
    onAccent = Palette.PureWhite,
    danger = Palette.AlertRed,
    scrim = Color(0xA6808080),
)

val DarkColors = TColors(
    isDark = true,
    appBg = Palette.CarbonDark,
    panel = Palette.CarbonDark,
    card = Color(0xFF23262D),
    cardPressed = Color(0xFF2E323A),
    dock = Color(0xFF111317),
    frost = Palette.CarbonDark.copy(alpha = 0.75f),
    text = Palette.PureWhite,
    body = Palette.PaleSilver,
    muted = Color(0xFFA2A4A8),
    secondary = Palette.SilverFog,
    divider = Palette.Graphite,
    accent = Palette.ElectricBlue,
    onAccent = Palette.PureWhite,
    danger = Palette.AlertRed,
    scrim = Color(0xA6000000),
)

val LocalTColors = staticCompositionLocalOf { LightColors }
