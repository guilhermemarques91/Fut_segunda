package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.domain.ReceberItem
import com.futsegunda.live.domain.ReceberItems
import com.futsegunda.live.network.AttendanceDto
import com.futsegunda.live.network.ChargeHistoryDto
import com.futsegunda.live.network.ConfigDto
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.ResultDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val POLL_INTERVAL_MS = 5_000L

data class DebtorGroup(val name: String, val player: PlayerDto?, val total: Double, val items: List<ReceberItem>)
data class Highlight(val playerName: String, val count: Int)

data class DashboardUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val results: List<ResultDto> = emptyList(),
    val attendances: List<AttendanceDto> = emptyList(),
    val chargeHistory: List<ChargeHistoryDto> = emptyList(),
    val dinnerHistory: List<DinnerHistoryDto> = emptyList(),
    val lancamentos: List<LancamentoDto> = emptyList(),
    val expenses: List<com.futsegunda.live.network.ExpenseDto> = emptyList(),
    val config: ConfigDto = ConfigDto(),
) {
    val caixaTotal: Double
        get() {
            val guestCashIn = lancamentos.filter { it.paid && it.playerId == null }.sumOf { it.amount }
            val paidExpenses = expenses.filter { it.paid }.sumOf { it.amount }
            return config.initialBalance + players.sumOf { it.balance } + guestCashIn - paidExpenses
        }

    /** Mesma fonte que o Financeiro vai usar na Fase 10 — pendências derivadas, nunca "fantasma". */
    private val pendItems: List<ReceberItem>
        get() = ReceberItems.buildReceber(players, chargeHistory, attendances, dinnerHistory, lancamentos, config.goleiroIsento)
            .filter { it.status == "pendente" }

    val totalFeePend: Double get() = pendItems.filter { it.badge != "tiragosto" }.sumOf { it.amount }
    val totalDinPend: Double get() = pendItems.filter { it.badge == "tiragosto" }.sumOf { it.amount }
    val pendCount: Int get() = pendItems.map { it.name }.toSet().size

    val pendingResultsCount: Int get() = results.count { it.pending || it.homeScore == null }
    val matchCount: Int get() = results.size

    val debtorGroups: List<DebtorGroup>
        get() = pendItems.groupBy { it.name }
            .map { (name, its) ->
                DebtorGroup(name, players.find { it.name == name }, its.sumOf { it.amount }, its.sortedByDescending { it.date })
            }
            .sortedByDescending { it.total }

    val recentResults: List<ResultDto> get() = results.sortedByDescending { it.date }.take(5)

    private val yearResults: List<ResultDto>
        get() {
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            return results.filter { !it.pending && it.date?.startsWith(year) == true }
        }

    val topScorer: Highlight?
        get() {
            val map = mutableMapOf<Int, Int>()
            yearResults.forEach { r -> r.goals.forEach { g -> map[g.playerId] = (map[g.playerId] ?: 0) + g.count } }
            val topId = map.maxByOrNull { it.value }?.key ?: return null
            return Highlight(players.find { it.id == topId }?.name ?: "—", map[topId] ?: 0)
        }

    val topMotm: Highlight?
        get() {
            val map = mutableMapOf<Int, Int>()
            yearResults.forEach { r -> r.motm?.let { map[it] = (map[it] ?: 0) + 1 } }
            val topId = map.maxByOrNull { it.value }?.key ?: return null
            return Highlight(players.find { it.id == topId }?.name ?: "—", map[topId] ?: 0)
        }
}

/**
 * 100% derivado do AppDataCache, espelhando renderDashboard() do painel web
 * (frontend/index.html:1882) — 4 stat cards + Pendências/Resumo Financeiro
 * (esquerda) + Últimas Partidas/Destaques (direita), tudo calculado sobre a
 * mesma fonte de "a receber" que o Financeiro vai usar (ReceberItems), pra
 * nunca mostrar uma pendência que o Financeiro não mostraria também.
 *
 * Faz polling a cada 5s (mesmo padrão do LiveMatchViewModel).
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** `force` força ignorar o cache (ex.: pull-to-refresh); polling automático usa false. */
    suspend fun refresh(force: Boolean = false) {
        val isFirstLoad = _state.value.players.isEmpty() && _state.value.results.isEmpty()
        if (isFirstLoad) _state.value = _state.value.copy(loading = true)
        val snapshot = AppDataCache.ensureFresh(getApplication(), force)
        _state.value = DashboardUiState(
            loading = false,
            players = snapshot?.players ?: _state.value.players,
            results = snapshot?.results ?: _state.value.results,
            attendances = snapshot?.attendances ?: _state.value.attendances,
            chargeHistory = snapshot?.chargeHistory ?: _state.value.chargeHistory,
            dinnerHistory = snapshot?.dinnerHistory ?: _state.value.dinnerHistory,
            lancamentos = snapshot?.lancamentos ?: _state.value.lancamentos,
            expenses = snapshot?.expenses ?: _state.value.expenses,
            config = snapshot?.config ?: _state.value.config,
        )
    }
}
