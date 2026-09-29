package com.futsegunda.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Mesma paleta do app Android (android/.../ui/theme/Color.kt), extraída do
// CSS do painel web — mantém as duas telas com a mesma identidade visual.
val FutBackground = Color(0xFF060D1C)
val FutSurface = Color(0xFF0A162A)
val FutOnSurface = Color(0xFFE2E8F0)
val FutGreenStart = Color(0xFF22C55E)
val FutGreenEnd = Color(0xFF15803D)
val FutBlueAccent = Color(0xFF60A5FA)
val FutAmber = Color(0xFFFBBF24)
val FutRed = Color(0xFFEF4444)
val FutTeamPreto = Color(0xFF1E293B)

private val FutWearColors = Colors(
    primary = FutGreenStart,
    primaryVariant = FutGreenEnd,
    secondary = FutBlueAccent,
    secondaryVariant = FutBlueAccent,
    background = FutBackground,
    surface = FutSurface,
    error = FutRed,
    onPrimary = Color.White,
    onSecondary = Color(0xFF06111F),
    onBackground = FutOnSurface,
    onSurface = FutOnSurface,
    onError = Color.White,
)

@Composable
fun FutSegundaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = FutWearColors, content = content)
}
