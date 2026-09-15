package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Held purely in memory, keyed by idempotencyKey — nothing is written to disk besides the auth
 * token. Recovery of a dropped-connection spin/box still works within the current app session
 * (the scenario shown in the demo recording), but not after a real process kill, since there is
 * no on-disk record to resume from on next launch. A deliberate scope cut given the assignment's
 * time limit (see DECISIONS.md).
 */
@Singleton
class PendingGameActionRepositoryImpl @Inject constructor() : PendingGameActionRepository {

    private val mutex = Mutex()
    private val pendingState = MutableStateFlow<List<PendingGameAction>>(emptyList())

    override fun observePending() = pendingState.asStateFlow()

    override suspend fun markPending(action: PendingGameAction) = mutex.withLock {
        pendingState.value = pendingState.value.filterNot { it.idempotencyKey == action.idempotencyKey } + action
    }

    override suspend fun clearPending(idempotencyKey: String) = mutex.withLock {
        pendingState.value = pendingState.value.filterNot { it.idempotencyKey == idempotencyKey }
    }

    override suspend fun getPending(): List<PendingGameAction> = pendingState.value
}
