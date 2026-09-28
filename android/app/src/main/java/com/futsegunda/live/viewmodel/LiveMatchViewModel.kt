package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.LiveEventRepository
import com.futsegunda.live.data.LiveResult
import com.futsegunda.live.network.LiveStateDto
import com.futsegunda.live.network.PlayerDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val TEAM_HOME = "home"
const val TEAM_AWAY = "away"

data class LiveMatchUiState(
    val loading: Boolean = true,
    val hasActiveMatch: Boolean = false,
    val players: List<PlayerDto> = emptyList(),
    val live: LiveStateDto? = null,
    val lastSyncError: Boolean = false,
    val pendingCount: Int = 0,
    val toast: String? = null,
)

class LiveMatchViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LiveEventRepository(app)

    private val _state = MutableStateFlow(LiveMatchUiState())
    val state: StateFlow<LiveMatchUiState> = _state.asStateFlow()

    private var polling = false

    init {
        viewModelScope.launch {
            repo.pendingCountFlow().collect { count ->
                _state.value = _state.value.copy(pendingCount = count)
            }
        }
        startPolling()
    }

    fun playerName(id: Int?): String =
        id?.let { pid -> _state.value.players.find { it.id == pid }?.name } ?: "?"

    fun playersOf(team: String, ownGoalMode: Boolean): List<PlayerDto> {
        val live = _state.value.live ?: return emptyList()
        // Gol contra: quem marcou é do time ADVERSÁRIO, mas o gol soma pro time que apertou "+".
        val ids = if (ownGoalMode) {
            if (team == TEAM_HOME) live.awayPlayers else live.homePlayers
        } else {
            if (team == TEAM_HOME) live.homePlayers else live.awayPlayers
        }
        return ids.mapNotNull { id -> _state.value.players.find { it.id == id } }
    }

    fun scoreFor(team: String): Int {
        val log = _state.value.live?.goalLog ?: return 0
        return log.count { it.team == team }
    }

    fun addGoal(team: String, scorerId: Int?, assistId: Int?, ownGoal: Boolean, minute: Int?) {
        viewModelScope.launch {
            val result = repo.addGoal(team, scorerId, assistId, ownGoal, minute)
            notifyResult(result)
            refreshNow()
        }
    }

    fun undoGoal(team: String) {
        viewModelScope.launch {
            val result = repo.undoGoal(team)
            notifyResult(result)
            refreshNow()
        }
    }

    fun periodEvent(event: String) {
        viewModelScope.launch {
            val result = repo.periodEvent(event)
            notifyResult(result)
            refreshNow()
        }
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun notifyResult(result: LiveResult) {
        if (result is LiveResult.QueuedOffline) {
            _state.value = _state.value.copy(toast = "Sem conexão — guardado e será enviado depois")
        }
    }

    private suspend fun refreshNow() {
        try {
            val resp = repo.fetchPublicState()
            if (resp != null) {
                _state.value = _state.value.copy(
                    loading = false,
                    hasActiveMatch = resp.liveState?.active == true,
                    players = resp.players,
                    live = resp.liveState,
                    lastSyncError = false,
                )
            } else {
                _state.value = _state.value.copy(loading = false, lastSyncError = true)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, lastSyncError = true)
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (true) {
                refreshNow()
                delay(4000)
            }
        }
    }
}
