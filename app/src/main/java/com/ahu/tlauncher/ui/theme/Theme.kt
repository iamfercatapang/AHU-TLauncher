package com.ahu.tlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

object T {
    val colors: TColors
        @Composable get() = LocalTColors.current
}

@Composable
fun TLauncherTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, background = c.appBg, surface = c.panel, onSurface = c.text, onBackground = c.text)
    } else {
        lightColorScheme(primary = c.accent, background = c.appBg, surface = c.panel, onSurface = c.text, onBackground = c.text)
    }
    CompositionLocalProvider(LocalTColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
