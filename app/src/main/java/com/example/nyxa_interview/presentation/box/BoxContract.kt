package com.example.nyxa_interview.presentation.box

import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

enum class BoxPhase { SELECTING, REQUESTING, RESOLVING, REVEALING, SETTLED }

data class BoxUiState(
    val selectedTier: BoxTier = BoxTier.BRONZE,
    val phase: BoxPhase = BoxPhase.SELECTING,
    val pendingResult: BoxResult? = null,
    val lastResult: BoxResult? = null,
    val errorMessage: String? = null,
) : UiState {
    val isOpenEnabled: Boolean get() = phase == BoxPhase.SELECTING
}

sealed interface BoxIntent : UiIntent {
    data class TierSelected(val tier: BoxTier) : BoxIntent
    data object OpenTapped : BoxIntent
    data object RevealFinished : BoxIntent
    data object DismissResult : BoxIntent
}

sealed interface BoxEffect : UiEffect {
    data class PlayReveal(val result: BoxResult) : BoxEffect
}
