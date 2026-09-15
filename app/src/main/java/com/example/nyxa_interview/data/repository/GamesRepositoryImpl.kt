package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.data.remote.AuthenticatedApiGateway
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.domain.repository.GamesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamesRepositoryImpl @Inject constructor(
    private val mockBackend: MockBackend,
    private val gateway: AuthenticatedApiGateway,
) : GamesRepository {

    override suspend fun spin(idempotencyKey: String): AppResult<SpinResult> =
        gateway.call { mockBackend.spin(idempotencyKey) }

    override suspend fun recoverSpin(resultId: String): AppResult<SpinResult> =
        gateway.call { mockBackend.getSpinResult(resultId) }

    override suspend fun findSpinByIdempotencyKey(idempotencyKey: String): AppResult<SpinResult?> =
        gateway.call { mockBackend.findSpinByIdempotencyKey(idempotencyKey) }

    override suspend fun openBox(tier: BoxTier, idempotencyKey: String): AppResult<BoxResult> =
        gateway.call { mockBackend.openBox(tier, idempotencyKey) }

    override suspend fun findBoxByIdempotencyKey(idempotencyKey: String): AppResult<BoxResult?> =
        gateway.call { mockBackend.findBoxByIdempotencyKey(idempotencyKey) }
}
