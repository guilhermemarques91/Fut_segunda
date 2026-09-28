package com.futsegunda.wear.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingEventDao {
    @Insert
    suspend fun insert(event: PendingEvent): Long

    @Query("SELECT * FROM pending_events ORDER BY id ASC")
    suspend fun listAll(): List<PendingEvent>

    @Query("SELECT COUNT(*) FROM pending_events")
    fun observeCount(): Flow<Int>

    @Update
    suspend fun update(event: PendingEvent)

    @Delete
    suspend fun delete(event: PendingEvent)
}
