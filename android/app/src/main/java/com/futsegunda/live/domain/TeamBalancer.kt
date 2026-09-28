package com.futsegunda.live.domain

import com.futsegunda.live.network.PlayerDto

private val POS_ORDER = mapOf("Goleiro" to 0, "Defesa" to 1, "Meio" to 2, "Ataque" to 3)

data class BalancedTeams(val home: List<PlayerDto>, val away: List<PlayerDto>)

/**
 * Port exato de `_splitAllIntoTwoTeams` (frontend/index.html:2413-2420):
 * separa os goleiros primeiro (1 melhor pra cada time), depois distribui o
 * resto ordenado por posição/overall alternando pro time menor.
 */
fun splitAllIntoTwoTeams(selected: List<PlayerDto>): BalancedTeams {
    val goalkeepers = selected.filter { it.position == "Goleiro" }.sortedByDescending { it.overall ?: 0 }
    val home = mutableListOf<PlayerDto>()
    val away = mutableListOf<PlayerDto>()
    goalkeepers.getOrNull(0)?.let { home.add(it) }
    goalkeepers.getOrNull(1)?.let { away.add(it) }

    val rest = (goalkeepers.drop(2) + selected.filter { it.position != "Goleiro" })
        .sortedWith(
            compareBy<PlayerDto> { POS_ORDER[it.position] ?: 4 }.thenByDescending { it.overall ?: 0 },
        )
    rest.forEach { p -> if (home.size <= away.size) home.add(p) else away.add(p) }

    return BalancedTeams(home, away)
}
