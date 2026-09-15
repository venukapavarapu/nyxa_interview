package com.example.nyxa_interview.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nyxa_interview.data.local.entity.PendingGameActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingGameActionDao {

    @Query("SELECT * FROM pending_game_actions ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingGameActionEntity>>

    @Query("SELECT * FROM pending_game_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingGameActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingGameActionEntity)

    @Query("DELETE FROM pending_game_actions WHERE idempotencyKey = :idempotencyKey")
    suspend fun deleteByKey(idempotencyKey: String)
}
