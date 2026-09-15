package com.example.nyxa_interview.domain.model

/**
 * Single normalised domain shape for a catalogue item.
 * The backend serves two inconsistent wire shapes (see remote/dto + mapper);
 * this is the only shape the UI layer is ever allowed to see.
 */
data class Product(
    val id: String,
    val title: String,
    val priceCents: Long,
    val entryCount: Long,
    val multiplier: Int?,
    val imageUrl: String?,
    val variants: List<ProductVariant>,
)

data class ProductVariant(
    val id: String,
    val label: String,
    val available: Boolean,
)
