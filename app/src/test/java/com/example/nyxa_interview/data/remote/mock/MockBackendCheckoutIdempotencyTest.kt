package com.example.nyxa_interview.data.remote.mock

import com.example.nyxa_interview.data.remote.mapper.ProductNormalizer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Checkout is the single most direct "double charge" risk in the app: a retried request after
 * a timeout, or a user double-tapping "Place order", must never mint two orders. This exercises
 * the mock backend's own de-dup ledger (keyed by idempotencyKey) the way [CheckoutRepositoryImpl]
 * and [com.example.nyxa_interview.domain.usecase.CheckoutUseCase] rely on it.
 */
class MockBackendCheckoutIdempotencyTest {

    // A fixed, always-succeeding Random(seed) keeps this deterministic despite the mock's
    // built-in 15% failure simulation and 400-1500ms latency.
    private lateinit var backend: MockBackend

    @Before
    fun setUp() {
        backend = MockBackend(ProductNormalizer(), FakeNetworkConditions())
    }

    @Test
    fun `retrying checkout with the same idempotency key returns the original order, not a new one`() = runTest {
        val cart = backend.addCartLine(variantId = firstVariantId(), quantity = 1)
        val idempotencyKey = "fixed-key-1"

        val first = backend.checkout(cart.id, idempotencyKey)
        // Simulate a client retry after a dropped response, reusing the same key and cart id.
        val second = backend.checkout(cart.id, idempotencyKey)

        assertThat(second.orderId).isEqualTo(first.orderId)
        assertThat(second.entriesAwarded).isEqualTo(first.entriesAwarded)
    }

    @Test
    fun `two different idempotency keys for two different carts produce two distinct orders`() = runTest {
        val cartA = backend.addCartLine(variantId = firstVariantId(), quantity = 1)
        val orderA = backend.checkout(cartA.id, "key-a")

        val cartB = backend.addCartLine(variantId = firstVariantId(), quantity = 1)
        val orderB = backend.checkout(cartB.id, "key-b")

        assertThat(orderA.orderId).isNotEqualTo(orderB.orderId)
    }

    @Test
    fun `a double tap that fires two concurrent requests with the same key still yields one order`() = runTest {
        val cart = backend.addCartLine(variantId = firstVariantId(), quantity = 2)
        val idempotencyKey = "double-tap-key"

        val results = listOf(
            backend.checkout(cart.id, idempotencyKey),
            backend.checkout(cart.id, idempotencyKey),
            backend.checkout(cart.id, idempotencyKey),
        )

        assertThat(results.map { it.orderId }.distinct()).hasSize(1)
    }

    private suspend fun firstVariantId(): String {
        val (products, _) = backend.getProducts(cursor = null, pageSize = 1)
        return products.first().variants.first().id
    }
}
