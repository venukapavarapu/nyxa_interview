package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.domain.repository.GamesRepository
import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.PendingGameKind
import com.example.nyxa_interview.domain.repository.WalletRepository
import javax.inject.Inject

sealed interface RecoveredGame {
    data class Spin(val action: PendingGameAction, val result: SpinResult) : RecoveredGame
    data class Box(val action: PendingGameAction, val result: BoxResult) : RecoveredGame
    /** Server has no record yet (still 15% failing / mid-flight); keep the pending record. */
    data class StillUnknown(val action: PendingGameAction) : RecoveredGame
}

/**
 * Resolves every locally-recorded pending game action by asking the server "what happened to
 * the spin/box I paid for with this idempotency key". Called on cold launch and whenever a
 * spin/box request itself fails, so the user is never shown a stuck "spinning" state and never
 * loses a result they were charged for.
 */
class RecoverPendingGamesUseCase @Inject constructor(
    private val gamesRepository: GamesRepository,
    private val pendingGameActionRepository: PendingGameActionRepository,
    private val walletRepository: WalletRepository,
) {
    suspend operator fun invoke(): List<RecoveredGame> {
        val pending = pendingGameActionRepository.getPending()
        return pending.map { action -> recover(action) }
    }

    private suspend fun recover(action: PendingGameAction): RecoveredGame {
        return when (action.kind) {
            PendingGameKind.SPIN -> when (val found = gamesRepository.findSpinByIdempotencyKey(action.idempotencyKey)) {
                is AppResult.Success -> {
                    val spinResult = found.data
                    if (spinResult != null) {
                        pendingGameActionRepository.clearPending(action.idempotencyKey)
                        walletRepository.refresh()
                        RecoveredGame.Spin(action, spinResult)
                    } else {
                        RecoveredGame.StillUnknown(action)
                    }
                }

                is AppResult.Error -> RecoveredGame.StillUnknown(action)
            }

            PendingGameKind.BOX -> when (val found = gamesRepository.findBoxByIdempotencyKey(action.idempotencyKey)) {
                is AppResult.Success -> {
                    val boxResult = found.data
                    if (boxResult != null) {
                        pendingGameActionRepository.clearPending(action.idempotencyKey)
                        walletRepository.refresh()
                        RecoveredGame.Box(action, boxResult)
                    } else {
                        RecoveredGame.StillUnknown(action)
                    }
                }

                is AppResult.Error -> RecoveredGame.StillUnknown(action)
            }
        }
    }
}
