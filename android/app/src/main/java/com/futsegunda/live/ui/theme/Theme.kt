package com.futsegunda.live.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Sempre escuro, de propósito — o painel web não tem modo claro, então o
 * app também não tem (não olha `isSystemInDarkTheme`, é sempre o dark
 * theme da marca).
 */
private val FutColorScheme = darkColorScheme(
    primary = FutGreenStart,
    onPrimary = Color.White,
    primaryContainer = FutGreenEnd,
    onPrimaryContainer = Color.White,
    secondary = FutBlueAccent,
    onSecondary = Color.White,
    tertiary = FutAmber,
    onTertiary = Color(0xFF1A1200),
    background = FutBackground,
    onBackground = FutOnSurface,
    surface = FutSurface,
    onSurface = FutOnSurface,
    surfaceVariant = FutSurfaceVariant,
    onSurfaceVariant = FutOnSurfaceMuted,
    error = FutRed,
    onError = Color.White,
    outline = FutBorder,
)

@Composable
fun FutSegundaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FutColorScheme, content = content)
}
