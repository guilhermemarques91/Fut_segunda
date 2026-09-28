package com.futsegunda.live.data.repository

import android.content.Context
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.network.ResultSaveRequest
import com.futsegunda.live.network.ResultSaveResponse
import com.futsegunda.live.network.ServerConfig

/**
 * Histórico é majoritariamente leitura (results/teamHistory/attendances/
 * dinnerHistory já vêm no AppDataCache) — o único endpoint de escrita é a
 * edição manual de um resultado (`result_save`, upsert por data).
 */
class HistoricoRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private fun token(): String? = tokenStore.token

    suspend fun saveResult(date: String, homeScore: Int?, awayScore: Int?, motm: Int?, pending: Boolean?): FinResult<List<ResultDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.resultSave(
                apiKey = ServerConfig.API_KEY,
                authToken = token,
                body = JsonCodec.body(ResultSaveRequest(date = date, homeScore = homeScore, awayScore = awayScore, motm = motm, pending = pending)),
            )
            val body = JsonCodec.decode<ResultSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.results) } else FinResult.Error(body?.error ?: "Falha ao salvar resultado")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }
}
