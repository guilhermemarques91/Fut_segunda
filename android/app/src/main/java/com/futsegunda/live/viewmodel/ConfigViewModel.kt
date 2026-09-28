package com.futsegunda.live.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.ConfigRepository
import com.futsegunda.live.data.repository.FinResult
import com.futsegunda.live.network.ConfigDto
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.UserDto
import com.futsegunda.live.util.PhotoResizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 5_000L

data class ConfigUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val config: ConfigDto = ConfigDto(),
    val loucaRotation: List<Int> = emptyList(),
    val loucaCycleStart: String? = null,
    val loucaOverrides: Map<String, Boolean> = emptyMap(),
    val dinnerHistory: List<DinnerHistoryDto> = emptyList(),
    val users: List<UserDto> = emptyList(),
    val usersLoading: Boolean = true,
    val currentUsername: String? = null,
    val isAdmin: Boolean = false,
    val uploadingLogo: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
) {
    /** Mesma lógica de _loucaWashedMapInCycle() do painel web — 1º registro de tira-gosto
     * de cada responsável dentro do ciclo atual, o mais antigo primeiro. */
    fun washedMap(excludeDate: String? = null): Map<Int, DinnerHistoryDto> {
        val cycleStart = loucaCycleStart ?: "0000-00-00"
        val map = LinkedHashMap<Int, DinnerHistoryDto>()
        dinnerHistory
            .filter { it.loucaResponsavel != null && it.date > cycleStart && it.date != excludeDate }
            .sortedBy { it.date }
            .forEach { d -> d.loucaResponsavel?.let { id -> if (id !in map) map[id] = d } }
        return map
    }

    /** Override manual tem prioridade sobre a detecção automática — mesma regra do painel web. */
    fun isWashed(id: Int, derivedMap: Map<Int, DinnerHistoryDto>): Boolean {
        val key = id.toString()
        if (loucaOverrides.containsKey(key)) return loucaOverrides[key] == true
        return derivedMap.containsKey(id)
    }

    val nextLoucaId: Int?
        get() {
            if (loucaRotation.isEmpty()) return null
            val derived = washedMap()
            return loucaRotation.firstOrNull { !isWashed(it, derived) } ?: loucaRotation.first()
        }
}

/**
 * Config nativo (Fase 4): Identidade Visual (logo/nome/título), Rotação de
 * Louça (mesma lógica de ciclo/detecção automática do painel web) e
 * Usuários (endpoints que já eram granulares, só faltava a tela).
 */
class ConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConfigRepository(app)
    private val tokenStore = TokenStore(app)
    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(currentUsername = tokenStore.username, isAdmin = tokenStore.role == "admin")
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
        if (_state.value.isAdmin) refreshUsers()
    }

    private suspend fun refresh() {
        val snapshot = AppDataCache.ensureFresh(getApplication())
        _state.value = _state.value.copy(
            loading = false,
            players = snapshot?.players ?: _state.value.players,
            config = snapshot?.config ?: _state.value.config,
            loucaRotation = snapshot?.loucaRotation ?: _state.value.loucaRotation,
            loucaCycleStart = snapshot?.loucaCycleStart ?: _state.value.loucaCycleStart,
            loucaOverrides = snapshot?.loucaOverrides ?: _state.value.loucaOverrides,
            dinnerHistory = snapshot?.dinnerHistory ?: _state.value.dinnerHistory,
        )
    }

    fun saveIdentity(teamName: String, tabTitle: String) {
        val newConfig = _state.value.config.copy(
            teamName = teamName.trim().ifBlank { "Fut Segunda" },
            tabTitle = tabTitle.trim().ifBlank { "Fut Segunda — Manager" },
        )
        persistConfig(newConfig, "Identidade salva")
    }

    fun pickLogo(uri: Uri) {
        _state.value = _state.value.copy(uploadingLogo = true)
        viewModelScope.launch {
            val resized = PhotoResizer.resizeToJpeg(getApplication(), uri)
            if (resized == null) {
                _state.value = _state.value.copy(uploadingLogo = false, error = "Não foi possível processar a imagem")
                return@launch
            }
            when (val r = repo.uploadLogo(resized)) {
                is FinResult.Ok -> persistConfig(_state.value.config.copy(logo = r.data), "Logo atualizada")
                is FinResult.Error -> _state.value = _state.value.copy(uploadingLogo = false, error = r.message)
            }
            resized.delete()
        }
    }

    fun removeLogo() {
        persistConfig(_state.value.config.copy(logo = null), "Logo removida")
    }

    private fun persistConfig(newConfig: ConfigDto, successMsg: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, uploadingLogo = false)
            when (val r = repo.saveConfig(newConfig)) {
                is FinResult.Ok -> _state.value = _state.value.copy(saving = false, config = r.data, toast = successMsg)
                is FinResult.Error -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    // ── Rotação de louça ──────────────────────────────────

    fun toggleWashed(id: Int) {
        val derived = _state.value.washedMap()
        val target = !_state.value.isWashed(id, derived)
        val overrides = _state.value.loucaOverrides.toMutableMap()
        val key = id.toString()
        if (target == derived.containsKey(id)) overrides.remove(key) else overrides[key] = target
        persistLouca(_state.value.loucaRotation, _state.value.loucaCycleStart, overrides)
    }

    fun resetCycle() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        persistLouca(_state.value.loucaRotation, today, emptyMap())
    }

    fun addToRotation(playerId: Int) {
        if (playerId in _state.value.loucaRotation) return
        persistLouca(_state.value.loucaRotation + playerId, _state.value.loucaCycleStart, _state.value.loucaOverrides)
    }

    fun removeFromRotation(index: Int) {
        val rot = _state.value.loucaRotation.toMutableList()
        if (index !in rot.indices) return
        rot.removeAt(index)
        persistLouca(rot, _state.value.loucaCycleStart, _state.value.loucaOverrides)
    }

    fun moveRotationUp(index: Int) {
        val rot = _state.value.loucaRotation.toMutableList()
        if (index <= 0 || index >= rot.size) return
        val tmp = rot[index - 1]; rot[index - 1] = rot[index]; rot[index] = tmp
        persistLouca(rot, _state.value.loucaCycleStart, _state.value.loucaOverrides)
    }

    fun moveRotationDown(index: Int) {
        val rot = _state.value.loucaRotation.toMutableList()
        if (index < 0 || index >= rot.size - 1) return
        val tmp = rot[index + 1]; rot[index + 1] = rot[index]; rot[index] = tmp
        persistLouca(rot, _state.value.loucaCycleStart, _state.value.loucaOverrides)
    }

    private fun persistLouca(rotation: List<Int>, cycleStart: String?, overrides: Map<String, Boolean>) {
        // Otimista: aplica local já, e confirma com o servidor em seguida.
        _state.value = _state.value.copy(loucaRotation = rotation, loucaCycleStart = cycleStart, loucaOverrides = overrides)
        viewModelScope.launch {
            when (val r = repo.saveLoucaRotation(rotation, cycleStart, overrides)) {
                is FinResult.Ok -> {
                    AppDataCache.invalidate()
                    _state.value = _state.value.copy(loucaRotation = r.data.loucaRotation, loucaCycleStart = r.data.loucaCycleStart, loucaOverrides = r.data.loucaOverrides)
                }
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    // ── Usuários ──────────────────────────────────────────

    fun refreshUsers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(usersLoading = true)
            when (val r = repo.listUsers()) {
                is FinResult.Ok -> _state.value = _state.value.copy(usersLoading = false, users = r.data)
                is FinResult.Error -> _state.value = _state.value.copy(usersLoading = false, error = r.message)
            }
        }
    }

    fun createUser(username: String, password: String, role: String) {
        viewModelScope.launch {
            when (val r = repo.createUser(username, password, role)) {
                is FinResult.Ok -> { _state.value = _state.value.copy(toast = "Usuário criado"); refreshUsers() }
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun updateUserRole(id: Int, role: String) {
        viewModelScope.launch {
            when (val r = repo.updateUserRole(id, role)) {
                is FinResult.Ok -> refreshUsers()
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            when (val r = repo.deleteUser(id)) {
                is FinResult.Ok -> refreshUsers()
                is FinResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
