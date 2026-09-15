package com.example.nyxa_interview.data.remote.mock

import com.example.nyxa_interview.data.remote.mapper.ProductNormalizer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Covers the cart-page increase/decrease flow: adding the same variant twice merges into one
 * line (already covered informally elsewhere), and updating a line's quantity directly sets it
 * rather than adding to it, with a quantity of zero removing the line entirely.
 */
class MockBackendCartQuantityTest {

    private lateinit var backend: MockBackend

    @Before
    fun setUp() {
        backend = MockBackend(ProductNormalizer(), FakeNetworkConditions())
    }

    @Test
    fun `adding the same variant twice merges into a single line with summed quantity`() = runTest {
        val variantId = firstVariantId()

        backend.addCartLine(variantId, quantity = 2)
        val cart = backend.addCartLine(variantId, quantity = 3)

        assertThat(cart.lines).hasSize(1)
        assertThat(cart.lines.single().quantity).isEqualTo(5)
    }

    @Test
    fun `updateCartLineQuantity sets an exact quantity rather than adding to it`() = runTest {
        val variantId = firstVariantId()
        backend.addCartLine(variantId, quantity = 2)

        val cart = backend.updateCartLineQuantity(variantId, quantity = 7)

        assertThat(cart.lines.single().quantity).isEqualTo(7)
    }

    @Test
    fun `updateCartLineQuantity to zero removes the line`() = runTest {
        val variantId = firstVariantId()
        backend.addCartLine(variantId, quantity = 2)

        val cart = backend.updateCartLineQuantity(variantId, quantity = 0)

        assertThat(cart.lines).isEmpty()
    }

    @Test
    fun `updateCartLineQuantity to a negative value also removes the line`() = runTest {
        val variantId = firstVariantId()
        backend.addCartLine(variantId, quantity = 2)

        val cart = backend.updateCartLineQuantity(variantId, quantity = -1)

        assertThat(cart.lines).isEmpty()
    }

    @Test
    fun `updateCartLineQuantity on a variant not in the cart throws NotFound`() = runTest {
        backend.addCartLine(firstVariantId(), quantity = 1)

        var caught: Exception? = null
        try {
            backend.updateCartLineQuantity("never-added-variant", quantity = 1)
        } catch (e: MockApiException.NotFound) {
            caught = e
        }

        assertThat(caught).isNotNull()
    }

    private suspend fun firstVariantId(): String {
        val (products, _) = backend.getProducts(cursor = null, pageSize = 1)
        return products.first().variants.first().id
    }
}
