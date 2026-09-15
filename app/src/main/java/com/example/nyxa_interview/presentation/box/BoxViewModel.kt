package com.example.nyxa_interview.presentation.box

import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.domain.usecase.OpenBoxUseCase
import com.example.nyxa_interview.domain.usecase.RecoverPendingGamesUseCase
import com.example.nyxa_interview.domain.usecase.RecoveredGame
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mirrors [com.example.nyxa_interview.presentation.spin.SpinViewModel]'s charge-once discipline for box openings. */
@HiltViewModel
class BoxViewModel @Inject constructor(
    private val openBoxUseCase: OpenBoxUseCase,
    private val recoverPendingGamesUseCase: RecoverPendingGamesUseCase,
) : MviViewModel<BoxUiState, BoxIntent, BoxEffect>(BoxUiState()) {

    init {
        recoverOnLaunch()
    }

    override fun onIntent(intent: BoxIntent) {
        when (intent) {
            is BoxIntent.TierSelected -> setState { copy(selectedTier = intent.tier) }
            BoxIntent.OpenTapped -> open()
            BoxIntent.RevealFinished -> setState { copy(phase = BoxPhase.SETTLED, lastResult = pendingResult, pendingResult = null) }
            BoxIntent.DismissResult -> setState { copy(phase = BoxPhase.SELECTING, lastResult = null) }
        }
    }

    private fun recoverOnLaunch() {
        viewModelScope.launch {
            val recovered = recoverPendingGamesUseCase()
            val box = recovered.filterIsInstance<RecoveredGame.Box>().firstOrNull() ?: return@launch
            setState { copy(phase = BoxPhase.REVEALING, pendingResult = box.result) }
            sendEffect(BoxEffect.PlayReveal(box.result))
        }
    }

    private fun open() {
        if (!currentState.isOpenEnabled) return
        setState { copy(phase = BoxPhase.REQUESTING, errorMessage = null) }

        viewModelScope.launch {
            when (val outcome = openBoxUseCase(currentState.selectedTier)) {
                is OpenBoxUseCase.BoxOutcome.Resolved -> {
                    setState { copy(phase = BoxPhase.REVEALING, pendingResult = outcome.result) }
                    sendEffect(BoxEffect.PlayReveal(outcome.result))
                }

                is OpenBoxUseCase.BoxOutcome.Unresolved -> resolvePending()
            }
        }
    }

    private suspend fun resolvePending() {
        setState { copy(phase = BoxPhase.RESOLVING) }
        var attempt = 0
        while (true) {
            val recovered = recoverPendingGamesUseCase()
            val box = recovered.filterIsInstance<RecoveredGame.Box>().firstOrNull()
            if (box != null) {
                setState { copy(phase = BoxPhase.REVEALING, pendingResult = box.result) }
                sendEffect(BoxEffect.PlayReveal(box.result))
                return
            }
            attempt++
            if (attempt >= MAX_RECOVERY_ATTEMPTS) {
                setState {
                    copy(
                        phase = BoxPhase.SELECTING,
                        errorMessage = "We're still confirming your box. Check the wallet ledger shortly.",
                    )
                }
                return
            }
            delay(RECOVERY_BACKOFF_MILLIS)
        }
    }

    private companion object {
        const val MAX_RECOVERY_ATTEMPTS = 5
        const val RECOVERY_BACKOFF_MILLIS = 800L
    }
}
