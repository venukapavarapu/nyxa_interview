package com.example.nyxa_interview.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Covers [Cart.totalQuantity], which drives the cart badge shown on Store and Product detail. */
class CartTest {

    private val product = Product(
        id = "p1",
        title = "Redline Tee",
        priceCents = 2000,
        entryCount = 100,
        multiplier = null,
        imageUrl = null,
        variants = listOf(ProductVariant("v1", "M", available = true)),
    )
    private val variant = product.variants.first()

    @Test
    fun `totalQuantity sums quantity across every line`() {
        val cart = Cart(
            id = "cart1",
            lines = listOf(
                CartLine(product, variant, quantity = 2),
                CartLine(product.copy(id = "p2"), variant, quantity = 3),
            ),
        )

        assertThat(cart.totalQuantity).isEqualTo(5)
    }

    @Test
    fun `totalQuantity is zero for an empty cart`() {
        val cart = Cart(id = "cart1", lines = emptyList())

        assertThat(cart.totalQuantity).isEqualTo(0)
    }
}
