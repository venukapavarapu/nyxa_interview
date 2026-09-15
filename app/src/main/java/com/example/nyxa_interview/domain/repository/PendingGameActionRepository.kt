package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.domain.model.BoxTier
import kotlinx.coroutines.flow.Flow

enum class PendingGameKind { SPIN, BOX }

data class PendingGameAction(
    val idempotencyKey: String,
    val kind: PendingGameKind,
    val boxTier: BoxTier?,
    val createdAt: Long,
)

/**
 * Durable record of "I asked the server to charge me for a spin/box and have not yet confirmed
 * the outcome". Written to disk *before* the network call fires, so a process death or a
 * dropped connection leaves a trail that survives app restart and can be resolved on next launch.
 */
interface PendingGameActionRepository {
    fun observePending(): Flow<List<PendingGameAction>>
    suspend fun markPending(action: PendingGameAction)
    suspend fun clearPending(idempotencyKey: String)
    suspend fun getPending(): List<PendingGameAction>
}
