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
data class PlayerDto(
    val id: Int,
    val name: String,
    val position: String? = null,
    val overall: Int? = null,
    val photo: String? = null,
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
