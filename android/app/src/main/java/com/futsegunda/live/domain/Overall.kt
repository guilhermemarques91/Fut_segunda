package com.futsegunda.live.domain

import kotlin.math.roundToInt

/**
 * Port de `calcOverall()`/`getOverallLabel()` do painel web
 * (frontend/index.html:1291-1294) — precisa bater exatamente com o cálculo
 * que o servidor também replica em `player_rate` (api/api.php), senão o
 * overall diverge entre os três lugares.
 */
fun calcOverall(physical: Int, tactical: Int, technical: Int): Int =
    (physical * 0.3 + tactical * 0.35 + technical * 0.35).roundToInt()

fun overallLabel(overall: Int): String = when {
    overall >= 85 -> "Lenda"
    overall >= 80 -> "Elite"
    overall >= 75 -> "Excelente"
    overall >= 70 -> "Bom"
    overall >= 65 -> "Médio"
    else -> "Iniciante"
}
