package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.FinResult
import com.futsegunda.live.data.repository.HistoricoRepository
import com.futsegunda.live.network.AttendanceDto
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.network.TeamHistoryDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 5_000L

data class PlayerTally(val player: PlayerDto, val total: Int)

data class HistoricoUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val results: List<ResultDto> = emptyList(),
    val teamHistory: List<TeamHistoryDto> = emptyList(),
    val attendances: List<AttendanceDto> = emptyList(),
    val dinnerHistory: List<DinnerHistoryDto> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
) {
    private fun playerOf(id: Int?): PlayerDto? = id?.let { pid -> players.find { it.id == pid } }

    private val finishedResults: List<ResultDto>
        get() = results.filter { !it.pending && it.homeScore != null }

    /** Mesma lógica de renderRankings() do painel web (frontend/index.html:1786) — artilheiro. */
    val goalRanking: List<PlayerTally>
        get() {
            val goalMap = mutableMapOf<Int, Int>()
            finishedResults.forEach { r -> r.goals.forEach { g -> if (g.count > 0) goalMap[g.playerId] = (goalMap[g.playerId] ?: 0) + g.count } }
            return goalMap.entries.mapNotNull { (pid, total) -> playerOf(pid)?.let { PlayerTally(it, total) } }.sortedByDescending { it.total }
        }

    /** Mesma lógica de renderRankings() — melhor da partida (MOTM). */
    val motmRanking: List<PlayerTally>
        get() {
            val motmMap = mutableMapOf<Int, Int>()
            finishedResults.forEach { r -> r.motm?.let { motmMap[it] = (motmMap[it] ?: 0) + 1 } }
            return motmMap.entries.mapNotNull { (pid, total) -> playerOf(pid)?.let { PlayerTally(it, total) } }.sortedByDescending { it.total }
        }

    val matchesComputed: Int get() = finishedResults.size
}

/**
 * Histórico nativo: Resultados (com edição manual), Rankings (artilheiro/
 * MOTM, calculados localmente sobre o cache, sem endpoint próprio), Times,
 * Presenças e Tira Gosto — todos em listas simples. O calendário/matriz de
 * presença do painel web (grid mês a mês) fica simplificado numa lista por
 * data nesta primeira versão nativa.
 */
class HistoricoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HistoricoRepository(app)
    private val _state = MutableStateFlow(HistoricoUiState())
    val state: StateFlow<HistoricoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        val snapshot = AppDataCache.ensureFresh(getApplication())
        _state.value = _state.value.copy(
            loading = false,
            players = snapshot?.players ?: _state.value.players,
            results = snapshot?.results ?: _state.value.results,
            teamHistory = snapshot?.teamHistory ?: _state.value.teamHistory,
            attendances = snapshot?.attendances ?: _state.value.attendances,
            dinnerHistory = snapshot?.dinnerHistory ?: _state.value.dinnerHistory,
        )
    }

    fun saveResult(date: String, homeScore: Int?, awayScore: Int?, motm: Int?, pending: Boolean?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            when (val r = repo.saveResult(date, homeScore, awayScore, motm, pending)) {
                is FinResult.Ok -> _state.value = _state.value.copy(saving = false, results = r.data, toast = "Resultado salvo")
                is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
