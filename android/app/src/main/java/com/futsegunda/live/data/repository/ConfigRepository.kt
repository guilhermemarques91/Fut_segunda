package com.futsegunda.live.data.repository

import android.content.Context
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.ConfigDto
import com.futsegunda.live.network.ConfigSaveRequest
import com.futsegunda.live.network.ConfigSaveResponse
import com.futsegunda.live.network.CreateUserRequest
import com.futsegunda.live.network.CreateUserResponse
import com.futsegunda.live.network.DeleteUserRequest
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.LoucaRotationSaveRequest
import com.futsegunda.live.network.LoucaRotationSaveResponse
import com.futsegunda.live.network.ServerConfig
import com.futsegunda.live.network.SimpleOkResponse
import com.futsegunda.live.network.UpdateUserRoleRequest
import com.futsegunda.live.network.UploadLogoResponse
import com.futsegunda.live.network.UserDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Identidade visual + rotação de louça (endpoints granulares novos da
 * Fase 4) e gestão de usuários (endpoints que já existiam pro painel web,
 * só faltava a tela nativa).
 */
class ConfigRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private fun token(): String? = tokenStore.token

    suspend fun saveConfig(config: ConfigDto): FinResult<ConfigDto> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.configSave(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(ConfigSaveRequest(config)))
            val body = JsonCodec.decode<ConfigSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body.config) } else FinResult.Error(body?.error ?: "Falha ao salvar configuração")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    /** Sobe o arquivo (já redimensionado via PhotoResizer, reaproveitado da Fase 1) e devolve a URL salva. */
    suspend fun uploadLogo(jpegFile: File): FinResult<String> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val filePart = MultipartBody.Part.createFormData("file", jpegFile.name, jpegFile.asRequestBody("image/jpeg".toMediaType()))
            val resp = ApiClient.service.uploadLogo(apiKey = ServerConfig.API_KEY, authToken = token, file = filePart)
            val body = JsonCodec.decode<UploadLogoResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true && body.url != null) FinResult.Ok(body.url) else FinResult.Error(body?.error ?: "Falha ao subir a logo")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun saveLoucaRotation(rotation: List<Int>, cycleStart: String?, overrides: Map<String, Boolean>): FinResult<LoucaRotationSaveResponse> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.loucaRotationSave(
                apiKey = ServerConfig.API_KEY, authToken = token,
                body = JsonCodec.body(LoucaRotationSaveRequest(loucaRotation = rotation, loucaCycleStart = cycleStart, loucaOverrides = overrides)),
            )
            val body = JsonCodec.decode<LoucaRotationSaveResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) { AppDataCache.invalidate(); FinResult.Ok(body) } else FinResult.Error(body?.error ?: "Falha ao salvar rotação de louça")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun listUsers(): FinResult<List<UserDto>> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.listUsers(apiKey = ServerConfig.API_KEY, authToken = token)
            val body = JsonCodec.decode<List<UserDto>>(resp.body())
            if (resp.isSuccessful && body != null) FinResult.Ok(body) else FinResult.Error("Falha ao listar usuários")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun createUser(username: String, password: String, role: String): FinResult<Unit> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.createUser(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(CreateUserRequest(username, password, role)))
            val body = JsonCodec.decode<CreateUserResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) FinResult.Ok(Unit) else FinResult.Error(body?.error ?: "Falha ao criar usuário")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun updateUserRole(id: Int, role: String): FinResult<Unit> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.updateUserRole(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(UpdateUserRoleRequest(id, role)))
            val body = JsonCodec.decode<SimpleOkResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) FinResult.Ok(Unit) else FinResult.Error(body?.error ?: "Falha ao alterar role")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }

    suspend fun deleteUser(id: Int): FinResult<Unit> {
        val token = token() ?: return FinResult.Error("Sessão expirada — entre de novo")
        return try {
            val resp = ApiClient.service.deleteUser(apiKey = ServerConfig.API_KEY, authToken = token, body = JsonCodec.body(DeleteUserRequest(id)))
            val body = JsonCodec.decode<SimpleOkResponse>(resp.body())
            if (resp.isSuccessful && body?.ok == true) FinResult.Ok(Unit) else FinResult.Error(body?.error ?: "Falha ao remover usuário")
        } catch (e: Exception) { FinResult.Error("Sem conexão com o servidor") }
    }
}
