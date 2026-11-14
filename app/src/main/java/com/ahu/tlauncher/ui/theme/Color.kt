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
    /** "OK / energy" colour: the prototype's vehicle green at night; plain text by day (DESIGN.md has no semantic colours). */
    val green: Color,
    /** Round map buttons, Navigate pill, toasts. */
    val chrome: Color,
    /** Settings content area. */
    val sheet: Color,
) {
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
    scrim = Color(0x66808080),
    green = Palette.CarbonDark,
    chrome = Palette.LightAsh.copy(alpha = 0.96f),
    sheet = Palette.PureWhite,
)

/** Night theme: the Tesla UI prototype's tokens (near-black surfaces, black dock, blue accent, vehicle green). */
object NightPalette {
    val AppBg = Color(0xFF1A1A1A)
    val Sidebar = Color(0xFF0E0E0E)
    val NavTree = Color(0xFF161616)
    val SettingsPanel = Color(0xFF090909)
    val ControlBar = Color(0xFF000000)
    val SwitchOn = Color(0xFF007BE4)
    val VehicleGreen = Color(0xFF25CB55)
    val Muted = Color.White.copy(alpha = 0.4f)
    val Secondary = Color(0xFF7F7F7F)
    val Divider = Color(0x33797979)
    val Toast = Color.Black.copy(alpha = 0.9f)
}

val DarkColors = TColors(
    isDark = true,
    appBg = NightPalette.AppBg,
    panel = NightPalette.Sidebar,
    card = NightPalette.NavTree,
    cardPressed = Color(0xFF242424),
    dock = NightPalette.ControlBar,
    frost = NightPalette.Toast,
    text = Palette.PureWhite,
    body = Color.White.copy(alpha = 0.8f),
    muted = NightPalette.Muted,
    secondary = NightPalette.Secondary,
    divider = NightPalette.Divider,
    accent = NightPalette.SwitchOn,
    onAccent = Palette.PureWhite,
    danger = Palette.AlertRed,
    scrim = Color(0x66080B10),
    green = NightPalette.VehicleGreen,
    chrome = NightPalette.Toast,
    sheet = NightPalette.SettingsPanel,
)

val LocalTColors = staticCompositionLocalOf { LightColors }
