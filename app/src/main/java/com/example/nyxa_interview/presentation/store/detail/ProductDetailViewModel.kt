package com.example.nyxa_interview.presentation.store.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.repository.CartRepository
import com.example.nyxa_interview.domain.repository.ProductCache
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

const val PRODUCT_ID_ARG = "productId"

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productCache: ProductCache,
    private val cartRepository: CartRepository,
) : MviViewModel<ProductDetailUiState, ProductDetailIntent, ProductDetailEffect>(ProductDetailUiState()) {

    init {
        val encodedProductId: String = checkNotNull(savedStateHandle[PRODUCT_ID_ARG])
        val productId = URLDecoder.decode(encodedProductId, StandardCharsets.UTF_8.name())
        val product = productCache.get(productId)
        setState {
            copy(
                product = product,
                selectedVariantId = product?.variants?.firstOrNull { it.available }?.id,
            )
        }
    }

    override fun onIntent(intent: ProductDetailIntent) {
        when (intent) {
            is ProductDetailIntent.VariantSelected -> setState { copy(selectedVariantId = intent.variantId) }
            is ProductDetailIntent.QuantityChanged -> setState {
                copy(quantity = intent.quantity.coerceIn(1, 10))
            }

            ProductDetailIntent.AddToCart -> addToCart()
        }
    }

    private fun addToCart() {
        val product = currentState.product ?: return
        val variantId = currentState.selectedVariantId ?: return
        if (!currentState.canAddToCart) return

        setState { copy(isAddingToCart = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = cartRepository.addLine(product, variantId, currentState.quantity)) {
                is AppResult.Success -> {
                    setState { copy(isAddingToCart = false, quantity = 1) }
                    sendEffect(ProductDetailEffect.AddedToCart(product.title))
                }

                is AppResult.Error -> setState {
                    copy(isAddingToCart = false, errorMessage = "Couldn't add to cart, please try again.")
                }
            }
        }
    }
}
