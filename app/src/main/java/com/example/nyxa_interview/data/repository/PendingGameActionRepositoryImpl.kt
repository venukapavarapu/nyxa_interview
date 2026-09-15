package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.data.local.dao.PendingGameActionDao
import com.example.nyxa_interview.data.local.entity.PendingGameActionEntity
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.PendingGameKind
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingGameActionRepositoryImpl @Inject constructor(
    private val dao: PendingGameActionDao,
) : PendingGameActionRepository {

    override fun observePending() = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun markPending(action: PendingGameAction) {
        dao.upsert(action.toEntity())
    }

    override suspend fun clearPending(idempotencyKey: String) {
        dao.deleteByKey(idempotencyKey)
    }

    override suspend fun getPending(): List<PendingGameAction> = dao.getAll().map { it.toDomain() }

    private fun PendingGameActionEntity.toDomain() = PendingGameAction(
        idempotencyKey = idempotencyKey,
        kind = PendingGameKind.valueOf(kind),
        boxTier = boxTier?.let { BoxTier.valueOf(it) },
        createdAt = createdAt,
    )

    private fun PendingGameAction.toEntity() = PendingGameActionEntity(
        idempotencyKey = idempotencyKey,
        kind = kind.name,
        boxTier = boxTier?.name,
        createdAt = createdAt,
    )
}
