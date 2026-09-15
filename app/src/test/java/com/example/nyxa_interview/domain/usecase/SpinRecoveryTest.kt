package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.Prize
import com.example.nyxa_interview.domain.model.PrizeType
import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.domain.repository.GamesRepository
import com.example.nyxa_interview.domain.repository.PendingGameAction
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.PendingGameKind
import com.example.nyxa_interview.domain.repository.WalletRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Covers the exact scenario section 4.2 calls "dropped connection handling": the server settles
 * a spin and charges the user, but the response never reaches the client. This is the highest
 * financial-loss-risk path in the app — a bug here means either a double charge (spinning
 * again on retry) or a lost prize (the user never sees what they paid for).
 */
class SpinRecoveryTest {

    private val gamesRepository: GamesRepository = mockk()
    private val pendingGameActionRepository: PendingGameActionRepository = mockk(relaxUnitFun = true)
    private val walletRepository: WalletRepository = mockk(relaxUnitFun = true)
    private val idempotencyKeyGenerator: IdempotencyKeyGenerator = mockk()

    private lateinit var performSpin: PerformSpinUseCase
    private lateinit var recoverPendingGames: RecoverPendingGamesUseCase

    private val fixedKey = "fixed-idempotency-key"

    @Before
    fun setUp() {
        every { idempotencyKeyGenerator.generate() } returns fixedKey
        coEvery { walletRepository.refresh() } returns AppResult.Success(Unit)

        performSpin = PerformSpinUseCase(gamesRepository, pendingGameActionRepository, walletRepository, idempotencyKeyGenerator)
        recoverPendingGames = RecoverPendingGamesUseCase(gamesRepository, pendingGameActionRepository, walletRepository)
    }

    @Test
    fun `a dropped connection records a pending action before failing, and never clears it`() = runTest {
        coEvery { gamesRepository.spin(fixedKey) } returns AppResult.Error(AppError.Timeout)

        val outcome = performSpin()

        assertThat(outcome).isInstanceOf(PerformSpinUseCase.SpinOutcome.Unresolved::class.java)
        coVerify(exactly = 1) {
            pendingGameActionRepository.markPending(
                match { it.idempotencyKey == fixedKey && it.kind == PendingGameKind.SPIN && it.boxTier == null }
            )
        }
        coVerify(exactly = 0) { pendingGameActionRepository.clearPending(fixedKey) }
    }

    @Test
    fun `recovery resolves a pending spin once the server has the result, and clears the pending record`() = runTest {
        val result = sampleSpinResult()
        coEvery { pendingGameActionRepository.getPending() } returns listOf(
            PendingGameAction(fixedKey, PendingGameKind.SPIN, boxTier = null, createdAt = 0L)
        )
        coEvery { gamesRepository.findSpinByIdempotencyKey(fixedKey) } returns AppResult.Success(result)

        val recovered = recoverPendingGames()

        assertThat(recovered).hasSize(1)
        val spinRecovery = recovered.single() as RecoveredGame.Spin
        assertThat(spinRecovery.result).isEqualTo(result)
        coVerify(exactly = 1) { pendingGameActionRepository.clearPending(fixedKey) }
        coVerify(exactly = 1) { walletRepository.refresh() }
    }

    @Test
    fun `recovery leaves the pending record intact when the server still has no result yet`() = runTest {
        coEvery { pendingGameActionRepository.getPending() } returns listOf(
            PendingGameAction(fixedKey, PendingGameKind.SPIN, boxTier = null, createdAt = 0L)
        )
        coEvery { gamesRepository.findSpinByIdempotencyKey(fixedKey) } returns AppResult.Success(null)

        val recovered = recoverPendingGames()

        assertThat(recovered.single()).isInstanceOf(RecoveredGame.StillUnknown::class.java)
        coVerify(exactly = 0) { pendingGameActionRepository.clearPending(fixedKey) }
        coVerify(exactly = 0) { walletRepository.refresh() }
    }

    @Test
    fun `recovery treats a transient lookup failure as still-unknown rather than losing the pending record`() = runTest {
        coEvery { pendingGameActionRepository.getPending() } returns listOf(
            PendingGameAction(fixedKey, PendingGameKind.SPIN, boxTier = null, createdAt = 0L)
        )
        coEvery { gamesRepository.findSpinByIdempotencyKey(fixedKey) } returns AppResult.Error(AppError.Network)

        val recovered = recoverPendingGames()

        assertThat(recovered.single()).isInstanceOf(RecoveredGame.StillUnknown::class.java)
        coVerify(exactly = 0) { pendingGameActionRepository.clearPending(fixedKey) }
    }

    @Test
    fun `a spin rejected for insufficient credits is not treated as unresolved and never triggers recovery polling`() = runTest {
        coEvery { gamesRepository.spin(fixedKey) } returns AppResult.Error(AppError.InsufficientCredits)

        val outcome = performSpin()

        assertThat(outcome).isEqualTo(PerformSpinUseCase.SpinOutcome.Rejected(AppError.InsufficientCredits))
        // Nothing was ever charged server-side, so the pending record is cleared immediately —
        // there is no result to recover, and polling for one would spin forever for nothing.
        coVerify(exactly = 1) { pendingGameActionRepository.clearPending(fixedKey) }
        coVerify(exactly = 0) { walletRepository.refresh() }
    }

    @Test
    fun `a successful spin on first try clears any pending record and never needs recovery`() = runTest {
        val result = sampleSpinResult()
        coEvery { gamesRepository.spin(fixedKey) } returns AppResult.Success(result)

        val outcome = performSpin()

        assertThat(outcome).isEqualTo(PerformSpinUseCase.SpinOutcome.Resolved(result))
        coVerify(exactly = 1) { pendingGameActionRepository.clearPending(fixedKey) }
        coVerify(exactly = 1) { walletRepository.refresh() }
    }

    private fun sampleSpinResult() = SpinResult(
        resultId = "sr_881",
        segmentIndex = 3,
        segments = listOf("$250K", "$100K", "$10K", "$5", "VIP Merch", "$5", "$10K", "$5"),
        prize = Prize(PrizeType.CASH, 500, "$5"),
        serverSeed = "seed",
        clientSeedEcho = fixedKey,
        settledAt = Instant.parse("2026-09-15T10:00:00Z"),
    )
}
