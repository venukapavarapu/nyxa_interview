package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.domain.repository.GamesRepository
import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.PendingGameKind
import com.example.nyxa_interview.domain.repository.WalletRepository
import javax.inject.Inject

/**
 * Orchestrates a single spin end-to-end with a charge-once, never-lose-a-result guarantee.
 *
 * Sequence:
 * 1. Generate an idempotency key and persist it locally as "pending" *before* any network call.
 *    If the process dies here, [RecoverPendingSpinUseCase] will pick it up on next launch.
 * 2. POST /games/spin with that key. The server is expected to de-dupe on this key, so a retry
 *    of step 2 with the same key never charges twice.
 * 3. If the call fails with a network/timeout error, the outcome is unknown to this client
 *    (the server may have settled the spin and simply failed to deliver the response). We do
 *    NOT clear the pending record and surface a [SpinOutcome.Unresolved] so the caller can show
 *    a "resolving your spin" state instead of a hard failure or a re-enabled spin button.
 * 4. On success or once resolved, clear the pending record and refresh the wallet.
 */
class PerformSpinUseCase @Inject constructor(
    private val gamesRepository: GamesRepository,
    private val pendingGameActionRepository: PendingGameActionRepository,
    private val walletRepository: WalletRepository,
    private val idempotencyKeyGenerator: IdempotencyKeyGenerator,
) {
    sealed interface SpinOutcome {
        data class Resolved(val result: SpinResult) : SpinOutcome
        /** Charged on the server, but this client doesn't know the result yet (dropped connection or transient failure). */
        data class Unresolved(val idempotencyKey: String) : SpinOutcome
    }

    suspend operator fun invoke(): SpinOutcome {
        val idempotencyKey = idempotencyKeyGenerator.generate()
        pendingGameActionRepository.markPending(
            PendingGameAction(
                idempotencyKey = idempotencyKey,
                kind = PendingGameKind.SPIN,
                boxTier = null,
                createdAt = System.currentTimeMillis(),
            )
        )

        return when (val result = gamesRepository.spin(idempotencyKey)) {
            is AppResult.Success -> {
                pendingGameActionRepository.clearPending(idempotencyKey)
                walletRepository.refresh()
                SpinOutcome.Resolved(result.data)
            }

            is AppResult.Error -> SpinOutcome.Unresolved(idempotencyKey)
        }
    }
}
