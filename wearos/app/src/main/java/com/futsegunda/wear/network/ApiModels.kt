package com.futsegunda.wear.network

import kotlinx.serialization.Serializable

/**
 * Mesmos DTOs do app Android (android/.../network/ApiModels.kt) — os dois
 * apps falam com o mesmo backend (api/api.php), então os campos precisam
 * bater exatamente com o que o servidor espera/devolve.
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
data class PlayerDto(
    val id: Int,
    val name: String,
)

@Serializable
data class GoalLogEntry(
    val clientEventId: String? = null,
    val scorerId: Int? = null,
    val assistId: Int? = null,
    val team: String,
    val ownGoal: Boolean = false,
)

@Serializable
data class LiveStateDto(
    val active: Boolean = false,
    val homePlayers: List<Int> = emptyList(),
    val awayPlayers: List<Int> = emptyList(),
    val goalLog: List<GoalLogEntry> = emptyList(),
    val periodo: Int? = null,
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
    val ownGoal: Boolean = false,
    val source: String = "watch",
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
)

@Serializable
data class AppReleasesResponse(
    val android: AppReleaseInfo? = null,
    val wear: AppReleaseInfo? = null,
)
