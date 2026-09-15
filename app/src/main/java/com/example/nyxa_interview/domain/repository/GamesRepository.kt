package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.domain.model.SpinResult

interface GamesRepository {
    /**
     * Requests a spin using [idempotencyKey]. If the server settles the spin but the response
     * never reaches the client (dropped connection), this call fails with a network/timeout
     * error even though the user has been charged. The caller is responsible for persisting
     * the pending [idempotencyKey] *before* calling this, and resolving it afterwards via
     * [recoverSpin] or [findSpinByIdempotencyKey].
     */
    suspend fun spin(idempotencyKey: String): AppResult<SpinResult>

    suspend fun recoverSpin(resultId: String): AppResult<SpinResult>

    /** Looks up a previously-submitted spin by the key the client used to request it. */
    suspend fun findSpinByIdempotencyKey(idempotencyKey: String): AppResult<SpinResult?>

    suspend fun openBox(tier: BoxTier, idempotencyKey: String): AppResult<BoxResult>

    suspend fun findBoxByIdempotencyKey(idempotencyKey: String): AppResult<BoxResult?>
}
