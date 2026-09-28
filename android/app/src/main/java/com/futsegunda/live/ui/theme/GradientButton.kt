package com.futsegunda.live.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Equivalente ao `.btn-primary` do painel web (degradê verde) — usado só
 * nos CTAs principais de cada tela (Entrar, Salvar, FAB), não em todo botão.
 */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit,
) {
    val brush = if (enabled) {
        Brush.linearGradient(listOf(FutGreenStart, FutGreenEnd))
    } else {
        Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
    ) {
        Row(
            modifier = Modifier.background(brush).padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(LocalTextStyle.current.copy(color = Color.White)) {
                content()
            }
        }
    }
}
