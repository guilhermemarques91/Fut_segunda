package com.futsegunda.live.data

import android.content.Context
import com.futsegunda.live.data.db.AppDatabase
import com.futsegunda.live.data.db.PendingEvent
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.GoalAddRequest
import com.futsegunda.live.network.GoalUndoRequest
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.PeriodEventRequest
import com.futsegunda.live.network.PublicStateResponse
import com.futsegunda.live.network.ServerConfig
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.util.UUID

sealed class LiveResult {
    data object SentOnline : LiveResult()
    data object QueuedOffline : LiveResult()
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Ponto único por onde a UI marca gol / desfaz / controla período. Tenta
 * mandar direto pro servidor; se falhar (sem rede, timeout, 5xx), guarda na
 * fila local (Room) e devolve para o SyncWorker tentar de novo depois —
 * sempre com o mesmo clientEventId, então nunca duplica quando reenviado.
 */
class LiveEventRepository(context: Context) {
    private val dao = AppDatabase.get(context).pendingEventDao()
    private val tokenStore = TokenStore(context)

    fun pendingCountFlow(): Flow<Int> = dao.observeCount()

    suspend fun fetchPublicState(): PublicStateResponse? {
        val resp = ApiClient.service.public(apiKey = ServerConfig.API_KEY)
        return if (resp.isSuccessful) JsonCodec.decode<PublicStateResponse>(resp.body()) else null
    }

    suspend fun addGoal(team: String, scorerId: Int?, assistId: Int?, ownGoal: Boolean, minute: Int?): LiveResult {
        val req = GoalAddRequest(
            clientEventId = UUID.randomUUID().toString(),
            team = team,
            scorerId = scorerId,
            assistId = assistId,
            ownGoal = ownGoal,
            minute = minute,
            at = System.currentTimeMillis(),
        )
        val sentOk = tryDirect { token ->
            ApiClient.service.liveGoalAdd(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
        }
        if (sentOk) return LiveResult.SentOnline
        enqueue("goal_add", req.clientEventId, JsonCodec.json.encodeToString(GoalAddRequest.serializer(), req))
        return LiveResult.QueuedOffline
    }

    suspend fun undoGoal(team: String): LiveResult {
        val req = GoalUndoRequest(clientEventId = UUID.randomUUID().toString(), team = team)
        val sentOk = tryDirect { token ->
            ApiClient.service.liveGoalUndo(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
        }
        if (sentOk) return LiveResult.SentOnline
        enqueue("goal_undo", req.clientEventId, JsonCodec.json.encodeToString(GoalUndoRequest.serializer(), req))
        return LiveResult.QueuedOffline
    }

    suspend fun periodEvent(event: String): LiveResult {
        val req = PeriodEventRequest(
            clientEventId = UUID.randomUUID().toString(),
            event = event,
            at = System.currentTimeMillis(),
        )
        val sentOk = tryDirect { token ->
            ApiClient.service.livePeriod(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
        }
        if (sentOk) return LiveResult.SentOnline
        enqueue("period", req.clientEventId, JsonCodec.json.encodeToString(PeriodEventRequest.serializer(), req))
        return LiveResult.QueuedOffline
    }

    /** Chamado pelo SyncWorker (e opcionalmente ao reabrir o app) para escoar a fila. */
    suspend fun flushPending(): Boolean {
        val token = tokenStore.token ?: return false
        var allOk = true
        for (event in dao.listAll()) {
            val ok = try {
                val body = event.payloadJson.toRequestBody(JSON_MEDIA_TYPE)
                val resp: Response<okhttp3.ResponseBody>? = when (event.type) {
                    "goal_add" -> ApiClient.service.liveGoalAdd(apiKey = ServerConfig.API_KEY, authToken = token, body = body)
                    "goal_undo" -> ApiClient.service.liveGoalUndo(apiKey = ServerConfig.API_KEY, authToken = token, body = body)
                    "period" -> ApiClient.service.livePeriod(apiKey = ServerConfig.API_KEY, authToken = token, body = body)
                    else -> null
                }
                resp?.isSuccessful == true
            } catch (e: Exception) {
                false
            }
            if (ok) {
                dao.delete(event)
            } else {
                allOk = false
                dao.update(event.copy(attempts = event.attempts + 1, lastError = "falha ao reenviar"))
            }
        }
        return allOk
    }

    private suspend fun enqueue(type: String, clientEventId: String, payloadJson: String) {
        dao.insert(PendingEvent(clientEventId = clientEventId, type = type, payloadJson = payloadJson))
    }

    /** Tenta mandar direto; devolve false (sem lançar) se não tiver token, rede ou o servidor falhar. */
    private suspend fun tryDirect(call: suspend (String) -> Response<okhttp3.ResponseBody>): Boolean {
        val token = tokenStore.token ?: return false
        return try {
            call(token).isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
