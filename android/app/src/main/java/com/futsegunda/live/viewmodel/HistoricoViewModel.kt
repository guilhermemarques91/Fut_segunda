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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val POLL_INTERVAL_MS = 5_000L
private val ISO_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
val MONTH_NAMES_FULL = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
val MONTH_NAMES_SHORT = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
val DOW_NAMES_SHORT = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

data class PlayerTally(val player: PlayerDto, val total: Int)

/** 1 célula do grid do calendário — null nas posições vazias antes do dia 1. */
data class CalendarDay(val day: Int, val date: String, val hasMatch: Boolean, val playerCount: Int, val isToday: Boolean, val isSelected: Boolean)

data class DayDetail(
    val date: String,
    val opponent: String,
    val present: List<PlayerDto>,
    val absent: List<PlayerDto>,
    val result: ResultDto?,
    val hasTeams: Boolean,
)

data class MatrixRow(val player: PlayerDto, val count: Int, val pct: Int)

data class HistoricoUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val results: List<ResultDto> = emptyList(),
    val teamHistory: List<TeamHistoryDto> = emptyList(),
    val attendances: List<AttendanceDto> = emptyList(),
    val dinnerHistory: List<DinnerHistoryDto> = emptyList(),
    val calYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val calMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedDay: String? = null,
    val histFilter: String = "all",
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

    // ── Calendário (frontend/index.html:1427-1454) ────────
    val calendarMonthLabel: String get() = "${MONTH_NAMES_FULL[calMonth]} $calYear"

    val calendarCells: List<CalendarDay?>
        get() {
            val cal = Calendar.getInstance()
            cal.set(calYear, calMonth, 1, 0, 0, 0)
            val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Calendar: domingo=1..sábado=7 → segunda=0
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val monthPrefix = "%04d-%02d".format(calYear, calMonth + 1)
            val byDate = attendances.filter { it.date.startsWith(monthPrefix) }.associateBy { it.date }
            val todayStr = ISO_DATE.format(Date())
            val cells = mutableListOf<CalendarDay?>()
            repeat(firstDow) { cells.add(null) }
            for (d in 1..daysInMonth) {
                val ds = "%04d-%02d-%02d".format(calYear, calMonth + 1, d)
                val match = byDate[ds]
                cells.add(CalendarDay(d, ds, match != null, match?.players?.size ?: 0, ds == todayStr, ds == selectedDay))
            }
            return cells
        }

    val selectedDayDetail: DayDetail?
        get() {
            val date = selectedDay ?: return null
            val att = attendances.find { it.date == date } ?: return null
            val present = players.filter { it.id in att.players }
            val absent = players.filter { it.id !in att.players }
            return DayDetail(
                date = date, opponent = att.opponent.orEmpty(),
                present = present, absent = absent,
                result = results.find { it.date == date },
                hasTeams = teamHistory.any { it.date == date },
            )
        }

    // ── Matriz de presenças (frontend/index.html:1515-1600) ─
    val availableMonths: List<String>
        get() = attendances.map { it.date.take(7) }.filter { it.length == 7 }.distinct().sortedDescending()

    val filteredGames: List<AttendanceDto>
        get() = (if (histFilter == "all") attendances else attendances.filter { it.date.startsWith(histFilter) }).sortedBy { it.date }

    private val matrixCounts: Map<Int, Int>
        get() = players.associate { p -> p.id to filteredGames.count { p.id in it.players } }

    val totalMatchesInFilter: Int get() = filteredGames.size

    val avgAttendance: String
        get() {
            if (filteredGames.isEmpty()) return "—"
            val total = filteredGames.sumOf { it.players.size }
            return "%.1f".format(total.toDouble() / filteredGames.size)
        }

    val topAttendee: PlayerTally?
        get() {
            val counts = matrixCounts
            val top = players.maxByOrNull { counts[it.id] ?: 0 } ?: return null
            val c = counts[top.id] ?: 0
            return if (c > 0) PlayerTally(top, c) else null
        }

    val matrixRows: List<MatrixRow>
        get() {
            val counts = matrixCounts
            return players.sortedWith(compareByDescending<PlayerDto> { counts[it.id] ?: 0 }.thenBy { it.name })
                .map { p ->
                    val c = counts[p.id] ?: 0
                    val pct = if (totalMatchesInFilter > 0) c * 100 / totalMatchesInFilter else 0
                    MatrixRow(p, c, pct)
                }
        }
}

/**
 * Histórico nativo: Presenças (calendário + matriz), Times, Resultados
 * (com edição manual), Rankings e Tira Gosto — espelha as 5 abas de
 * `showHistTab()` (frontend/index.html:1401-1411), na mesma ordem.
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

    // ── Calendário ──────────────────────────────────────
    fun calNav(delta: Int) {
        val s = _state.value
        var month = s.calMonth + delta
        var year = s.calYear
        if (month < 0) { month = 11; year-- }
        if (month > 11) { month = 0; year++ }
        _state.value = s.copy(calMonth = month, calYear = year, selectedDay = null)
    }

    fun selectDay(date: String) {
        _state.value = _state.value.copy(selectedDay = if (_state.value.selectedDay == date) null else date)
    }

    fun setHistFilter(value: String) {
        _state.value = _state.value.copy(histFilter = value)
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
