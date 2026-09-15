package com.example.nyxa_interview.data.remote.mock

import com.example.nyxa_interview.data.remote.mapper.ProductNormalizer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for a real bug found via manual testing: the mock backend used to settle
 * (and charge/award) a spin even with zero spin credits remaining, silently clamping the credit
 * counter at zero with `.coerceAtLeast(0)` instead of rejecting the request. That let a user keep
 * spinning — and winning — for free forever once their paid credits ran out.
 */
class MockBackendSpinCreditsTest {

    private lateinit var backend: MockBackend

    @Before
    fun setUp() {
        backend = MockBackend(ProductNormalizer(), FakeNetworkConditions())
    }

    @Test
    fun `a spin is rejected once spin credits are exhausted`() = runTest {
        // The mock seeds 3 spin credits; spend exactly that many first.
        repeat(3) { i -> backend.spin("key-$i") }
        val (_, creditsAfterThree, _) = backend.getWallet()
        assertThat(creditsAfterThree).isEqualTo(0)

        var caught: Exception? = null
        try {
            backend.spin("key-fourth")
        } catch (e: MockApiException.InsufficientSpinCredits) {
            caught = e
        }

        assertThat(caught).isNotNull()
    }

    @Test
    fun `a rejected spin never settles a result or touches the ledger`() = runTest {
        repeat(3) { i -> backend.spin("key-$i") }
        val (_, _, ledgerBefore) = backend.getWallet()

        try {
            backend.spin("key-fourth")
        } catch (e: MockApiException.InsufficientSpinCredits) {
            // expected
        }

        val (_, creditsAfter, ledgerAfter) = backend.getWallet()
        assertThat(creditsAfter).isEqualTo(0)
        assertThat(ledgerAfter).hasSize(ledgerBefore.size)
        assertThat(backend.findSpinByIdempotencyKey("key-fourth")).isNull()
    }
}
