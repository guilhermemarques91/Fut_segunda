package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.RodadaRepository
import com.futsegunda.live.data.repository.RodadaResult
import com.futsegunda.live.domain.LoucaRotation
import com.futsegunda.live.domain.splitAllIntoTwoTeams
import com.futsegunda.live.network.AppSnapshotDto
import com.futsegunda.live.network.AttendanceSaveRequest
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.DinnerSaveRequest
import com.futsegunda.live.network.GoalCountDto
import com.futsegunda.live.network.LiveStartRequest
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.network.ServerConfig
import com.futsegunda.live.network.TeamHistorySaveRequest
import com.futsegunda.live.network.TokenPlayerDto
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
enum class RodadaStage { LANDING, PICK_DATE, MANAGE }

private const val POLL_INTERVAL_MS = 5_000L
private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

data class RodadaSummary(
    val date: String,
    val confirmedCount: Int,
    val avulsoCount: Int,
    val homeCount: Int,
    val awayCount: Int,
    val result: ResultDto?,
    val locked: Boolean,
)

data class RodadaUiState(
    val loading: Boolean = true,
    val stage: RodadaStage = RodadaStage.LANDING,
    val date: String = DATE_FORMAT.format(Date()),
    val allPlayers: List<PlayerDto> = emptyList(),
    val summaries: List<RodadaSummary> = emptyList(),
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
    val homeScoreText: String = "",
    val awayScoreText: String = "",
    val motmId: Int? = null,
    val avulsoOrder: List<Int> = emptyList(),
    val sendingWhatsApp: Boolean = false,
    val whatsAppLink: String? = null,
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

    /** Avulsos confirmados, na ordem configurada (`avulsoOrder`) — os que ainda não estão na lista entram no fim. */
    val orderedAvulsosConfirmed: List<PlayerDto>
        get() {
            val avulsosConfirmed = confirmedIds.mapNotNull { id -> allPlayers.find { it.id == id && !it.isRegular } }
            val byId = avulsosConfirmed.associateBy { it.id }
            val ordered = avulsoOrder.mapNotNull { byId[it] }
            val missing = avulsosConfirmed.filter { it.id !in avulsoOrder }
            return ordered + missing
        }
}

