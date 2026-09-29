package com.futsegunda.live.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Mesma faixa de cores por overall do painel web (getOverallColor, frontend/index.html:1293). */
fun overallRingColor(overall: Int): Color = when {
    overall >= 85 -> Color(0xFFFACC15) // yellow-400 — Lenda
    overall >= 80 -> Color(0xFFFB923C) // orange-400 — Elite
    overall >= 75 -> Color(0xFF4ADE80) // green-400 — Excelente
    overall >= 70 -> Color(0xFF60A5FA) // blue-400 — Bom
    overall >= 65 -> Color(0xFF9CA3AF) // gray-400 — Médio
    else -> Color(0xFFF87171) // red-400 — Iniciante
}

/** `.overall-ring` do painel — círculo com borda colorida pela faixa e o número no meio. */
@Composable
fun OverallRing(overall: Int, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    val color = overallRingColor(overall)
    Box(
        modifier = modifier.size(size).border(BorderStroke(2.dp, color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(overall.toString(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
