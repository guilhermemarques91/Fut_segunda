package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.ConfigRepository
import com.futsegunda.live.data.repository.FinResult
import com.futsegunda.live.data.repository.FinanceiroRepository
import com.futsegunda.live.domain.PagarItem
import com.futsegunda.live.domain.ReceberItem
import com.futsegunda.live.domain.ReceberItems
import com.futsegunda.live.domain.SettleAction
import com.futsegunda.live.network.AttendanceDto
import com.futsegunda.live.network.ChargeHistoryDto
import com.futsegunda.live.network.ConfigDto
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.FeesDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.RecurringExpenseDto
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
private fun today(): String = ISO_DATE.format(Date())

enum class FinanceiroTab { LANCAMENTOS, FECHAMENTO, CONFIGURACOES }

data class FechamentoReport(
    val recMensal: Double, val recAvulso: Double, val recTiraGosto: Double, val recContrib: Double,
    val expByCategory: Map<String, Double>, val recorrentes: List<RecurringExpenseDto>,
    val totalReceitas: Double, val totalDespesas: Double, val saldo: Double,
    val totalEntradas: Double, val totalSaidas: Double, val saldoEmCaixa: Double,
)

data class PlayerFinanceRow(val player: PlayerDto, val paid: Double, val feePending: Double, val dinnerPending: Double)