/**
 * Presença, tira-gosto, times e resultado de uma rodada — espelha rodState
 * do painel web (frontend/index.html:3921), na mesma estrutura de 2 etapas
 * (landing com histórico → formulário de gestão), mas cada ação salva
 * granular no servidor em vez de "salvar tudo" no debounce.
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
        val res = snapshot?.results?.find { it.date == date }
        val players = snapshot?.players ?: _state.value.allPlayers
        val byId = players.associateBy { it.id }

        // Não pisa numa edição de times em andamento (teamsEditMode) com o que veio do servidor.
        val keepLocalTeams = _state.value.teamsEditMode
        _state.value = _state.value.copy(
            loading = false,
            allPlayers = players,
            summaries = snapshot?.let { buildSummaries(it) } ?: _state.value.summaries,
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
            homeScoreText = if (res != null) (res.homeScore?.toString() ?: "") else _state.value.homeScoreText,
            awayScoreText = if (res != null) (res.awayScore?.toString() ?: "") else _state.value.awayScoreText,
            motmId = res?.motm ?: _state.value.motmId,
            avulsoOrder = snapshot?.avulsoOrder ?: _state.value.avulsoOrder,
        )
    }

    /** Mesma união de datas de renderRodadaHistory() (frontend/index.html:5855-5918). */
    private fun buildSummaries(snapshot: AppSnapshotDto): List<RodadaSummary> {
        val dates = (snapshot.attendances.map { it.date } + snapshot.results.map { it.date.orEmpty() }).filter { it.isNotBlank() }.toSet()
        return dates.map { date ->
            val att = snapshot.attendances.find { it.date == date }
            val th = snapshot.teamHistory.find { it.date == date }
            val res = snapshot.results.find { it.date == date }
            val presentes = att?.players.orEmpty().mapNotNull { id -> snapshot.players.find { it.id == id } }
            RodadaSummary(
                date = date,
                confirmedCount = presentes.size,
                avulsoCount = presentes.count { !it.isRegular && !it.isIsento },
                homeCount = th?.home?.size ?: 0,
                awayCount = th?.away?.size ?: 0,
                result = res,
                locked = snapshot.lockedRodadas.contains(date),
            )
        }.sortedByDescending { it.date }
    }

    // ── Navegação landing ↔ gerenciar ──────────────────────
    fun openLanding() {
        _state.value = _state.value.copy(stage = RodadaStage.LANDING)
        viewModelScope.launch { refresh() }
    }

    fun openNewRodada() {
        _state.value = RodadaUiState(stage = RodadaStage.PICK_DATE, date = DATE_FORMAT.format(Date()), allPlayers = _state.value.allPlayers, summaries = _state.value.summaries)
        viewModelScope.launch { refresh() }
    }

    fun openExistingRodada(date: String) {
        _state.value = RodadaUiState(stage = RodadaStage.MANAGE, date = date, allPlayers = _state.value.allPlayers, summaries = _state.value.summaries)
        viewModelScope.launch { refresh() }
    }

    fun setNewDate(date: String) {
        _state.value = _state.value.copy(date = date)
    }

    /** Espelha criarRodada() — cria a presença (mensalistas confirmados por padrão) se ainda não existir. */
    fun createRodada() {
        val s = _state.value
        viewModelScope.launch {
            val existing = s.confirmedIds.isNotEmpty()
            if (!existing) {
                val defaults = s.allPlayers.filter { it.isRegular && !it.isIsento }.map { it.id }
                when (val r = repo.saveAttendance(AttendanceSaveRequest(date = s.date, players = defaults))) {
                    is RodadaResult.Ok -> _state.value = _state.value.copy(confirmedIds = defaults)
                    is RodadaResult.Error -> { _state.value = _state.value.copy(error = r.message); return@launch }
                }
            }
            _state.value = _state.value.copy(stage = RodadaStage.MANAGE, toast = "🔄 Rodada criada — Em Andamento")
        }
    }

    // ── Presença ──────────────────────────────────────────
    fun toggleConfirmed(id: Int) {
        if (_state.value.locked) return
        val cur = _state.value.confirmedIds
        _state.value = _state.value.copy(confirmedIds = if (id in cur) cur - id else cur + id)
        savePresenca()
    }

    fun selectAll() {
        if (_state.value.locked) return
        _state.value = _state.value.copy(confirmedIds = _state.value.allPlayers.map { it.id })
        savePresenca()
    }

    fun clearAll() {
        if (_state.value.locked) return
        _state.value = _state.value.copy(confirmedIds = emptyList())
        savePresenca()
    }

    private fun savePresenca() {
        viewModelScope.launch {
            val s = _state.value
            repo.saveAttendance(AttendanceSaveRequest(date = s.date, players = s.confirmedIds))
        }
    }

    // ── Ordenar avulsos ───────────────────────────────────
    fun moveAvulsoUp(playerId: Int) {
        val order = _state.value.orderedAvulsosConfirmed.map { it.id }.toMutableList()
        val i = order.indexOf(playerId)
        if (i <= 0) return
        order[i] = order[i - 1].also { order[i - 1] = order[i] }
        persistAvulsoOrder(order)
    }

    fun moveAvulsoDown(playerId: Int) {
        val order = _state.value.orderedAvulsosConfirmed.map { it.id }.toMutableList()
        val i = order.indexOf(playerId)
        if (i < 0 || i >= order.size - 1) return
        order[i] = order[i + 1].also { order[i + 1] = order[i] }
        persistAvulsoOrder(order)
    }

    private fun persistAvulsoOrder(order: List<Int>) {
        _state.value = _state.value.copy(avulsoOrder = order)
        viewModelScope.launch {
            when (val r = repo.saveAvulsoOrder(order)) {
                is RodadaResult.Ok -> _state.value = _state.value.copy(avulsoOrder = r.data)
                is RodadaResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    // ── WhatsApp (Presença) ───────────────────────────────
    /** "📋 WA" — convite genérico (mensalistas + suplentes), sem depender de status de confirmação. */
    fun buildInviteWhatsAppLink(): String {
        val s = _state.value
        val d = runCatching { SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(DATE_FORMAT.parse(s.date)!!) }.getOrDefault(s.date)
        val regulars = s.allPlayers.filter { it.isRegular && !it.isIsento }.sortedBy { it.name }
        val subs = s.allPlayers.filter { !it.isRegular && !it.isIsento }
        val lines = mutableListOf("⚽ *PELADA DE SEGUNDA!*", "📅 $d", "", "━━━━━━━━━━━━━━━━━━━━", "📋 *MENSALISTAS (confirmados):*")
        regulars.forEach { lines += "✅ ${it.name}" }
        lines += ""; lines += "👥 *SUPLENTES:*"
        subs.forEach { lines += "❓ ${it.name}" }
        lines += ""; lines += "_Confirme sua presença!_"; lines += "━━━━━━━━━━━━━━━━━━━━"
        return "https://wa.me/?text=" + java.net.URLEncoder.encode(lines.joinToString("\n"), "UTF-8")
    }

    /** "✅ Confirmados" — versão simplificada (o app não tem o sistema de status de confirmação por
     * link do painel; aqui é só confirmado/não confirmado a partir da presença marcada). */
    fun buildConfirmadosWhatsAppLink(): String {
        val s = _state.value
        val d = runCatching { SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(DATE_FORMAT.parse(s.date)!!) }.getOrDefault(s.date)
        val confirmed = s.confirmedIds.mapNotNull { id -> s.allPlayers.find { it.id == id } }.sortedBy { it.name }
        val notConfirmed = s.allPlayers.filter { it.isRegular && !it.isIsento && it.id !in s.confirmedIds }.sortedBy { it.name }
        val lines = mutableListOf("⚽ *PELADA DE SEGUNDA!*", "📅 $d", "", "━━━━━━━━━━━━━━━━━━━━", "📋 *CONFIRMADOS:*")
        confirmed.forEach { lines += "✅ ${it.name}" }
        if (notConfirmed.isNotEmpty()) { lines += ""; lines += "❌ *NÃO CONFIRMARAM:*"; notConfirmed.forEach { lines += "❌ ${it.name}" } }
        lines += ""; lines += "━━━━━━━━━━━━━━━━━━━━"
        return "https://wa.me/?text=" + java.net.URLEncoder.encode(lines.joinToString("\n"), "UTF-8")
    }

    /** "📣 Grupo" — o servidor monta a mensagem (wa_build_confirmados_msg) e manda direto via Evolution API. */
    fun sendConfirmadosGrupo() {
        viewModelScope.launch {
            _state.value = _state.value.copy(sendingWhatsApp = true)
            when (val r = repo.sendConfirmadosGrupo(_state.value.date)) {
                is RodadaResult.Ok -> _state.value = _state.value.copy(sendingWhatsApp = false, toast = "📣 Lista enviada no grupo!")
                is RodadaResult.Error -> _state.value = _state.value.copy(sendingWhatsApp = false, error = r.message)
            }
        }
    }

    /** "🔗 Links" — gera o token de confirmação e devolve o link do WhatsApp já pronto (a tela abre). */
    fun generateConfirmationLinks() {
        val s = _state.value
        val withPhone = s.allPlayers.filter { !it.whatsapp.isNullOrBlank() }.map { TokenPlayerDto(it.id, it.name, it.whatsapp!!) }
        if (withPhone.isEmpty()) {
            _state.value = s.copy(error = "Nenhum jogador com WhatsApp cadastrado")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(sendingWhatsApp = true)
            when (val r = repo.generateTokens(s.date, withPhone)) {
                is RodadaResult.Ok -> {
                    val baseUrl = ServerConfig.BASE_URL.removeSuffix("/api/")
                    val link = "$baseUrl/confirmar.php?t=${r.data}"
                    val d = runCatching { SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(DATE_FORMAT.parse(s.date)!!) }.getOrDefault(s.date)
                    val regulars = s.allPlayers.filter { it.isRegular && !it.isIsento }.sortedBy { it.name }
                    val lines = mutableListOf("⚽ *PELADA DE SEGUNDA!*", "📅 $d", "", "━━━━━━━━━━━━━━━━━━━━", "📋 *MENSALISTAS:*")
                    regulars.forEach { lines += "⏳ ${it.name}" }
                    lines += ""; lines += "🔗 *Confirme sua presença pelo link:*"; lines += link
                    lines += ""; lines += "_Clique no link, informe seu número e confirme!_"; lines += "━━━━━━━━━━━━━━━━━━━━"
                    val waLink = "https://wa.me/?text=" + java.net.URLEncoder.encode(lines.joinToString("\n"), "UTF-8")
                    _state.value = _state.value.copy(sendingWhatsApp = false, whatsAppLink = waLink)
                }
                is RodadaResult.Error -> _state.value = _state.value.copy(sendingWhatsApp = false, error = r.message)
            }
        }
    }

    fun consumeWhatsAppLink(): String? {
        val link = _state.value.whatsAppLink
        _state.value = _state.value.copy(whatsAppLink = null)
        return link
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

    // ── Resultado ─────────────────────────────────────────
    fun updateHomeScore(text: String) { _state.value = _state.value.copy(homeScoreText = text) }
    fun updateAwayScore(text: String) { _state.value = _state.value.copy(awayScoreText = text) }
    fun updateMotm(id: Int?) { _state.value = _state.value.copy(motmId = id); saveResult() }

    fun saveResult() {
        viewModelScope.launch {
            val s = _state.value
            when (val r = repo.saveResult(s.date, s.homeScoreText.toIntOrNull(), s.awayScoreText.toIntOrNull(), s.motmId)) {
                is RodadaResult.Ok -> _state.value = _state.value.copy(toast = "Resultado salvo")
                is RodadaResult.Error -> _state.value = _state.value.copy(error = r.message)
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
                    onDone(true)
                    _state.value = RodadaUiState(stage = RodadaStage.LANDING, allPlayers = _state.value.allPlayers, toast = "Rodada excluída")
                    refresh()
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
