package com.example.nyxa_interview.data.remote.dto

/**
 * Union of the two catalogue wire shapes the mock backend serves, reflecting the real
 * (inconsistent) backend. "Modern" items use priceCents/entries/multiplier and typed variants;
 * "legacy" items use string price/entryCount and a variants shape keyed by free-form options.
 * [ProductNormalizer] is the single place that resolves this into [domain.model.Product].
 */
sealed interface ProductDto {
    val id: String
    val title: String

    data class Modern(
        override val id: String,
        override val title: String,
        val priceCents: Long,
        val entries: Long,
        val multiplier: Int?,
        val imageUrl: String?,
        val variants: List<ModernVariantDto>,
    ) : ProductDto

    data class Legacy(
        override val id: String,
        override val title: String,
        val price: String,
        val entryCount: String,
        val imageUrl: String?,
        val variants: List<LegacyVariantDto>,
    ) : ProductDto
}

data class ModernVariantDto(
    val id: String,
    val size: String?,
    val color: String?,
    val available: Boolean,
)

data class LegacyVariantDto(
    val id: String,
    val options: Map<String, String>,
    val inStock: Int,
)
