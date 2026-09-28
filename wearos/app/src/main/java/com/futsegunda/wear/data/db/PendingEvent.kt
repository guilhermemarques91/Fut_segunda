package com.futsegunda.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fila offline do relógio: cada toque vira uma linha com um clientEventId
 * único. O relógio pode ficar sem WiFi/LTE durante o jogo inteiro — a fila
 * garante que nada se perde e nada duplica quando a conexão voltar.
 */
@Entity(tableName = "pending_events")
data class PendingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientEventId: String,
    val type: String, // "goal_add" | "goal_undo" | "period"
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
