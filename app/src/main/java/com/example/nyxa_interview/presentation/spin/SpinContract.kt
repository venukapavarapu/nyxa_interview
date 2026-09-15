package com.example.nyxa_interview.presentation.spin

import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

enum class SpinPhase { IDLE, REQUESTING, ANIMATING, RESOLVING, SETTLED }

data class SpinUiState(
    val segments: List<String> = DEFAULT_SEGMENTS,
    val phase: SpinPhase = SpinPhase.IDLE,
    val pendingResult: SpinResult? = null,
    val lastResult: SpinResult? = null,
    val errorMessage: String? = null,
) : UiState {
    /** Disabled for the full duration of a spin, including recovery, so a double tap can never fire two spins. */
    val isSpinEnabled: Boolean get() = phase == SpinPhase.IDLE

    companion object {
        val DEFAULT_SEGMENTS = listOf("$250K", "$100K", "$10K", "$5", "VIP Merch", "$5", "$10K", "$5")
    }
}

sealed interface SpinIntent : UiIntent {
    data object SpinTapped : SpinIntent
    /** Fired once the wheel's landing animation finishes, to move a Resolved outcome to SETTLED. */
    data object AnimationFinished : SpinIntent
    data object DismissResult : SpinIntent
    /** Debug-only: forces the next spin to simulate a dropped connection. */
    data object ForceDropNextSpin : SpinIntent
}

sealed interface SpinEffect : UiEffect {
    data class AnimateToSegment(val segmentIndex: Int) : SpinEffect
}
