package com.futsegunda.live.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fila offline: cada toque (gol, desfazer, período) vira uma linha aqui com
 * um `clientEventId` (UUID) único. O SyncWorker tenta enviar em ordem; como
 * as actions live_goal_add/live_goal_undo/live_period no servidor são
 * idempotentes por clientEventId, reenviar depois de uma queda de conexão
 * nunca duplica o efeito.
 */
@Entity(tableName = "pending_events")
data class PendingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientEventId: String,
    val type: String, // "goal_add" | "goal_undo" | "period"
    /** Corpo já serializado em JSON, pronto para ir no POST correspondente. */
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
)
