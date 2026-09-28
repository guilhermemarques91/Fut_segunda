package com.futsegunda.live.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.FinResult
import com.futsegunda.live.data.repository.FinanceiroRepository
import com.futsegunda.live.network.ChargeHistoryDto
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.RecurringExpenseDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val POLL_INTERVAL_MS = 5_000L

data class FinanceiroUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val lancamentos: List<LancamentoDto> = emptyList(),
    val expenses: List<ExpenseDto> = emptyList(),
    val recurringExpenses: List<RecurringExpenseDto> = emptyList(),
    val chargeHistory: List<ChargeHistoryDto> = emptyList(),
    val selectedLancamentoIds: Set<Int> = emptySet(),
    val saving: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
) {
    /** Mesma fórmula do getCaixaTotal() do painel web — só sem `config.initialBalance` (chega na Fase 4). */
    val caixaTotal: Double
        get() {
            val guestCashIn = lancamentos.filter { it.paid && it.playerId == null }.sumOf { it.amount }
            val paidExpenses = expenses.filter { it.paid }.sumOf { it.amount }
            return players.sumOf { it.balance } + guestCashIn - paidExpenses
        }

    val currentMonth: String get() = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    val currentMonthCharges: ChargeHistoryDto? get() = chargeHistory.find { it.month == currentMonth }
}

/**
 * Lançamentos manuais, despesas (avulsas/recorrentes) e mensalidades — o
 * "Financeiro" nativo cobre o registro direto; as visões derivadas
 * complexas do painel web (linha por jogo avulso, alocação FIFO por mês)
 * continuam só no painel por enquanto.
 */
class FinanceiroViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FinanceiroRepository(app)
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
        _state.value = _state.value.copy(
            loading = false,
            players = snapshot?.players ?: _state.value.players,
            lancamentos = snapshot?.lancamentos ?: _state.value.lancamentos,
            expenses = snapshot?.expenses ?: _state.value.expenses,
            recurringExpenses = snapshot?.recurringExpenses ?: _state.value.recurringExpenses,
            chargeHistory = snapshot?.chargeHistory ?: _state.value.chargeHistory,
        )
    }

    fun saveLancamento(l: LancamentoDto) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            when (val r = repo.saveLancamento(l)) {
                is FinResult.Ok -> _state.value = _state.value.copy(saving = false, lancamentos = r.data, toast = "Lançamento salvo")
                is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
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

    fun toggleSelected(id: Int) {
        val cur = _state.value.selectedLancamentoIds
        _state.value = _state.value.copy(selectedLancamentoIds = if (id in cur) cur - id else cur + id)
    }

    fun quitarSelecionados() {
        val ids = _state.value.selectedLancamentoIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val r = repo.bulkQuitar(ids)) {
                is FinResult.Ok -> _state.value = _state.value.copy(
                    lancamentos = r.data.first, players = r.data.second,
                    selectedLancamentoIds = emptySet(), toast = "Quitado com sucesso",
                )
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun saveExpense(e: ExpenseDto) {
        viewModelScope.launch {
            when (val r = repo.saveExpense(e)) {
                is FinResult.Ok -> _state.value = _state.value.copy(expenses = r.data, toast = "Despesa salva")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun toggleExpensePaid(e: ExpenseDto) {
        saveExpense(e.copy(paid = !e.paid))
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            when (val r = repo.deleteExpense(id)) {
                is FinResult.Ok -> _state.value = _state.value.copy(expenses = r.data)
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun saveRecurringExpense(r: RecurringExpenseDto) {
        viewModelScope.launch {
            when (val res = repo.saveRecurringExpense(r)) {
                is FinResult.Ok -> _state.value = _state.value.copy(recurringExpenses = res.data, toast = "Despesa recorrente salva")
                is FinResult.Error -> _state.value = _state.value.copy(error = res.message)
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

    fun generateMonthlyCharges() {
        viewModelScope.launch {
            when (val r = repo.generateMonthlyCharges()) {
                is FinResult.Ok -> _state.value = _state.value.copy(chargeHistory = r.data, toast = "Mensalidades geradas")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun payMonthlyCharge(month: String, playerId: Int) {
        viewModelScope.launch {
            when (val r = repo.payMonthlyCharge(month, playerId)) {
                is FinResult.Ok -> _state.value = _state.value.copy(chargeHistory = r.data.first, players = r.data.second, toast = "Mensalidade paga")
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
