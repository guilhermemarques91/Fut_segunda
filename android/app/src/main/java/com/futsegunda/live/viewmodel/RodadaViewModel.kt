package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.RodadaRepository
import com.futsegunda.live.data.repository.RodadaResult
import com.futsegunda.live.domain.LoucaRotation
import com.futsegunda.live.domain.splitAllIntoTwoTeams
import com.futsegunda.live.network.AttendanceSaveRequest
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.DinnerSaveRequest
import com.futsegunda.live.network.GoalCountDto
import com.futsegunda.live.network.LiveStartRequest
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.TeamHistorySaveRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

enum class TeamZone { PRETO, AZUL, BANCO }

private const val POLL_INTERVAL_MS = 5_000L
private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

data class RodadaUiState(
    val loading: Boolean = true,
    val date: String = DATE_FORMAT.format(Date()),
    val allPlayers: List<PlayerDto> = emptyList(),
    val confirmedIds: List<Int> = emptyList(),
    val locked: Boolean = false,
    val home: List<PlayerDto> = emptyList(),
    val away: List<PlayerDto> = emptyList(),
    val homeReserve: PlayerDto? = null,
    val awayReserve: PlayerDto? = null,
    val teamsGenerated: Boolean = false,
    val teamsEditMode: Boolean = false,
    val dinnerIds: List<Int> = emptyList(),
    val meal: String = "",
    val dinnerTotalText: String = "",
    val loucaResponsavelId: Int? = null,
    val loucaRotation: List<Int> = emptyList(),
    val loucaCycleStart: String? = null,
    val loucaOverrides: Map<String, Boolean> = emptyMap(),
    val dinnerHistory: List<DinnerHistoryDto> = emptyList(),
    val saving: Boolean = false,
    val startingMatch: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
) {
    val dinnerShare: Double
        get() {
            val total = dinnerTotalText.replace(",", ".").toDoubleOrNull() ?: 0.0
            if (total <= 0 || dinnerIds.isEmpty()) return 0.0
            val real = total / dinnerIds.size
            return ceil(real / 5.0) * 5.0
        }

    /** Sugestão de próximo responsável pela louça — mesma lógica de updateLoucaSelectInRodada(date)
     * do painel web, excluindo a própria data da rodada atual do cálculo de "já lavou". */
    val suggestedLoucaResponsavel: Int?
        get() {
            val derived = LoucaRotation.washedMap(dinnerHistory, loucaCycleStart, excludeDate = date)
            return LoucaRotation.nextResponsavel(loucaRotation, loucaOverrides, derived)
        }
}

/**
 * Presença, tira-gosto e times de uma rodada — espelha rodState do painel
 * web (frontend/index.html:3921), mas cada ação salva granular no servidor
 * em vez de "salvar tudo" no debounce.
 */
class RodadaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RodadaRepository(app)

    private val _state = MutableStateFlow(RodadaUiState())
    val state: StateFlow<RodadaUiState> = _state.asStateFlow()

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
        val date = _state.value.date
        val att = snapshot?.attendances?.find { it.date == date }
        val th = snapshot?.teamHistory?.find { it.date == date }
        val din = snapshot?.dinnerHistory?.find { it.date == date }
        val players = snapshot?.players ?: _state.value.allPlayers
        val byId = players.associateBy { it.id }

        // Não pisa numa edição de times em andamento (teamsEditMode) com o que veio do servidor.
        val keepLocalTeams = _state.value.teamsEditMode
        _state.value = _state.value.copy(
            loading = false,
            allPlayers = players,
            confirmedIds = att?.players ?: _state.value.confirmedIds,
            locked = snapshot?.lockedRodadas?.contains(date) ?: _state.value.locked,
            home = if (keepLocalTeams) _state.value.home else th?.home?.mapNotNull { byId[it] } ?: _state.value.home,
            away = if (keepLocalTeams) _state.value.away else th?.away?.mapNotNull { byId[it] } ?: _state.value.away,
            homeReserve = if (keepLocalTeams) _state.value.homeReserve else th?.homeReserve?.let { byId[it] },
            awayReserve = if (keepLocalTeams) _state.value.awayReserve else th?.awayReserve?.let { byId[it] },
            teamsGenerated = keepLocalTeams || th != null,
            dinnerIds = din?.participants ?: _state.value.dinnerIds,
            meal = din?.meal ?: _state.value.meal,
            loucaResponsavelId = din?.loucaResponsavel ?: _state.value.loucaResponsavelId,
            loucaRotation = snapshot?.loucaRotation ?: _state.value.loucaRotation,
            loucaCycleStart = snapshot?.loucaCycleStart ?: _state.value.loucaCycleStart,
            loucaOverrides = snapshot?.loucaOverrides ?: _state.value.loucaOverrides,
            dinnerHistory = snapshot?.dinnerHistory ?: _state.value.dinnerHistory,
        )
    }

    fun setDate(date: String) {
        _state.value = RodadaUiState(date = date, allPlayers = _state.value.allPlayers)
        viewModelScope.launch { refresh() }
    }

    // ── Presença ──────────────────────────────────────────
    fun toggleConfirmed(id: Int) {
        if (_state.value.locked) return
        val cur = _state.value.confirmedIds
        _state.value = _state.value.copy(confirmedIds = if (id in cur) cur - id else cur + id)
        savePresenca()
    }

    private fun savePresenca() {
        viewModelScope.launch {
            val s = _state.value
            repo.saveAttendance(AttendanceSaveRequest(date = s.date, players = s.confirmedIds))
        }
    }

    // ── Times ─────────────────────────────────────────────
    fun generateTeams() {
        val s = _state.value
        if (s.locked) return
        val selected = s.confirmedIds.mapNotNull { id -> s.allPlayers.find { it.id == id } }
        if (selected.size < 4) {
            _state.value = s.copy(error = "Confirme pelo menos 4 jogadores para gerar os times")
            return
        }
        val balanced = splitAllIntoTwoTeams(selected)
        _state.value = s.copy(
            home = balanced.home, away = balanced.away,
            homeReserve = null, awayReserve = null,
            teamsGenerated = true, teamsEditMode = false, error = null,
        )
        saveTeams()
    }

    fun toggleTeamsEditMode() {
        val s = _state.value
        if (s.locked) return
        if (s.confirmedIds.isEmpty()) {
            _state.value = s.copy(error = "Confirme a presença de jogadores primeiro")
            return
        }
        val enteringEdit = !s.teamsEditMode
        if (enteringEdit && !s.teamsGenerated) {
            _state.value = s.copy(teamsEditMode = true, teamsGenerated = true)
            return
        }
        if (!enteringEdit) {
            // Ao concluir: quem ficou no banco (confirmado mas fora dos times) entra no time menor.
            val inTeam = (s.home + s.away).map { it.id }.toSet()
            var home = s.home
            var away = s.away
            s.confirmedIds.mapNotNull { id -> s.allPlayers.find { it.id == id } }
                .filter { it.id !in inTeam }
                .forEach { p -> if (home.size <= away.size) home = home + p else away = away + p }
            _state.value = s.copy(home = home, away = away, teamsEditMode = false)
            saveTeams()
        } else {
            _state.value = s.copy(teamsEditMode = true)
        }
    }

    /** Move um jogador entre as 3 zonas do drag-and-drop (PRETO/AZUL/BANCO). */
    fun movePlayer(playerId: Int, target: TeamZone) {
        val s = _state.value
        val player = s.allPlayers.find { it.id == playerId } ?: return
        var home = s.home.filter { it.id != playerId }
        var away = s.away.filter { it.id != playerId }
        var homeReserve = s.homeReserve?.takeIf { it.id != playerId }
        var awayReserve = s.awayReserve?.takeIf { it.id != playerId }
        when (target) {
            TeamZone.PRETO -> home = home + player
            TeamZone.AZUL -> away = away + player
            TeamZone.BANCO -> { /* já removido de todos acima */ }
        }
        _state.value = s.copy(home = home, away = away, homeReserve = homeReserve, awayReserve = awayReserve)
    }

    /** Jogadores confirmados que não estão em nenhum time (banco, disponíveis pra arrastar). */
    fun benchPlayers(): List<PlayerDto> {
        val s = _state.value
        val assigned = (s.home + s.away).map { it.id }.toSet() +
            listOfNotNull(s.homeReserve?.id, s.awayReserve?.id)
        return s.confirmedIds.filter { it !in assigned }.mapNotNull { id -> s.allPlayers.find { it.id == id } }
    }

    fun saveTeams() {
        viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(saving = true)
            val result = repo.saveTeams(
                TeamHistorySaveRequest(
                    date = s.date, home = s.home.map { it.id }, away = s.away.map { it.id },
                    homeReserve = s.homeReserve?.id, awayReserve = s.awayReserve?.id,
                ),
            )
            _state.value = when (result) {
                is RodadaResult.Ok -> _state.value.copy(saving = false, toast = "Times salvos")
                is RodadaResult.Error -> _state.value.copy(saving = false, error = result.message)
            }
        }
    }

    // ── Tira-gosto ────────────────────────────────────────
    fun toggleDinnerParticipant(id: Int) {
        if (_state.value.locked) return
        val cur = _state.value.dinnerIds
        _state.value = _state.value.copy(dinnerIds = if (id in cur) cur - id else cur + id)
        saveDinner()
    }

    fun updateMeal(meal: String) {
        _state.value = _state.value.copy(meal = meal)
    }

    fun updateDinnerTotal(text: String) {
        _state.value = _state.value.copy(dinnerTotalText = text)
    }

    fun setLoucaResponsavel(id: Int?) {
        _state.value = _state.value.copy(loucaResponsavelId = id)
        saveDinner()
    }

    fun saveDinner() {
        viewModelScope.launch {
            val s = _state.value
            val total = s.dinnerTotalText.replace(",", ".").toDoubleOrNull() ?: 0.0
            repo.saveDinner(
                DinnerSaveRequest(
                    date = s.date, meal = s.meal, total = total,
                    participants = s.dinnerIds, loucaResponsavel = s.loucaResponsavelId,
                ),
            )
        }
    }

    // ── Lock / unlock / exclusão ──────────────────────────
    fun lockRodada() {
        viewModelScope.launch {
            saveTeams(); saveDinner()
            when (val r = repo.setLocked(_state.value.date, true)) {
                is RodadaResult.Ok -> _state.value = _state.value.copy(locked = true, toast = "Rodada bloqueada")
                is RodadaResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun unlockRodada() {
        viewModelScope.launch {
            when (val r = repo.setLocked(_state.value.date, false)) {
                is RodadaResult.Ok -> _state.value = _state.value.copy(locked = false, toast = "Rodada reaberta")
                is RodadaResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun deleteRodada(password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.deleteRodada(_state.value.date, password)) {
                is RodadaResult.Ok -> {
                    _state.value = RodadaUiState(date = _state.value.date, allPlayers = _state.value.allPlayers, toast = "Rodada excluída")
                    onDone(true)
                }
                is RodadaResult.Error -> {
                    _state.value = _state.value.copy(error = r.message)
                    onDone(false)
                }
            }
        }
    }

    // ── Iniciar partida (Ao Vivo) ─────────────────────────
    fun startMatch(onStarted: () -> Unit) {
        val s = _state.value
        if (s.home.isEmpty() && s.away.isEmpty()) {
            _state.value = s.copy(error = "Gere ou monte os times antes de iniciar a partida")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(startingMatch = true)
            val req = LiveStartRequest(
                active = true, date = s.date,
                homePlayers = s.home.map { it.id }, awayPlayers = s.away.map { it.id },
                homeReserveId = s.homeReserve?.id, awayReserveId = s.awayReserve?.id,
                goals = emptyList<GoalCountDto>(), goalLog = emptyList(),
                periodo = 1, timerRunning = false, timerElapsed = 0,
            )
            when (val r = repo.startLiveMatch(req)) {
                is RodadaResult.Ok -> {
                    _state.value = _state.value.copy(startingMatch = false, toast = "Partida iniciada — vai pra aba Ao Vivo")
                    onStarted()
                }
                is RodadaResult.Error -> _state.value = _state.value.copy(startingMatch = false, error = r.message)
            }
        }
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
