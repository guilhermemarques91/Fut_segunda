package com.futsegunda.live.data.repository

import android.content.Context
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.DeletePhotoRequest
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.PlayerAttributes
import com.futsegunda.live.network.PlayerDeleteRequest
import com.futsegunda.live.network.PlayerDeleteResponse
import com.futsegunda.live.network.PlayerDraftDto
import com.futsegunda.live.network.PlayerRateRequest
import com.futsegunda.live.network.PlayerSaveRequest
import com.futsegunda.live.network.PlayerSaveResponse
import com.futsegunda.live.network.ServerConfig
import com.futsegunda.live.network.UploadPhotoResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

sealed class PlayerResult {
    data class Ok(val players: List<com.futsegunda.live.network.PlayerDto>) : PlayerResult()
    data class Error(val message: String) : PlayerResult()
}

/**
 * Ponto único de escrita pra Jogadores — chama os endpoints granulares
 * novos (player_save/player_delete/player_rate/upload_player_photo) e
 * invalida o AppDataCache em qualquer sucesso, pra próxima leitura vir
 * atualizada em todas as telas.
 */
class PlayersRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)

    private fun token(): String? = tokenStore.token

    suspend fun save(draft: PlayerDraftDto): PlayerResult {
        val token = token() ?: return PlayerResult.Error("Sessão expirada — entre de novo")
        return try {
            val req = PlayerSaveRequest(clientEventId = UUID.randomUUID().toString(), player = draft)
            val resp = ApiClient.service.playerSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
            val body = JsonCodec.decode<PlayerSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                PlayerResult.Ok(body.players)
            } else {
                PlayerResult.Error(body?.error ?: "Falha ao salvar jogador")
            }
        } catch (e: Exception) {
            PlayerResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun delete(id: Int): PlayerResult {
        val token = token() ?: return PlayerResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.playerDelete(
                apiKey = ServerConfig.API_KEY, authToken = token,
                body = JsonCodec.body(PlayerDeleteRequest(id)),
            )
            val body = JsonCodec.decode<PlayerDeleteResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                PlayerResult.Ok(body.players)
            } else {
                PlayerResult.Error(body?.error ?: "Falha ao remover jogador")
            }
        } catch (e: Exception) {
            PlayerResult.Error("Sem conexão com o servidor")
        }
    }

    suspend fun rate(id: Int, attributes: PlayerAttributes): PlayerResult {
        val token = token() ?: return PlayerResult.Error("Sessão expirada — entre de novo")
        return try {
            val req = PlayerRateRequest(clientEventId = UUID.randomUUID().toString(), id = id, attributes = attributes)
            val resp = ApiClient.service.playerRate(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(req))
            val body = JsonCodec.decode<PlayerSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) {
                AppDataCache.invalidate()
                PlayerResult.Ok(body.players)
            } else {
                PlayerResult.Error(body?.error ?: "Falha ao avaliar jogador")
            }
        } catch (e: Exception) {
            PlayerResult.Error("Sem conexão com o servidor")
        }
    }

    /** Sobe a foto já redimensionada (ver PhotoResizer) e devolve a URL salva no servidor. */
    suspend fun uploadPhoto(playerId: Int, jpegFile: File): String? {
        val token = token() ?: return null
        return try {
            val filePart = MultipartBody.Part.createFormData(
                "file", jpegFile.name, jpegFile.asRequestBody("image/jpeg".toMediaType()),
            )
            val idPart = playerId.toString().toRequestBody("text/plain".toMediaType())
            val resp = ApiClient.service.uploadPlayerPhoto(
                apiKey = ServerConfig.API_KEY, authToken = token, file = filePart, playerId = idPart,
            )
            val body = JsonCodec.decode<UploadPhotoResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) body.url else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deletePhoto(url: String) {
        val token = token() ?: return
        try {
            ApiClient.service.deletePlayerPhoto(
                apiKey = ServerConfig.API_KEY, authToken = token,
                body = JsonCodec.body(DeletePhotoRequest(url)),
            )
        } catch (e: Exception) {
            // best-effort — igual ao painel web, não bloqueia o fluxo se falhar
        }
    }
}
