package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ResultDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val playerCount: Int = 0,
    val matchCount: Int = 0,
    val recentResults: List<ResultDto> = emptyList(),
)

/**
 * 100% derivado do AppDataCache, igual ao renderDashboard() do painel web
 * (frontend/index.html:1882) — sem endpoint próprio. Caixa Total/Pendências
 * dependem de expenses/config, que só chegam na Fase 3 (Financeiro); por
 * enquanto mostra só o que já dá pra calcular com precisão (jogadores e
 * partidas), em vez de exibir um número errado.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val snapshot = AppDataCache.ensureFresh(getApplication(), force)
            val results = snapshot?.results.orEmpty()
            _state.value = DashboardUiState(
                loading = false,
                playerCount = snapshot?.players?.size ?: 0,
                matchCount = results.count { !it.pending },
                recentResults = results.filter { !it.pending }.sortedByDescending { it.date }.take(5),
            )
        }
    }
}
