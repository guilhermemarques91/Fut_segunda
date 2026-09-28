package com.futsegunda.live.domain

import com.futsegunda.live.network.DinnerHistoryDto

/**
 * Port 1:1 de `_loucaWashedMapInCycle`/`_loucaIsWashed`/`getNextLoucaResponsavel`
 * do painel web (frontend/index.html:6683-6707). Compartilhado entre a tela
 * de Config (gestão da rotação) e a Rodada (seletor de responsável ao
 * fechar o tira-gosto) — uma implementação só, pra não divergir.
 */
object LoucaRotation {
    /** 1º registro de tira-gosto de cada responsável dentro do ciclo atual (o mais antigo). */
    fun washedMap(dinnerHistory: List<DinnerHistoryDto>, cycleStart: String?, excludeDate: String? = null): Map<Int, DinnerHistoryDto> {
        val start = cycleStart ?: "0000-00-00"
        val map = LinkedHashMap<Int, DinnerHistoryDto>()
        dinnerHistory
            .filter { it.loucaResponsavel != null && it.date > start && it.date != excludeDate }
            .sortedBy { it.date }
            .forEach { d -> d.loucaResponsavel?.let { id -> if (id !in map) map[id] = d } }
        return map
    }

    /** Override manual (chave = playerId.toString()) tem prioridade sobre a detecção automática. */
    fun isWashed(id: Int, overrides: Map<String, Boolean>, derivedMap: Map<Int, DinnerHistoryDto>): Boolean {
        val key = id.toString()
        if (overrides.containsKey(key)) return overrides[key] == true
        return derivedMap.containsKey(id)
    }

    /** Próximo da fila que ainda não lavou; se todos já lavaram, volta pro primeiro (mesma regra do painel web). */
    fun nextResponsavel(rotation: List<Int>, overrides: Map<String, Boolean>, derivedMap: Map<Int, DinnerHistoryDto>): Int? {
        if (rotation.isEmpty()) return null
        return rotation.firstOrNull { !isWashed(it, overrides, derivedMap) } ?: rotation.first()
    }
}
