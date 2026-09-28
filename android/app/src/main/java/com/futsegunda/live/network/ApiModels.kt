package com.futsegunda.live.network

import kotlinx.serialization.Serializable

/**
 * DTOs do backend PHP existente (api/api.php). Os nomes dos campos seguem
 * exatamente o que o frontend/index.html já envia/recebe hoje — ver
 * `_pushLiveState`, `startPartida()` e as actions `public`/`login` em api.php.
 */

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val role: String? = null,
    val username: String? = null,
    val error: String? = null,
)

@Serializable
data class ValidateResponse(val ok: Boolean = false, val role: String? = null, val username: String? = null)

@Serializable
data class PlayerAttributes(
    val physical: Int = 60,
    val tactical: Int = 60,
    val technical: Int = 60,
)

@Serializable
data class PlayerPayment(
    val type: String = "",
    val amount: Double = 0.0,
    val date: String = "",
)

/**
 * Espelha o objeto `player` do painel web exatamente (mesmos nomes de campo —
 * frontend/index.html:2149, `savePlayer()`) — os dois lados leem/escrevem a
 * mesma sub-chave `players[]` do JSON, então o formato precisa bater.
 */
@Serializable
data class PlayerDto(
    val id: Int,
    val name: String,
    val apelido: String? = null,
    val whatsapp: String? = null,
    val position: String? = null,
    val attributes: PlayerAttributes = PlayerAttributes(),
    val overall: Int? = null,
    val isRegular: Boolean = true,
    val isIsento: Boolean = false,
    val monthlyFee: Double? = null,
    val balance: Double = 0.0,
    val payments: List<PlayerPayment> = emptyList(),
    val dinnerDebt: Double = 0.0,
    val lastRating: String? = null,
    val photo: String? = null,
    val video: String? = null,
)

/** Corpo de player_save — igual a PlayerDto, mas `id` nulo = criar (servidor calcula o id). */
@Serializable
data class PlayerDraftDto(
    val id: Int? = null,
    val name: String,
    val apelido: String? = null,
    val whatsapp: String? = null,
    val position: String? = null,
    val attributes: PlayerAttributes = PlayerAttributes(),
    val overall: Int? = null,
    val isRegular: Boolean = true,
    val isIsento: Boolean = false,
    val monthlyFee: Double? = null,
    val photo: String? = null,
    val video: String? = null,
)

@Serializable
data class GoalLogEntry(
    val clientEventId: String? = null,
    val scorerId: Int? = null,
    val assistId: Int? = null,
    val team: String,
    val ownGoal: Boolean = false,
    val minute: Int? = null,
    val source: String? = null,
    val at: Long? = null,
)

@Serializable
data class LiveStateDto(
    val active: Boolean = false,
    val date: String? = null,
    val homePlayers: List<Int> = emptyList(),
    val awayPlayers: List<Int> = emptyList(),
    val homeReserveId: Int? = null,
    val awayReserveId: Int? = null,
    val goalLog: List<GoalLogEntry> = emptyList(),
    val periodo: Int? = null,
    val t1ms: Long? = null,
    val timerRunning: Boolean = false,
    val timerElapsed: Long = 0,
    val timerStartedAt: Long? = null,
    val intervaloStart: Long? = null,
    val intervaloMs: Long? = null,
    val t2ended: Boolean = false,
)

@Serializable
data class PublicStateResponse(
    val players: List<PlayerDto> = emptyList(),
    val liveState: LiveStateDto? = null,
)

@Serializable
data class GoalAddRequest(
    val clientEventId: String,
    val team: String,
    val scorerId: Int? = null,
    val assistId: Int? = null,
    val ownGoal: Boolean = false,
    val minute: Int? = null,
    val source: String = "android",
    val at: Long? = null,
)

@Serializable
data class GoalUndoRequest(
    val clientEventId: String,
    val team: String,
)

@Serializable
data class PeriodEventRequest(
    val clientEventId: String,
    val event: String, // start_t1 | end_t1 | start_t2 | end_t2 | reset
    val at: Long? = null,
)

@Serializable
data class LiveEventResponse(
    val ok: Boolean = false,
    val duplicate: Boolean = false,
    val error: String? = null,
)

/** Build publicada pelo painel web (aba Config → "Apps — Builds para Download"). */
@Serializable
data class AppReleaseInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String? = null,
    val fileName: String? = null,
    val uploadedAt: String? = null,
)

@Serializable
data class AppReleasesResponse(
    val android: AppReleaseInfo? = null,
    val wear: AppReleaseInfo? = null,
)

@Serializable
data class FeesDto(
    val mensal: Double = 150.0,
    val avulso: Double = 50.0,
)

/** Resumo de uma partida — o bastante pro Dashboard/Histórico; cresce nas próximas fases. */
@Serializable
data class ResultDto(
    val id: Int? = null,
    val date: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val pending: Boolean = false,
    val motm: Int? = null,
)

/**
 * Espelha o GET sem `action` (api.php, o mesmo que `loadFromServer()` do
 * painel web lê) — cresce a cada fase conforme mais telas precisarem de
 * mais sub-chaves do blob. Leitura não tem risco de concorrência (só a
 * escrita virou granular), então continua sendo "traga tudo de uma vez".
 */
@Serializable
data class AppSnapshotDto(
    val players: List<PlayerDto> = emptyList(),
    val fees: FeesDto = FeesDto(),
    val results: List<ResultDto> = emptyList(),
)

@Serializable
data class PlayerSaveRequest(
    val clientEventId: String,
    val player: PlayerDraftDto,
)

@Serializable
data class PlayerSaveResponse(
    val ok: Boolean = false,
    val duplicate: Boolean = false,
    val error: String? = null,
    val players: List<PlayerDto> = emptyList(),
)

@Serializable
data class PlayerDeleteRequest(val id: Int)

@Serializable
data class PlayerDeleteResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val players: List<PlayerDto> = emptyList(),
)

@Serializable
data class PlayerRateRequest(
    val clientEventId: String,
    val id: Int,
    val attributes: PlayerAttributes,
)

@Serializable
data class DeletePhotoRequest(val url: String)

@Serializable
data class UploadPhotoResponse(
    val ok: Boolean = false,
    val url: String? = null,
    val error: String? = null,
)

@Serializable
data class FeesSaveRequest(val fees: FeesDto)

@Serializable
data class FeesSaveResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val fees: FeesDto = FeesDto(),
)
