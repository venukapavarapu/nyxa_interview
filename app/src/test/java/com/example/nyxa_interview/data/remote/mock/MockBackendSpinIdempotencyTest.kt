package com.example.nyxa_interview.data.remote.mock

import com.example.nyxa_interview.data.remote.mapper.ProductNormalizer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Verifies the mock server's own charge-once behavior for spins, independent of the client-side
 * use case: replaying the same idempotencyKey must never re-charge (never consume a second spin
 * credit) and must always hand back the exact result that was originally settled.
 */
class MockBackendSpinIdempotencyTest {

    private lateinit var backend: MockBackend

    @Before
    fun setUp() {
        backend = MockBackend(ProductNormalizer(), FakeNetworkConditions())
    }

    @Test
    fun `replaying the same idempotency key returns the original settled result`() = runTest {
        val key = "spin-key-1"

        val first = backend.spin(key)
        val second = backend.spin(key)

        assertThat(second.resultId).isEqualTo(first.resultId)
        assertThat(second.segmentIndex).isEqualTo(first.segmentIndex)
        assertThat(second.prize).isEqualTo(first.prize)
    }

    @Test
    fun `replaying the same key does not consume an extra spin credit`() = runTest {
        val (_, creditsBefore, _) = backend.getWallet()
        val key = "spin-key-2"

        backend.spin(key)
        backend.spin(key)
        backend.spin(key)

        val (_, creditsAfter, _) = backend.getWallet()

        // A single settlement decrements credits by exactly one, regardless of how many times
        // the client replays the same idempotency key.
        assertThat(creditsBefore - creditsAfter).isEqualTo(1)
    }

    @Test
    fun `a spin result can be recovered by resultId after settlement`() = runTest {
        val key = "spin-key-3"
        val settled = backend.spin(key)

        val recovered = backend.getSpinResult(settled.resultId)

        assertThat(recovered).isEqualTo(settled)
    }

    @Test
    fun `findSpinByIdempotencyKey returns null when no spin has settled for that key yet`() = runTest {
        val found = backend.findSpinByIdempotencyKey("never-used-key")

        assertThat(found).isNull()
    }

    @Test
    fun `a dropped connection still settles the spin server-side so recovery finds it`() = runTest {
        val fakeConditions = FakeNetworkConditions()
        val dropBackend = MockBackend(ProductNormalizer(), fakeConditions)
        fakeConditions.forceDropNextSpin = true
        val key = "drop-key"

        var caught: Exception? = null
        try {
            dropBackend.spin(key)
        } catch (e: MockApiException.DroppedConnection) {
            caught = e
        }

        assertThat(caught).isNotNull()
        val recovered = dropBackend.findSpinByIdempotencyKey(key)
        assertThat(recovered).isNotNull()
    }
}
