package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.domain.repository.GamesRepository
import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.PendingGameKind
import com.example.nyxa_interview.domain.repository.WalletRepository
import javax.inject.Inject

/** Mirrors [PerformSpinUseCase]'s charge-once discipline for mystery box openings. */
class OpenBoxUseCase @Inject constructor(
    private val gamesRepository: GamesRepository,
    private val pendingGameActionRepository: PendingGameActionRepository,
    private val walletRepository: WalletRepository,
    private val idempotencyKeyGenerator: IdempotencyKeyGenerator,
) {
    sealed interface BoxOutcome {
        data class Resolved(val result: BoxResult) : BoxOutcome
        data class Unresolved(val idempotencyKey: String) : BoxOutcome
    }

    suspend operator fun invoke(tier: BoxTier): BoxOutcome {
        val idempotencyKey = idempotencyKeyGenerator.generate()
        pendingGameActionRepository.markPending(
            PendingGameAction(
                idempotencyKey = idempotencyKey,
                kind = PendingGameKind.BOX,
                boxTier = tier,
                createdAt = System.currentTimeMillis(),
            )
        )

        return when (val result = gamesRepository.openBox(tier, idempotencyKey)) {
            is AppResult.Success -> {
                pendingGameActionRepository.clearPending(idempotencyKey)
                walletRepository.refresh()
                BoxOutcome.Resolved(result.data)
            }

            is AppResult.Error -> BoxOutcome.Unresolved(idempotencyKey)
        }
    }
}
