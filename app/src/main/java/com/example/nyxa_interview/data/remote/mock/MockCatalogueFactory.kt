package com.example.nyxa_interview.data.remote.mock

import com.example.nyxa_interview.data.remote.dto.LegacyVariantDto
import com.example.nyxa_interview.data.remote.dto.ModernVariantDto
import com.example.nyxa_interview.data.remote.dto.ProductDto

/**
 * Generates a fixed 1,200-item catalogue mixing both wire shapes (see [ProductDto]), matching
 * the real backend's inconsistency described in the assignment brief.
 */
object MockCatalogueFactory {

    const val TOTAL_ITEMS = 1200

    private val titles = listOf(
        "RS60 Desert Runner", "RS60 Wrangler 392 Decal", "Redline Tee", "Redline Hoodie",
        "Carbon Fiber Wrap Kit", "Ceramic Coating Spray", "RS60 Trucker Cap", "Pit Crew Jacket",
        "Garage Mat", "Microfiber Towel Set", "RS60 Keychain", "Redline Tumbler",
    )

    fun generate(): List<ProductDto> = (0 until TOTAL_ITEMS).map { index ->
        val title = "${titles[index % titles.size]} #${index + 1}"
        val useModernShape = index % 2 == 0
        val basePriceCents = 1500L + (index % 40) * 250L
        val multiplier = if (index % 5 == 0) 500 else if (index % 3 == 0) 100 else null

        if (useModernShape) {
            ProductDto.Modern(
                id = "gid://shopify/Product/$index",
                title = title,
                priceCents = basePriceCents,
                entries = basePriceCents * (multiplier ?: 5) / 100,
                multiplier = multiplier,
                imageUrl = "https://picsum.photos/seed/rs60-$index/600/600",
                variants = listOf(
                    ModernVariantDto("v_${index}_1", size = "M", color = "Black", available = true),
                    ModernVariantDto("v_${index}_2", size = "L", color = "Black", available = index % 7 != 0),
                    ModernVariantDto("v_${index}_3", size = "XL", color = "Red", available = true),
                ),
            )
        } else {
            ProductDto.Legacy(
                id = "gid://shopify/Product/$index",
                title = title,
                price = "%.2f".format(basePriceCents / 100.0),
                entryCount = (basePriceCents * (multiplier ?: 5) / 100).toString(),
                imageUrl = "https://picsum.photos/seed/rs60-$index/600/600",
                variants = listOf(
                    LegacyVariantDto("v_${index}_1", options = mapOf("Size" to "One Size"), inStock = 1),
                ),
            )
        }
    }
}
