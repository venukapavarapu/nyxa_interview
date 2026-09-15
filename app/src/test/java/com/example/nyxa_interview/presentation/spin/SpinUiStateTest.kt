package com.example.nyxa_interview.presentation.spin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression coverage: [SpinUiState.isSpinEnabled] must gate on spin credits, not just phase.
 * Before this fix, a user with 0 credits could keep tapping Spin — the button showed a loading
 * spinner as if disabled, but the tap still fired a request, and the mock backend used to accept
 * and settle it for free instead of rejecting it (see MockBackendSpinCreditsTest).
 */
class SpinUiStateTest {

    @Test
    fun `spin is disabled when idle but out of credits`() {
        val state = SpinUiState(phase = SpinPhase.IDLE, spinCredits = 0)

        assertThat(state.isSpinEnabled).isFalse()
    }

    @Test
    fun `spin is enabled when idle with credits remaining`() {
        val state = SpinUiState(phase = SpinPhase.IDLE, spinCredits = 3)

        assertThat(state.isSpinEnabled).isTrue()
    }

    @Test
    fun `spin is disabled mid-flight even with credits remaining`() {
        val state = SpinUiState(phase = SpinPhase.ANIMATING, spinCredits = 3)

        assertThat(state.isSpinEnabled).isFalse()
    }
}
