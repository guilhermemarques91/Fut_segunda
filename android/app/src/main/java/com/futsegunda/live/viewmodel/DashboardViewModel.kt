package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ResultDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val playerCount: Int = 0,
    val matchCount: Int = 0,
    val recentResults: List<ResultDto> = emptyList(),
    val caixaTotal: Double = 0.0,
)

private const val POLL_INTERVAL_MS = 5_000L

/**
 * 100% derivado do AppDataCache, igual ao renderDashboard() do painel web
 * (frontend/index.html:1882) — sem endpoint próprio. Caixa Total usa a
 * mesma fórmula do getCaixaTotal() do painel (players[].balance + entradas
 * avulsas pagas − despesas pagas), sem `config.initialBalance` ainda
 * (chega só na Fase 4, quando `config_save` existir nativamente).
 *
 * Faz polling a cada 5s (mesmo padrão do LiveMatchViewModel) pra refletir
 * mudanças feitas no painel web sem precisar sair e voltar da tela — o TTL
 * curto do AppDataCache evita rebuscar à toa se outra tela já atualizou.
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
        val isFirstLoad = _state.value.playerCount == 0 && _state.value.recentResults.isEmpty()
        if (isFirstLoad) _state.value = _state.value.copy(loading = true)
        val snapshot = AppDataCache.ensureFresh(getApplication(), force)
        val results = snapshot?.results.orEmpty()
        val players = snapshot?.players.orEmpty()
        val guestCashIn = snapshot?.lancamentos.orEmpty().filter { it.paid && it.playerId == null }.sumOf { it.amount }
        val paidExpenses = snapshot?.expenses.orEmpty().filter { it.paid }.sumOf { it.amount }
        _state.value = DashboardUiState(
            loading = false,
            playerCount = players.size,
            matchCount = results.count { !it.pending },
            recentResults = results.filter { !it.pending }.sortedByDescending { it.date }.take(5),
            caixaTotal = players.sumOf { it.balance } + guestCashIn - paidExpenses,
        )
    }
}
