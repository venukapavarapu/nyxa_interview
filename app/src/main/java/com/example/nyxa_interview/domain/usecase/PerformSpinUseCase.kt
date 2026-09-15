package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.domain.repository.GamesRepository
import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.PendingGameKind
import com.example.nyxa_interview.domain.repository.WalletRepository
import javax.inject.Inject

/**
 * Orchestrates a single spin end-to-end with a charge-once, never-lose-a-result guarantee
 * *within the current app session* (the pending record lives in memory only — see
 * `PendingGameActionRepository` — so it does not survive a real process kill).
 *
 * Sequence:
 * 1. Generate an idempotency key and record it as "pending" *before* any network call fires.
 * 2. POST /games/spin with that key. The server is expected to de-dupe on this key, so a retry
 *    of step 2 with the same key never charges twice.
 * 3. If the call fails with a network/timeout error, the outcome is unknown to this client
 *    (the server may have settled the spin and simply failed to deliver the response). We do
 *    NOT clear the pending record and surface a [SpinOutcome.Unresolved] so the caller can keep
 *    the spin control disabled and poll for the real result instead of failing or re-enabling it.
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
        /**
         * Rejected by the server *before* settlement (e.g. no spin credits left). Unlike
         * [Unresolved], there is nothing to recover — the server never charged anything, so the
         * pending record is cleared immediately instead of polling a result that will never exist.
         */
        data class Rejected(val error: AppError) : SpinOutcome
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

            is AppResult.Error -> {
                if (result.error == AppError.InsufficientCredits) {
                    pendingGameActionRepository.clearPending(idempotencyKey)
                    SpinOutcome.Rejected(result.error)
                } else {
                    SpinOutcome.Unresolved(idempotencyKey)
                }
            }
        }
    }
}
