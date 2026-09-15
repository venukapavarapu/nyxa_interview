package com.example.nyxa_interview.presentation.spin

import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.data.remote.mock.MockNetworkConditions
import com.example.nyxa_interview.domain.usecase.PerformSpinUseCase
import com.example.nyxa_interview.domain.usecase.RecoverPendingGamesUseCase
import com.example.nyxa_interview.domain.usecase.RecoveredGame
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Spin to Win flow end to end. The golden rule enforced here: the client never
 * decides the outcome. [PerformSpinUseCase] talks to the server first; only once a [SpinResult]
 * comes back (either immediately, or later via recovery) does this ViewModel emit
 * [SpinEffect.AnimateToSegment], and the wheel animates *to* that already-known result rather
 * than generating its own.
 */
@HiltViewModel
class SpinViewModel @Inject constructor(
    private val performSpinUseCase: PerformSpinUseCase,
    private val recoverPendingGamesUseCase: RecoverPendingGamesUseCase,
    private val networkConditions: MockNetworkConditions,
) : MviViewModel<SpinUiState, SpinIntent, SpinEffect>(SpinUiState()) {

    init {
        recoverOnLaunch()
    }

    override fun onIntent(intent: SpinIntent) {
        when (intent) {
            SpinIntent.SpinTapped -> spin()
            SpinIntent.AnimationFinished -> settle()
            SpinIntent.DismissResult -> setState { copy(phase = SpinPhase.IDLE, lastResult = null) }
            SpinIntent.ForceDropNextSpin -> networkConditions.forceDropNextSpin = true
        }
    }

    private fun recoverOnLaunch() {
        viewModelScope.launch {
            val recovered = recoverPendingGamesUseCase()
            val spin = recovered.filterIsInstance<RecoveredGame.Spin>().firstOrNull() ?: return@launch
            setState { copy(phase = SpinPhase.ANIMATING, pendingResult = spin.result) }
            sendEffect(SpinEffect.AnimateToSegment(spin.result.segmentIndex))
        }
    }

    private fun spin() {
        if (!currentState.isSpinEnabled) return
        setState { copy(phase = SpinPhase.REQUESTING, errorMessage = null) }

        viewModelScope.launch {
            when (val outcome = performSpinUseCase()) {
                is PerformSpinUseCase.SpinOutcome.Resolved -> {
                    setState { copy(phase = SpinPhase.ANIMATING, pendingResult = outcome.result) }
                    sendEffect(SpinEffect.AnimateToSegment(outcome.result.segmentIndex))
                }

                is PerformSpinUseCase.SpinOutcome.Unresolved -> resolvePending()
            }
        }
    }

    /** Polls the recovery endpoint with backoff until the charged-but-undelivered spin resolves. */
    private suspend fun resolvePending() {
        setState { copy(phase = SpinPhase.RESOLVING) }
        var attempt = 0
        while (true) {
            val recovered = recoverPendingGamesUseCase()
            val spin = recovered.filterIsInstance<RecoveredGame.Spin>().firstOrNull()
            if (spin != null) {
                setState { copy(phase = SpinPhase.ANIMATING, pendingResult = spin.result) }
                sendEffect(SpinEffect.AnimateToSegment(spin.result.segmentIndex))
                return
            }
            attempt++
            if (attempt >= MAX_RECOVERY_ATTEMPTS) {
                setState {
                    copy(
                        phase = SpinPhase.IDLE,
                        errorMessage = "We're still confirming your last spin. Check the wallet ledger shortly.",
                    )
                }
                return
            }
            delay(RECOVERY_BACKOFF_MILLIS)
        }
    }

    private fun settle() {
        setState { copy(phase = SpinPhase.SETTLED, lastResult = pendingResult, pendingResult = null) }
    }

    private companion object {
        const val MAX_RECOVERY_ATTEMPTS = 5
        const val RECOVERY_BACKOFF_MILLIS = 800L
    }
}
