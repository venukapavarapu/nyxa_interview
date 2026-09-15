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
 * In-memory record of "I asked the server to charge me for a spin/box and have not yet confirmed
 * the outcome", written *before* the network call fires so a dropped connection leaves a trail
 * that can be resolved within the same app session. Not persisted to disk (see DECISIONS.md), so
 * this record does not survive a real process kill — only the auth token does.
 */
interface PendingGameActionRepository {
    fun observePending(): Flow<List<PendingGameAction>>
    suspend fun markPending(action: PendingGameAction)
    suspend fun clearPending(idempotencyKey: String)
    suspend fun getPending(): List<PendingGameAction>
}
