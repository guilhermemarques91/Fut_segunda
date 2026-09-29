package com.futsegunda.live.data.repository

import android.content.Context
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.ChargeGenerateRequest
import com.futsegunda.live.network.ChargeHistoryDto
import com.futsegunda.live.network.ChargeHistorySaveResponse
import com.futsegunda.live.network.ChargePayRequest
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.DinnerSettleRequest
import com.futsegunda.live.network.DinnerSettleResponse
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.ExpenseSaveRequest
import com.futsegunda.live.network.ExpenseSaveResponse
import com.futsegunda.live.network.FeeSettleRequest
import com.futsegunda.live.network.FeeSettleResponse
import com.futsegunda.live.network.FeesDto
import com.futsegunda.live.network.FeesSaveRequest
import com.futsegunda.live.network.FeesSaveResponse
import com.futsegunda.live.network.IdRequest
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.LancamentoBulkQuitarRequest
import com.futsegunda.live.network.LancamentoBulkQuitarResponse
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.LancamentoSaveRequest
import com.futsegunda.live.network.LancamentoSaveResponse
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.RecurringExpenseDto
import com.futsegunda.live.network.RecurringExpenseSaveRequest
import com.futsegunda.live.network.RecurringExpenseSaveResponse
import com.futsegunda.live.network.ServerConfig

sealed class FinResult<out T> {
    data class Ok<T>(val data: T) : FinResult<T>()
    data class Error(val message: String) : FinResult<Nothing>()
}

/** Mesmo padrão de PlayersRepository/RodadaRepository — endpoint granular + invalida o cache no sucesso. */
class FinanceiroRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private fun token(): String? = tokenStore.token

    suspend fun saveLancamento(l: LancamentoDto): FinResult<List<LancamentoDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.lancamentoSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(LancamentoSaveRequest(l)))
            val body = JsonCodec.decode<LancamentoSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.lancamentos) } else FinResult.Error(body?.error ?: "Falha ao salvar lançamento")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun deleteLancamento(id: Int): FinResult<List<LancamentoDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.lancamentoDelete(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(IdRequest(id)))
            val body = JsonCodec.decode<LancamentoSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.lancamentos) } else FinResult.Error(body?.error ?: "Falha ao remover lançamento")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun bulkQuitar(ids: List<Int>): FinResult<Pair<List<LancamentoDto>, List<PlayerDto>>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.lancamentoBulkQuitar(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(LancamentoBulkQuitarRequest(ids)))
            val body = JsonCodec.decode<LancamentoBulkQuitarResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.lancamentos to body.players) } else FinResult.Error(body?.error ?: "Falha ao quitar")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun saveExpense(e: ExpenseDto): FinResult<List<ExpenseDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.expenseSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(ExpenseSaveRequest(e)))
            val body = JsonCodec.decode<ExpenseSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.expenses) } else FinResult.Error(body?.error ?: "Falha ao salvar despesa")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun deleteExpense(id: Int): FinResult<List<ExpenseDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.expenseDelete(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(IdRequest(id)))
            val body = JsonCodec.decode<ExpenseSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.expenses) } else FinResult.Error(body?.error ?: "Falha ao remover despesa")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun saveRecurringExpense(r: RecurringExpenseDto): FinResult<List<RecurringExpenseDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.recurringExpenseSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(RecurringExpenseSaveRequest(r)))
            val body = JsonCodec.decode<RecurringExpenseSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.recurringExpenses) } else FinResult.Error(body?.error ?: "Falha ao salvar despesa recorrente")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun deleteRecurringExpense(id: Int): FinResult<List<RecurringExpenseDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.recurringExpenseDelete(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(IdRequest(id)))
            val body = JsonCodec.decode<RecurringExpenseSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.recurringExpenses) } else FinResult.Error(body?.error ?: "Falha ao remover despesa recorrente")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun generateMonthlyCharges(): FinResult<List<ChargeHistoryDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.chargeHistorySave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(ChargeGenerateRequest()))
            val body = JsonCodec.decode<ChargeHistorySaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.chargeHistory) } else FinResult.Error(body?.error ?: "Falha ao gerar mensalidades")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun payMonthlyCharge(month: String, playerId: Int): FinResult<Pair<List<ChargeHistoryDto>, List<PlayerDto>>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.chargeHistorySave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(ChargePayRequest(month = month, playerId = playerId)))
            val body = JsonCodec.decode<ChargeHistorySaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.chargeHistory to body.players) } else FinResult.Error(body?.error ?: "Falha ao marcar pago")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    /** Quita 1 cobrança de jogo avulso (linha do buildReceberItems() com settle.type=fee). */
    suspend fun settleFee(playerId: Int, amount: Double, ptype: String): FinResult<List<PlayerDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.feeSettle(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(FeeSettleRequest(playerId, amount, ptype)))
            val body = JsonCodec.decode<FeeSettleResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.players) } else FinResult.Error(body?.error ?: "Falha ao quitar")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    /** Quita a participação de 1 jogador num tira-gosto (settle.type=dinner). */
    suspend fun settleDinner(playerId: Int, dinnerHistoryId: Int): FinResult<Pair<List<PlayerDto>, List<DinnerHistoryDto>>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.dinnerSettle(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(DinnerSettleRequest(playerId, dinnerHistoryId)))
            val body = JsonCodec.decode<DinnerSettleResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.players to body.dinnerHistory) } else FinResult.Error(body?.error ?: "Falha ao quitar")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    /** Tarifas por categoria (aba Configurações do Financeiro). */
    suspend fun saveFees(fees: FeesDto): FinResult<FeesDto> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.feesSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(FeesSaveRequest(fees)))
            val body = JsonCodec.decode<FeesSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.fees) } else FinResult.Error(body?.error ?: "Falha ao salvar tarifas")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }
}
