package com.futsegunda.wear.data

import android.content.Context
import com.futsegunda.wear.data.db.AppDatabase
import com.futsegunda.wear.data.db.PendingEvent
import com.futsegunda.wear.network.ApiClient
import com.futsegunda.wear.network.GoalAddRequest
import com.futsegunda.wear.network.GoalUndoRequest
import com.futsegunda.wear.network.JsonCodec
import com.futsegunda.wear.network.PeriodEventRequest
import com.futsegunda.wear.network.PublicStateResponse
import com.futsegunda.wear.network.ServerConfig
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
 * Igual em espírito ao repositório do app Android — tenta mandar direto pro
 * servidor (WiFi/LTE do próprio relógio) e, se não conseguir, guarda na
 * fila local pro SyncWorker reenviar depois. Nunca duplica porque cada
 * evento tem um clientEventId conferido no servidor (Fase 0 do backend).
 */
class LiveEventRepository(context: Context) {
    private val dao = AppDatabase.get(context).pendingEventDao()
    private val tokenStore = TokenStore(context)

    fun pendingCountFlow(): Flow<Int> = dao.observeCount()

    suspend fun fetchPublicState(): PublicStateResponse? {
        val resp = ApiClient.service.public(apiKey = ServerConfig.API_KEY)
        return if (resp.isSuccessful) JsonCodec.decode<PublicStateResponse>(resp.body()) else null
    }

    /** `scorerId` nulo = gol sem artilheiro definido (ajustável depois no celular/painel). */
    suspend fun addGoal(team: String, scorerId: Int?): LiveResult {
        val req = GoalAddRequest(
            clientEventId = UUID.randomUUID().toString(),
            team = team,
            scorerId = scorerId,
            ownGoal = false,
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
                dao.update(event.copy(attempts = event.attempts + 1))
            }
        }
        return allOk
    }

    private suspend fun enqueue(type: String, clientEventId: String, payloadJson: String) {
        dao.insert(PendingEvent(clientEventId = clientEventId, type = type, payloadJson = payloadJson))
    }

    private suspend fun tryDirect(call: suspend (String) -> Response<okhttp3.ResponseBody>): Boolean {
        val token = tokenStore.token ?: return false
        return try {
            call(token).isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
