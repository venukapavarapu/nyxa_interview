package com.example.nyxa_interview.presentation.store.detail

import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

data class ProductDetailUiState(
    val product: Product? = null,
    val selectedVariantId: String? = null,
    val quantity: Int = 1,
    val isAddingToCart: Boolean = false,
    val errorMessage: String? = null,
) : UiState {
    val canAddToCart: Boolean
        get() = product != null &&
            selectedVariantId != null &&
            product.variants.firstOrNull { it.id == selectedVariantId }?.available == true &&
            !isAddingToCart
}

sealed interface ProductDetailIntent : UiIntent {
    data class VariantSelected(val variantId: String) : ProductDetailIntent
    data class QuantityChanged(val quantity: Int) : ProductDetailIntent
    data object AddToCart : ProductDetailIntent
}

sealed interface ProductDetailEffect : UiEffect {
    data object AddedToCart : ProductDetailEffect
}
