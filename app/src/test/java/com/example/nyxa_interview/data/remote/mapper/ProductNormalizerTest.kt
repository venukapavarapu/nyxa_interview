package com.example.nyxa_interview.data.remote.mapper

import com.example.nyxa_interview.data.remote.dto.LegacyVariantDto
import com.example.nyxa_interview.data.remote.dto.ModernVariantDto
import com.example.nyxa_interview.data.remote.dto.ProductDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The mock (and real) catalogue serves two incompatible wire shapes for a "Product" (see
 * assignment section 3.2). A bug here means the wrong price or entry count reaches checkout,
 * which is a direct financial-loss risk, so every field on both shapes gets an explicit case.
 */
class ProductNormalizerTest {

    private val normalizer = ProductNormalizer()

    @Test
    fun `modern shape maps priceCents and entries directly`() {
        val dto = ProductDto.Modern(
            id = "gid://shopify/Product/1",
            title = "RS60 Desert Runner",
            priceCents = 4500,
            entries = 22500,
            multiplier = 500,
            imageUrl = "https://example.com/a.png",
            variants = listOf(ModernVariantDto("v_1", size = "L", color = "Black", available = true)),
        )

        val result = normalizer.normalize(dto)

        assertThat(result.id).isEqualTo("gid://shopify/Product/1")
        assertThat(result.priceCents).isEqualTo(4500)
        assertThat(result.entryCount).isEqualTo(22500)
        assertThat(result.multiplier).isEqualTo(500)
        assertThat(result.variants).hasSize(1)
        assertThat(result.variants[0].label).isEqualTo("L / Black")
        assertThat(result.variants[0].available).isTrue()
    }

    @Test
    fun `legacy shape parses decimal price string into cents`() {
        val dto = ProductDto.Legacy(
            id = "gid://shopify/Product/2",
            title = "RS60 Wrangler 392 Decal",
            price = "15.00",
            entryCount = "7500",
            imageUrl = null,
            variants = listOf(LegacyVariantDto("v_9", options = mapOf("Size" to "One Size"), inStock = 1)),
        )

        val result = normalizer.normalize(dto)

        assertThat(result.priceCents).isEqualTo(1500)
        assertThat(result.entryCount).isEqualTo(7500)
        assertThat(result.multiplier).isNull()
        assertThat(result.variants[0].label).isEqualTo("One Size")
        assertThat(result.variants[0].available).isTrue()
    }

    @Test
    fun `legacy shape treats zero stock as unavailable`() {
        val dto = ProductDto.Legacy(
            id = "gid://shopify/Product/3",
            title = "Sold out item",
            price = "10.00",
            entryCount = "500",
            imageUrl = null,
            variants = listOf(LegacyVariantDto("v_1", options = mapOf("Size" to "M"), inStock = 0)),
        )

        val result = normalizer.normalize(dto)

        assertThat(result.variants[0].available).isFalse()
    }

    @Test
    fun `legacy price without decimal point is parsed as whole dollars`() {
        val dto = ProductDto.Legacy(
            id = "gid://shopify/Product/4",
            title = "Whole dollar item",
            price = "20",
            entryCount = "100",
            imageUrl = null,
            variants = emptyList(),
        )

        val result = normalizer.normalize(dto)

        assertThat(result.priceCents).isEqualTo(2000)
    }

    @Test
    fun `malformed legacy price falls back to zero instead of crashing`() {
        val dto = ProductDto.Legacy(
            id = "gid://shopify/Product/5",
            title = "Bad price item",
            price = "not-a-number",
            entryCount = "not-a-number-either",
            imageUrl = null,
            variants = emptyList(),
        )

        val result = normalizer.normalize(dto)

        assertThat(result.priceCents).isEqualTo(0)
        assertThat(result.entryCount).isEqualTo(0)
    }

    @Test
    fun `modern variant with no size or color falls back to Default label`() {
        val dto = ProductDto.Modern(
            id = "gid://shopify/Product/6",
            title = "No options item",
            priceCents = 1000,
            entries = 500,
            multiplier = null,
            imageUrl = null,
            variants = listOf(ModernVariantDto("v_1", size = null, color = null, available = true)),
        )

        val result = normalizer.normalize(dto)

        assertThat(result.variants[0].label).isEqualTo("Default")
    }
}
