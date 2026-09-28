package com.futsegunda.wear.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.wear.data.LiveEventRepository
import com.futsegunda.wear.data.LiveResult
import com.futsegunda.wear.network.LiveStateDto
import com.futsegunda.wear.network.PlayerDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val TEAM_HOME = "home"
const val TEAM_AWAY = "away"

data class LiveUiState(
    val loading: Boolean = true,
    val hasActiveMatch: Boolean = false,
    val live: LiveStateDto? = null,
    val players: List<PlayerDto> = emptyList(),
    val pendingCount: Int = 0,
    val offlineNotice: Boolean = false,
)

class LiveViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LiveEventRepository(app)

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.pendingCountFlow().collect { count ->
                _state.value = _state.value.copy(pendingCount = count)
            }
        }
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(4000)
            }
        }
    }

    fun scoreFor(team: String): Int = _state.value.live?.goalLog?.count { it.team == team } ?: 0

    /** Nomes dos jogadores do time, na ordem da escalação (`homePlayers`/`awayPlayers`). */
    fun playersOf(team: String): List<PlayerDto> {
        val live = _state.value.live ?: return emptyList()
        val ids = if (team == TEAM_HOME) live.homePlayers else live.awayPlayers
        val byId = _state.value.players.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun addGoal(team: String, scorerId: Int?) = viewModelScope.launch {
        val result = repo.addGoal(team, scorerId)
        showOfflineNoticeIfNeeded(result)
        refresh()
    }

    fun undoGoal(team: String) = viewModelScope.launch {
        val result = repo.undoGoal(team)
        showOfflineNoticeIfNeeded(result)
        refresh()
    }

    fun periodEvent(event: String) = viewModelScope.launch {
        val result = repo.periodEvent(event)
        showOfflineNoticeIfNeeded(result)
        refresh()
    }

    private fun showOfflineNoticeIfNeeded(result: LiveResult) {
        if (result is LiveResult.QueuedOffline) {
            _state.value = _state.value.copy(offlineNotice = true)
        }
    }

    fun dismissOfflineNotice() {
        _state.value = _state.value.copy(offlineNotice = false)
    }

    private suspend fun refresh() {
        try {
            val resp = repo.fetchPublicState()
            if (resp != null) {
                _state.value = _state.value.copy(
                    loading = false,
                    hasActiveMatch = resp.liveState?.active == true,
                    live = resp.liveState,
                    players = resp.players,
                )
            } else {
                _state.value = _state.value.copy(loading = false)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false)
        }
    }
}
