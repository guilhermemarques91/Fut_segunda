package com.futsegunda.live.data.repository

import android.content.Context
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.AttendanceDto
import com.futsegunda.live.network.AttendanceSaveRequest
import com.futsegunda.live.network.AttendanceSaveResponse
import com.futsegunda.live.network.AvulsoOrderSaveRequest
import com.futsegunda.live.network.AvulsoOrderSaveResponse
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.DinnerSaveRequest
import com.futsegunda.live.network.DinnerSaveResponse
import com.futsegunda.live.network.GenerateTokensRequest
import com.futsegunda.live.network.GenerateTokensResponse
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.LiveStartRequest
import com.futsegunda.live.network.LockedRodadasSaveRequest
import com.futsegunda.live.network.LockedRodadasSaveResponse
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.network.ResultSaveRequest
import com.futsegunda.live.network.ResultSaveResponse
import com.futsegunda.live.network.RodadaDeleteRequest
import com.futsegunda.live.network.RodadaDeleteResponse
import com.futsegunda.live.network.SendConfirmadosRequest
import com.futsegunda.live.network.SendConfirmadosResponse
import com.futsegunda.live.network.ServerConfig
import com.futsegunda.live.network.TeamHistoryDto
import com.futsegunda.live.network.TeamHistorySaveRequest
import com.futsegunda.live.network.TeamHistorySaveResponse
import com.futsegunda.live.network.TokenPlayerDto

sealed class RodadaResult<out T> {
    data class Ok<T>(val data: T) : RodadaResult<T>()
    data class Error(val message: String) : RodadaResult<Nothing>()
}

/**
 * Escrita da Rodada (presença, tira-gosto, times, lock/unlock, exclusão) —
 * mesmo padrão de PlayersRepository: chama o endpoint granular e invalida o
 * AppDataCache em qualquer sucesso.
 */
class RodadaRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private fun token(): String? = tokenStore.token

    suspend fun saveAttendance(req: AttendanceSaveRequest): RodadaResult<List<AttendanceDto>> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.attendanceSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
            val body = JsonCodec.decode<AttendanceSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                RodadaResult.Ok(body.attendances)
            } else RodadaResult.Error(body?.error ?: "Falha ao salvar presença")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun saveAvulsoOrder(order: List<Int>): RodadaResult<List<Int>> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.avulsoOrderSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(AvulsoOrderSaveRequest(order)))
            val body = JsonCodec.decode<AvulsoOrderSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                RodadaResult.Ok(body.avulsoOrder)
            } else RodadaResult.Error(body?.error ?: "Falha ao reordenar avulsos")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun saveTeams(req: TeamHistorySaveRequest): RodadaResult<Pair<List<TeamHistoryDto>, List<ResultDto>>> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.teamHistorySave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
            val body = JsonCodec.decode<TeamHistorySaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                RodadaResult.Ok(body.teamHistory to body.results)
            } else RodadaResult.Error(body?.error ?: "Falha ao salvar times")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun saveDinner(req: DinnerSaveRequest): RodadaResult<List<DinnerHistoryDto>> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.dinnerSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
            val body = JsonCodec.decode<DinnerSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                RodadaResult.Ok(body.dinnerHistory)
            } else RodadaResult.Error(body?.error ?: "Falha ao salvar tira-gosto")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun setLocked(date: String, locked: Boolean): RodadaResult<List<String>> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.lockedRodadasSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(LockedRodadasSaveRequest(date, locked)))
            val body = JsonCodec.decode<LockedRodadasSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                RodadaResult.Ok(body.lockedRodadas)
            } else RodadaResult.Error(body?.error ?: "Falha ao bloquear/reabrir rodada")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun deleteRodada(date: String, password: String): RodadaResult<Unit> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.rodadaDelete(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(RodadaDeleteRequest(date, password)))
            val body = JsonCodec.decode<RodadaDeleteResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                RodadaResult.Ok(Unit)
            } else RodadaResult.Error(body?.error ?: "Senha incorreta")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    /** Espelha startPartida() do painel web — só quem já está logado (X-Auth-Token não é exigido pelo live_update, mas a API key sim). */
    suspend fun startLiveMatch(req: LiveStartRequest): RodadaResult<Unit> {
        return try {
            val resp = ApiClient.service.liveUpdate(apiKey = ServerConfig.API_KEY, body = JsonCodec.body(req))
            if (resp.isSuccessful) RodadaResult.Ok(Unit) else RodadaResult.Error("Falha ao iniciar a partida")
        } catch (e: Exception) {
            RodadaResult.Error("Sem conexão com o servidor")
        }
    }

    /** Card "Resultado" — placar + MVP, mesma action que o Histórico usa pra editar manualmente. */
    suspend fun saveResult(date: String, homeScore: Int?, awayScore: Int?, motm: Int?): RodadaResult<List<ResultDto>> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.resultSave(
                apiKey = ServerConfig.API_KEY, authToken = token,
                body = JsonCodec.body(ResultSaveRequest(date = date, homeScore = homeScore, awayScore = awayScore, motm = motm, pending = homeScore == null)),
            )
            val body = JsonCodec.decode<ResultSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); RodadaResult.Ok(body.results) } else RodadaResult.Error(body?.error ?: "Falha ao salvar resultado")
        } catch (e: Exception) { RodadaResult.Error("Sem conexão com o servidor") }
    }

    /** "📣 Grupo" — texto opcional (se omitido, o servidor monta a mensagem sozinho, igual ao painel). */
    suspend fun sendConfirmadosGrupo(date: String): RodadaResult<String?> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.sendConfirmados(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(SendConfirmadosRequest(date)))
            val body = JsonCodec.decode<SendConfirmadosResponse>(resp.body())
            if (body?.ok == true) RodadaResult.Ok(body.preview)
            else RodadaResult.Error(
                when (body?.err) {
                    "not_configured" -> "WhatsApp do grupo ainda não configurado no servidor"
                    else -> "Falha ao enviar" + (body?.code?.let { " (HTTP $it)" } ?: "")
                },
            )
        } catch (e: Exception) { RodadaResult.Error("Erro de conexão ao enviar") }
    }

    /** "🔗 Links" — gera um token de confirmação pra rodada e devolve pra montar o link do WhatsApp. */
    suspend fun generateTokens(date: String, players: List<TokenPlayerDto>): RodadaResult<String> {
        val token = token() ?: return RodadaResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.generateTokens(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(GenerateTokensRequest(date, players)))
            val body = JsonCodec.decode<GenerateTokensResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true && body.token != null) RodadaResult.Ok(body.token) else RodadaResult.Error(body?.error ?: "Falha ao gerar links")
        } catch (e: Exception) { RodadaResult.Error("Sem conexão com o servidor") }
    }
}
