package com.futsegunda.live.data.cache

import android.content.Context
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.AppSnapshotDto
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Leitura compartilhada entre telas (Dashboard, Jogadores, e as que vierem
 * nas próximas fases) pra não bater na rede toda vez que uma tela abre.
 * Sem DI no projeto — é um `object` singleton, mesmo padrão de `ApiClient`.
 *
 * Só cacheia LEITURA: o GET sem `action` não tem risco de concorrência (o
 * que virou granular foi a ESCRITA — ver os `*_save`/`*_delete` novos em
 * api.php). Qualquer repository que escreva com sucesso deve chamar
 * `invalidate()` pra próxima leitura vir atualizada.
 */
object AppDataCache {
    private const val MAX_AGE_MS = 15_000L

    private val _snapshot = MutableStateFlow<AppSnapshotDto?>(null)
    val snapshot: StateFlow<AppSnapshotDto?> = _snapshot.asStateFlow()

    private var lastFetchAt = 0L
    private val mutex = Mutex()

    suspend fun ensureFresh(context: Context, force: Boolean = false): AppSnapshotDto? {
        val fresh = !force && (System.currentTimeMillis() - lastFetchAt) < MAX_AGE_MS && _snapshot.value != null
        if (fresh) return _snapshot.value

        return mutex.withLock {
            // Outra chamada pode ter atualizado enquanto esperávamos o lock.
            val stillStale = force || (System.currentTimeMillis() - lastFetchAt) >= MAX_AGE_MS || _snapshot.value == null
            if (!stillStale) return@withLock _snapshot.value

            val token = TokenStore(context).token ?: return@withLock _snapshot.value
            try {
                val resp = ApiClient.service.fullData(apiKey = ServerConfig.API_KEY, authToken = token)
                if (resp.isSuccessful) {
                    val data = JsonCodec.decode<AppSnapshotDto>(resp.body())
                    if (data != null) {
                        _snapshot.value = data
                        lastFetchAt = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                // Mantém o snapshot antigo (se houver) em vez de apagar tudo por causa de uma falha de rede.
            }
            _snapshot.value
        }
    }

    /** Chamado por qualquer escrita bem-sucedida (player_save, fees_save, ...) pra forçar rebusca. */
    fun invalidate() {
        lastFetchAt = 0L
    }
}
