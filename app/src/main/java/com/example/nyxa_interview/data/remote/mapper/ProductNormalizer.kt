package com.example.nyxa_interview.data.remote.mapper

import com.example.nyxa_interview.data.remote.dto.LegacyVariantDto
import com.example.nyxa_interview.data.remote.dto.ModernVariantDto
import com.example.nyxa_interview.data.remote.dto.ProductDto
import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.domain.model.ProductVariant
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Normalises both catalogue wire shapes into the single [Product] domain model. This is the
 * only place in the app allowed to know that two shapes exist — everything above this layer
 * (repositories, use cases, UI) only ever sees [Product].
 */
class ProductNormalizer @Inject constructor() {

    fun normalize(dto: ProductDto): Product = when (dto) {
        is ProductDto.Modern -> Product(
            id = dto.id,
            title = dto.title,
            priceCents = dto.priceCents,
            entryCount = dto.entries,
            multiplier = dto.multiplier,
            imageUrl = dto.imageUrl,
            variants = dto.variants.map { it.toDomain() },
        )

        is ProductDto.Legacy -> Product(
            id = dto.id,
            title = dto.title,
            priceCents = parseDecimalPriceToCents(dto.price),
            entryCount = dto.entryCount.trim().toLongOrNull() ?: 0L,
            multiplier = null,
            imageUrl = dto.imageUrl,
            variants = dto.variants.map { it.toDomain() },
        )
    }

    private fun ModernVariantDto.toDomain() = ProductVariant(
        id = id,
        label = listOfNotNull(size, color).joinToString(" / ").ifBlank { "Default" },
        available = available,
    )

    private fun LegacyVariantDto.toDomain() = ProductVariant(
        id = id,
        label = options.values.joinToString(" / ").ifBlank { "Default" },
        available = inStock > 0,
    )

    /** "15.00" -> 1500. Tolerant of missing decimals ("15") and stray whitespace. */
    private fun parseDecimalPriceToCents(price: String): Long {
        val amount = price.trim().toDoubleOrNull() ?: return 0L
        return (amount * 100).roundToLong()
    }
}
