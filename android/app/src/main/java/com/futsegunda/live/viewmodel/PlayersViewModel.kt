package com.futsegunda.live.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futsegunda.live.data.cache.AppDataCache
import com.futsegunda.live.data.repository.PlayerResult
import com.futsegunda.live.data.repository.PlayersRepository
import com.futsegunda.live.domain.calcOverall
import com.futsegunda.live.network.PlayerAttributes
import com.futsegunda.live.network.PlayerDraftDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.util.PhotoResizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 5_000L

data class PlayerFormState(
    val id: Int? = null,
    val name: String = "",
    val apelido: String = "",
    val whatsapp: String = "",
    val position: String = "Goleiro",
    val physical: Int = 65,
    val tactical: Int = 60,
    val technical: Int = 70,
    val isRegular: Boolean = true,
    val isIsento: Boolean = false,
    val photoUrl: String? = null,
    val video: String? = null,
    val uploadingPhoto: Boolean = false,
) {
    val overall: Int get() = calcOverall(physical, tactical, technical)
    val isEditing: Boolean get() = id != null
}

data class PlayersUiState(
    val loading: Boolean = true,
    val players: List<PlayerDto> = emptyList(),
    val search: String = "",
    val positionFilter: String? = null,
    val form: PlayerFormState? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
)

val PLAYER_POSITIONS = listOf("Goleiro", "Defesa", "Meio", "Ataque")

class PlayersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlayersRepository(app)

    private val _state = MutableStateFlow(PlayersUiState())
    val state: StateFlow<PlayersUiState> = _state.asStateFlow()

    init {
        // Polling automático (mesmo padrão do LiveMatchViewModel, a cada 5s) — reflete
        // edições feitas no painel web sem precisar sair e voltar da tela. O TTL curto
        // do AppDataCache evita rebuscar à toa se outra tela já atualizou há pouco.
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** `force` força ignorar o cache (ex.: pull-to-refresh); polling automático usa false. */
    suspend fun refresh(force: Boolean = false) {
        if (_state.value.players.isEmpty()) _state.value = _state.value.copy(loading = true)
        val snapshot = AppDataCache.ensureFresh(getApplication(), force)
        _state.value = _state.value.copy(loading = false, players = snapshot?.players ?: _state.value.players)
    }

    fun setSearch(query: String) {
        _state.value = _state.value.copy(search = query)
    }

    fun setPositionFilter(position: String?) {
        _state.value = _state.value.copy(positionFilter = position)
    }

    fun filteredPlayers(): List<PlayerDto> {
        val s = _state.value
        val q = s.search.trim().lowercase()
        return s.players
            .filter { s.positionFilter == null || it.position == s.positionFilter }
            .filter {
                q.isEmpty() ||
                    it.name.lowercase().contains(q) ||
                    (it.apelido?.lowercase()?.contains(q) == true)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun openNewForm() {
        _state.value = _state.value.copy(form = PlayerFormState(), error = null)
    }

    fun openEditForm(p: PlayerDto) {
        _state.value = _state.value.copy(
            form = PlayerFormState(
                id = p.id,
                name = p.name,
                apelido = p.apelido.orEmpty(),
                whatsapp = p.whatsapp.orEmpty(),
                position = p.position ?: "Goleiro",
                physical = p.attributes.physical,
                tactical = p.attributes.tactical,
                technical = p.attributes.technical,
                isRegular = p.isRegular,
                isIsento = p.isIsento,
                photoUrl = p.photo,
                video = p.video,
            ),
            error = null,
        )
    }

    fun closeForm() {
        _state.value = _state.value.copy(form = null, error = null)
    }

    fun updateForm(transform: (PlayerFormState) -> PlayerFormState) {
        val current = _state.value.form ?: return
        _state.value = _state.value.copy(form = transform(current))
    }

    fun save() {
        val form = _state.value.form ?: return
        if (form.name.isBlank()) {
            _state.value = _state.value.copy(error = "Informe o nome do jogador")
            return
        }
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val draft = PlayerDraftDto(
                id = form.id,
                name = form.name.trim(),
                apelido = form.apelido.trim().ifBlank { null },
                whatsapp = form.whatsapp.trim().ifBlank { null },
                position = form.position,
                attributes = PlayerAttributes(form.physical, form.tactical, form.technical),
                overall = form.overall,
                isRegular = form.isRegular,
                isIsento = form.isIsento,
                photo = form.photoUrl,
                video = form.video,
            )
            when (val result = repo.save(draft)) {
                is PlayerResult.Ok -> {
                    _state.value = _state.value.copy(saving = false, players = result.players, form = null, toast = "Jogador salvo")
                }
                is PlayerResult.Error -> {
                    _state.value = _state.value.copy(saving = false, error = result.message)
                }
            }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            when (val result = repo.delete(id)) {
                is PlayerResult.Ok -> _state.value = _state.value.copy(players = result.players, toast = "Jogador removido")
                is PlayerResult.Error -> _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    fun rateQuick(id: Int, physical: Int, tactical: Int, technical: Int) {
        viewModelScope.launch {
            when (val result = repo.rate(id, PlayerAttributes(physical, tactical, technical))) {
                is PlayerResult.Ok -> _state.value = _state.value.copy(players = result.players, toast = "Avaliação salva")
                is PlayerResult.Error -> _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    /** Só disponível editando um jogador já existente (precisa de um id real no servidor). */
    fun pickPhoto(uri: Uri) {
        val form = _state.value.form ?: return
        val playerId = form.id ?: run {
            _state.value = _state.value.copy(error = "Salve o jogador antes de adicionar foto")
            return
        }
        updateForm { it.copy(uploadingPhoto = true) }
        viewModelScope.launch {
            val resized = PhotoResizer.resizeToJpeg(getApplication(), uri)
            if (resized == null) {
                updateForm { it.copy(uploadingPhoto = false) }
                _state.value = _state.value.copy(error = "Não foi possível processar a imagem")
                return@launch
            }
            val oldUrl = _state.value.form?.photoUrl
            val url = repo.uploadPhoto(playerId, resized)
            resized.delete()
            if (url != null) {
                if (!oldUrl.isNullOrBlank() && oldUrl != url) repo.deletePhoto(oldUrl)
                updateForm { it.copy(photoUrl = url, uploadingPhoto = false) }
            } else {
                updateForm { it.copy(uploadingPhoto = false) }
                _state.value = _state.value.copy(error = "Falha no upload da foto")
            }
        }
    }

    fun removePhoto() {
        val url = _state.value.form?.photoUrl ?: return
        viewModelScope.launch { repo.deletePhoto(url) }
        updateForm { it.copy(photoUrl = null) }
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toast = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
