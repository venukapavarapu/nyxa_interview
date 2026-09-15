package com.example.nyxa_interview.domain.model

data class Cart(
    val id: String,
    val lines: List<CartLine>,
) {
    val subtotalCents: Long get() = lines.sumOf { it.lineTotalCents }
    val totalEntries: Long get() = lines.sumOf { it.entriesEarned }
    /** Total quantity across every line, e.g. 2x Tee + 3x Decal = 5. Drives the cart badge. */
    val totalQuantity: Int get() = lines.sumOf { it.quantity }
}

data class CartLine(
    val product: Product,
    val variant: ProductVariant,
    val quantity: Int,
) {
    val lineTotalCents: Long get() = product.priceCents * quantity
    val entriesEarned: Long get() = product.entryCount * quantity
}