data class FinanceiroUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val lancamentos: List<LancamentoDto> = emptyList(),
    val expenses: List<ExpenseDto> = emptyList(),
    val recurringExpenses: List<RecurringExpenseDto> = emptyList(),
    val chargeHistory: List<ChargeHistoryDto> = emptyList(),
    val attendances: List<AttendanceDto> = emptyList(),
    val dinnerHistory: List<DinnerHistoryDto> = emptyList(),
    val config: ConfigDto = ConfigDto(),
    val fees: FeesDto = FeesDto(),
    val tab: FinanceiroTab = FinanceiroTab.LANCAMENTOS,
    val search: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val dateExplicit: Boolean = false,
    val statusFilter: String = "pendente",
    val badgeFilter: String = "all",
    val showForm: Boolean = false,
    val formTipo: String = "receita",
    val formPlayerId: Int? = null,
    val formIsGuest: Boolean = false,
    val formGuestName: String = "",
    val formRecType: String = "Mensalidade",
    val formAmount: String = "",
    val formDate: String = today(),
    val formNotes: String = "",
    val formDespDesc: String = "",
    val formDespCat: String = "Janta",
    val formDespRecurring: Boolean = false,
    val selected: Map<String, Double> = emptyMap(),
    val fechamentoYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val fechamentoMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val initialBalanceText: String = "",
    val feeMensalText: String = "",
    val feeAvulsoText: String = "",
    val feeGoleiroIsento: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
) {
    private val guestCashIn: Double get() = lancamentos.filter { it.paid && it.playerId == null }.sumOf { it.amount }
    private val paidExpenses: Double get() = expenses.filter { it.paid }.sumOf { it.amount }

    /** Mesma fórmula do getCaixaTotal() do painel web. */
    val caixaTotal: Double get() = config.initialBalance + players.sumOf { it.balance } + guestCashIn - paidExpenses

    private val allReceber: List<ReceberItem>
        get() = ReceberItems.buildReceber(players, chargeHistory, attendances, dinnerHistory, lancamentos, config.goleiroIsento)
    private val allPagar: List<PagarItem>
        get() = ReceberItems.buildPagar(expenses, recurringExpenses, today().take(7))

    val pendReceberTotal: Double get() = allReceber.filter { it.status == "pendente" }.sumOf { it.amount }
    val pendPagarTotal: Double get() = allPagar.filter { it.status == "pendente" }.sumOf { it.amount }
    val projetado: Double get() = caixaTotal + pendReceberTotal - pendPagarTotal

    private fun passes(status: String, date: String, name: String, desc: String, badge: String): Boolean {
        if (search.isNotBlank()) {
            val s = search.lowercase()
            if (!name.lowercase().contains(s) && !desc.lowercase().contains(s)) return false
        }
        if (status == "pago" || dateExplicit) {
            if (dateFrom.isNotBlank() && date.isNotBlank() && date < dateFrom) return false
            if (dateTo.isNotBlank() && date.isNotBlank() && date > dateTo) return false
        }
        if (badgeFilter != "all" && badge != badgeFilter) return false
        if (statusFilter != "all" && status != statusFilter) return false
        return true
    }

    val filteredReceber: List<ReceberItem>
        get() = allReceber.filter { passes(it.status, it.date, it.name, it.desc, it.badge) }.sortedByDescending { it.date }
    val filteredPagar: List<PagarItem>
        get() = allPagar.filter { passes(it.status, it.date, it.name, it.desc, "despesa") }.sortedByDescending { it.date }

    val bulkCount: Int get() = selected.size
    val bulkSum: Double get() = selected.values.sum()

    // ── Fechamento mensal (frontend/index.html:2972-3070) ──
    val fechamentoLabel: String get() = "${MONTH_NAMES_FULL[fechamentoMonth]} / $fechamentoYear"

    val fechamentoReport: FechamentoReport
        get() {
            val prefix = "%04d-%02d".format(fechamentoYear, fechamentoMonth + 1)
            val allPayments = players.flatMap { p -> p.payments.filter { it.date.startsWith(prefix) } }
            fun sumByType(type: String) = allPayments.filter { it.type == type }.sumOf { it.amount }
            val recMensal = sumByType("Mensalidade")
            val recAvulso = sumByType("Jogo Avulso")
            val recTiraGosto = sumByType("Tira Gosto")
            val recContrib = allPayments.filter { it.type !in listOf("Mensalidade", "Jogo Avulso", "Tira Gosto") }.sumOf { it.amount }
            val totalReceitas = recMensal + recAvulso + recTiraGosto + recContrib

            val monthExpenses = expenses.filter { it.date.startsWith(prefix) }
            val expByCategory = monthExpenses.groupBy { it.category ?: "Outros" }.mapValues { (_, v) -> v.sumOf { it.amount } }
            val totalAvulsas = monthExpenses.sumOf { it.amount }
            val activeRecurring = recurringExpenses.filter { it.active }
            val totalRecorrentes = activeRecurring.sumOf { it.amount }
            val totalDespesas = totalAvulsas + totalRecorrentes
            val saldo = totalReceitas - totalDespesas

            val totalEntradas = config.initialBalance + players.sumOf { it.balance }
            val totalSaidas = expenses.filter { it.paid }.sumOf { it.amount }
            val saldoEmCaixa = totalEntradas - totalSaidas

            return FechamentoReport(recMensal, recAvulso, recTiraGosto, recContrib, expByCategory, activeRecurring, totalReceitas, totalDespesas, saldo, totalEntradas, totalSaidas, saldoEmCaixa)
        }

    // ── Resumo Geral (frontend/index.html:3716-3736) ──
    val playerFinanceRows: List<PlayerFinanceRow>
        get() = players.map { p ->
            val paid = p.payments.sumOf { it.amount }
            val feePending = feePendingFor(p)
            PlayerFinanceRow(p, paid, feePending, p.dinnerDebt)
        }

    private fun feePendingFor(p: PlayerDto): Double {
        if (ReceberItems.isFeeExempt(p, config.goleiroIsento)) return 0.0
        return if (p.isRegular) {
            chargeHistory.sumOf { ch -> ch.charges.find { it.playerId == p.id }?.let { if (!it.paid) it.amount else 0.0 } ?: 0.0 }
        } else {
            val paid = p.payments.filter { it.type != "Tira Gosto" }.sumOf { it.amount }
            val owed = attendances.count { p.id in it.players } * (p.monthlyFee ?: 0.0)
            (owed - paid).coerceAtLeast(0.0)
        }
    }

    val totalReceived: Double get() = playerFinanceRows.sumOf { it.paid }
    val totalFeePending: Double get() = playerFinanceRows.sumOf { it.feePending }
    val totalDinnerPending: Double get() = playerFinanceRows.sumOf { it.dinnerPending }

    val currentMonthCharges: ChargeHistoryDto? get() = chargeHistory.find { it.month == today().take(7) }
    val mensalistasList: List<PlayerDto>
        get() = players.filter { it.isRegular && !ReceberItems.isFeeExempt(it, config.goleiroIsento) }.sortedBy { it.name }
}

/**
 * Financeiro nativo: Lançamentos (feed unificado receita+despesa, formulário
 * inline, quitação individual/em lote), Fechamento Mensal e Configurações
 * (saldo inicial, tarifas, resumo geral) — espelha as 3 abas de
 * showFinanceTab() (frontend/index.html:621-625).
 */
class FinanceiroViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FinanceiroRepository(app)
    private val configRepo = ConfigRepository(app)
    private val _state = MutableStateFlow(FinanceiroUiState())
    val state: StateFlow<FinanceiroUiState> = _state.asStateFlow()

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
        val s = _state.value
        _state.value = s.copy(
            loading = false,
            players = snapshot?.players ?: s.players,
            lancamentos = snapshot?.lancamentos ?: s.lancamentos,
            expenses = snapshot?.expenses ?: s.expenses,
            recurringExpenses = snapshot?.recurringExpenses ?: s.recurringExpenses,
            chargeHistory = snapshot?.chargeHistory ?: s.chargeHistory,
            attendances = snapshot?.attendances ?: s.attendances,
            dinnerHistory = snapshot?.dinnerHistory ?: s.dinnerHistory,
            config = snapshot?.config ?: s.config,
            fees = snapshot?.fees ?: s.fees,
            initialBalanceText = if (s.initialBalanceText.isBlank()) (snapshot?.config?.initialBalance?.takeIf { it != 0.0 }?.toString() ?: s.initialBalanceText) else s.initialBalanceText,
            feeMensalText = s.feeMensalText.ifBlank { (snapshot?.fees ?: s.fees).mensal.toString() },
            feeAvulsoText = s.feeAvulsoText.ifBlank { (snapshot?.fees ?: s.fees).avulso.toString() },
        )
    }

    fun setTab(t: FinanceiroTab) { _state.value = _state.value.copy(tab = t) }

    // ── Filtros ─────────────────────────────────────────
    fun setSearch(v: String) { _state.value = _state.value.copy(search = v) }
    fun setDateFrom(v: String) { _state.value = _state.value.copy(dateFrom = v, dateExplicit = true) }
    fun setDateTo(v: String) { _state.value = _state.value.copy(dateTo = v, dateExplicit = true) }
    fun setStatusFilter(v: String) { _state.value = _state.value.copy(statusFilter = v) }
    fun setBadgeFilter(v: String) { _state.value = _state.value.copy(badgeFilter = v) }
    fun clearFilters() { _state.value = _state.value.copy(search = "", dateFrom = "", dateTo = "", dateExplicit = false, badgeFilter = "all", statusFilter = "pendente") }

    // ── Formulário inline (Novo Lançamento) ──────────────
    fun toggleForm() { _state.value = _state.value.copy(showForm = !_state.value.showForm) }
    fun setFormTipo(v: String) { _state.value = _state.value.copy(formTipo = v) }
    fun setFormPlayer(id: Int?, isGuest: Boolean) { _state.value = _state.value.copy(formPlayerId = id, formIsGuest = isGuest) }
    fun setFormGuestName(v: String) { _state.value = _state.value.copy(formGuestName = v) }
    fun setFormRecType(v: String) { _state.value = _state.value.copy(formRecType = v) }
    fun setFormAmount(v: String) { _state.value = _state.value.copy(formAmount = v) }
    fun setFormDate(v: String) { _state.value = _state.value.copy(formDate = v) }
    fun setFormNotes(v: String) { _state.value = _state.value.copy(formNotes = v) }
    fun setFormDespDesc(v: String) { _state.value = _state.value.copy(formDespDesc = v) }
    fun setFormDespCat(v: String) { _state.value = _state.value.copy(formDespCat = v) }
    fun setFormDespRecurring(v: Boolean) { _state.value = _state.value.copy(formDespRecurring = v) }

    fun saveLancamentoForm() {
        val s = _state.value
        val amount = s.formAmount.replace(",", ".").toDoubleOrNull()
        if (amount == null || amount <= 0) { _state.value = s.copy(error = "Informe um valor válido"); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            if (s.formTipo == "receita") {
                val name = if (s.formIsGuest) s.formGuestName.trim().ifBlank { "Convidado" } else s.players.find { it.id == s.formPlayerId }?.name ?: ""
                if (!s.formIsGuest && s.formPlayerId == null) { _state.value = _state.value.copy(saving = false, error = "Selecione o jogador ou convidado"); return@launch }
                val dto = LancamentoDto(
                    playerId = if (s.formIsGuest) null else s.formPlayerId,
                    name = name, type = s.formRecType, amount = amount, date = s.formDate,
                    notes = s.formNotes.trim().ifBlank { null }, paid = false,
                )
                when (val r = repo.saveLancamento(dto)) {
                    is FinResult.Ok -> _state.value = _state.value.copy(saving = false, lancamentos = r.data, showForm = false, toast = "Lançamento salvo como pendente")
                    is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
                }
            } else {
                if (s.formDespDesc.isBlank()) { _state.value = _state.value.copy(saving = false, error = "Informe a descrição"); return@launch }
                if (s.formDespRecurring) {
                    val dto = RecurringExpenseDto(description = s.formDespDesc.trim(), category = s.formDespCat, amount = amount, active = true)
                    when (val r = repo.saveRecurringExpense(dto)) {
                        is FinResult.Ok -> _state.value = _state.value.copy(saving = false, recurringExpenses = r.data, showForm = false, toast = "Despesa recorrente criada")
                        is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
                    }
                } else {
                    val dto = ExpenseDto(date = s.formDate, description = s.formDespDesc.trim(), category = s.formDespCat, amount = amount, paid = false, notes = s.formNotes.trim().ifBlank { null })
                    when (val r = repo.saveExpense(dto)) {
                        is FinResult.Ok -> _state.value = _state.value.copy(saving = false, expenses = r.data, showForm = false, toast = "Despesa salva como pendente")
                        is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
                    }
                }
            }
        }
    }

    // ── Quitação individual / em lote ────────────────────
    fun toggleSelect(key: String, amount: Double) {
        val cur = _state.value.selected.toMutableMap()
        if (key in cur) cur.remove(key) else cur[key] = amount
        _state.value = _state.value.copy(selected = cur)
    }

    fun clearSelection() { _state.value = _state.value.copy(selected = emptyMap()) }

    fun settleOne(settle: SettleAction) {
        viewModelScope.launch {
            val result = executeSettle(settle)
            if (result != null) _state.value = _state.value.copy(error = result) else _state.value = _state.value.copy(toast = "✅ Quitado")
        }
    }

    fun bulkSettle() {
        val s = _state.value
        if (s.selected.isEmpty()) return
        viewModelScope.launch {
            val allReceber = ReceberItems.buildReceber(s.players, s.chargeHistory, s.attendances, s.dinnerHistory, s.lancamentos, s.config.goleiroIsento)
            val allPagar = ReceberItems.buildPagar(s.expenses, s.recurringExpenses, today().take(7))
            val selectedReceber = allReceber.filter { it.key in s.selected }
            val selectedPagar = allPagar.filter { it.key in s.selected }

            val manualIds = selectedReceber.mapNotNull { (it.settle as? SettleAction.Manual)?.lancId }
            if (manualIds.isNotEmpty()) {
                when (val r = repo.bulkQuitar(manualIds)) {
                    is FinResult.Ok -> _state.value = _state.value.copy(lancamentos = r.data.first, players = r.data.second)
                    is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
                }
            }
            // Os demais tipos selecionados (fee/feecharge/dinner/expense/recurring) não têm bulk
            // endpoint próprio — quita um a um, igual ao painel faria internamente.
            val otherSettles = selectedReceber.filter { it.settle !is SettleAction.Manual }.mapNotNull { it.settle } +
                selectedPagar.mapNotNull { it.settle }
            otherSettles.forEach { executeSettle(it) }
            _state.value = _state.value.copy(selected = emptyMap(), toast = "✅ Quitação em lote concluída")
        }
    }

    private suspend fun executeSettle(settle: SettleAction): String? {
        val s = _state.value
        return when (settle) {
            is SettleAction.Manual -> when (val r = repo.bulkQuitar(listOf(settle.lancId))) {
                is FinResult.Ok -> { _state.value = _state.value.copy(lancamentos = r.data.first, players = r.data.second); null }
                is FinResult.Error -> r.message
            }
            is SettleAction.FeeCharge -> when (val r = repo.payMonthlyCharge(settle.month, settle.playerId)) {
                is FinResult.Ok -> { _state.value = _state.value.copy(chargeHistory = r.data.first, players = r.data.second); null }
                is FinResult.Error -> r.message
            }
            is SettleAction.Fee -> {
                val amount = ReceberItems.buildReceber(s.players, s.chargeHistory, s.attendances, s.dinnerHistory, s.lancamentos, s.config.goleiroIsento)
                    .find { it.settle == settle }?.amount ?: return "Item não encontrado"
                when (val r = repo.settleFee(settle.playerId, amount, settle.ptype)) {
                    is FinResult.Ok -> { _state.value = _state.value.copy(players = r.data); null }
                    is FinResult.Error -> r.message
                }
            }
            is SettleAction.Dinner -> when (val r = repo.settleDinner(settle.playerId, settle.dinnerHistoryId)) {
                is FinResult.Ok -> { _state.value = _state.value.copy(players = r.data.first, dinnerHistory = r.data.second); null }
                is FinResult.Error -> r.message
            }
            is SettleAction.Expense -> {
                val e = s.expenses.find { it.id == settle.expId } ?: return "Despesa não encontrada"
                when (val r = repo.saveExpense(e.copy(paid = true, paidDate = today()))) {
                    is FinResult.Ok -> { _state.value = _state.value.copy(expenses = r.data); null }
                    is FinResult.Error -> r.message
                }
            }
            is SettleAction.Recurring -> {
                val rec = s.recurringExpenses.find { it.id == settle.recId } ?: return "Despesa recorrente não encontrada"
                val monthStr = today().take(7)
                val expResult = repo.saveExpense(ExpenseDto(date = today(), description = rec.description, category = rec.category, amount = rec.amount, paid = true, paidDate = today()))
                if (expResult is FinResult.Error) return expResult.message
                when (val r = repo.saveRecurringExpense(rec.copy(lastPaidMonth = monthStr))) {
                    is FinResult.Ok -> {
                        _state.value = _state.value.copy(recurringExpenses = r.data, expenses = (expResult as FinResult.Ok).data)
                        null
                    }
                    is FinResult.Error -> r.message
                }
            }
        }
    }

    // ── Edição (openEditLancamento/openEditExpense/openEditRecurring) ────
    fun updateLancamento(l: LancamentoDto) {
        viewModelScope.launch {
            when (val r = repo.saveLancamento(l)) {
                is FinResult.Ok -> _state.value = _state.value.copy(lancamentos = r.data, toast = "Lançamento atualizado")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun updateExpense(e: ExpenseDto) {
        viewModelScope.launch {
            when (val r = repo.saveExpense(e)) {
                is FinResult.Ok -> _state.value = _state.value.copy(expenses = r.data, toast = "Despesa atualizada")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun updateRecurringExpense(rec: RecurringExpenseDto) {
        viewModelScope.launch {
            when (val r = repo.saveRecurringExpense(rec)) {
                is FinResult.Ok -> _state.value = _state.value.copy(recurringExpenses = r.data, toast = "Despesa recorrente atualizada")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun deleteLancamento(id: Int) {
        viewModelScope.launch {
            when (val r = repo.deleteLancamento(id)) {
                is FinResult.Ok -> _state.value = _state.value.copy(lancamentos = r.data)
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            when (val r = repo.deleteExpense(id)) {
                is FinResult.Ok -> _state.value = _state.value.copy(expenses = r.data)
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun deleteRecurringExpense(id: Int) {
        viewModelScope.launch {
            when (val r = repo.deleteRecurringExpense(id)) {
                is FinResult.Ok -> _state.value = _state.value.copy(recurringExpenses = r.data)
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    // ── Mensalistas ───────────────────────────────────────
    fun confirmMensalistas() {
        viewModelScope.launch {
            when (val r = repo.generateMonthlyCharges()) {
                is FinResult.Ok -> _state.value = _state.value.copy(chargeHistory = r.data, toast = "✅ Mensalidades lançadas como pendentes")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    // ── Fechamento ────────────────────────────────────────
    fun fechamentoNav(delta: Int) {
        val s = _state.value
        var m = s.fechamentoMonth + delta
        var y = s.fechamentoYear
        if (m < 0) { m = 11; y-- }
        if (m > 11) { m = 0; y++ }
        _state.value = s.copy(fechamentoMonth = m, fechamentoYear = y)
    }

    // ── Configurações ─────────────────────────────────────
    fun setInitialBalanceText(v: String) { _state.value = _state.value.copy(initialBalanceText = v) }
    fun setFeeMensalText(v: String) { _state.value = _state.value.copy(feeMensalText = v) }
    fun setFeeAvulsoText(v: String) { _state.value = _state.value.copy(feeAvulsoText = v) }
    fun setFeeGoleiroIsento(v: Boolean) { _state.value = _state.value.copy(feeGoleiroIsento = v) }

    fun saveInitialBalance() {
        val amount = _state.value.initialBalanceText.replace(",", ".").toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            when (val r = configRepo.saveConfig(_state.value.config.copy(initialBalance = amount))) {
                is FinResult.Ok -> _state.value = _state.value.copy(saving = false, config = r.data, toast = "Saldo inicial salvo")
                is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun saveFees() {
        val mensal = _state.value.feeMensalText.replace(",", ".").toDoubleOrNull() ?: 150.0
        val avulso = _state.value.feeAvulsoText.replace(",", ".").toDoubleOrNull() ?: 50.0
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            when (val r = repo.saveFees(FeesDto(mensal = mensal, avulso = avulso))) {
                is FinResult.Ok -> _state.value = _state.value.copy(saving = false, fees = r.data, toast = "Tarifas salvas")
                is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
            // goleiroIsento faz parte do `config`, não de `fees` — merge separado.
            configRepo.saveConfig(_state.value.config.copy(goleiroIsento = _state.value.feeGoleiroIsento)).let {
                if (it is FinResult.Ok) _state.value = _state.value.copy(config = it.data)
            }
        }
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
