package com.example.nyxa_interview.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_game_actions")
data class PendingGameActionEntity(
    @PrimaryKey val idempotencyKey: String,
    val kind: String,
    val boxTier: String?,
    val createdAt: Long,
)
