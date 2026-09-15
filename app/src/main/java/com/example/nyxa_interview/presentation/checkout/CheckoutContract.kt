package com.example.nyxa_interview.presentation.checkout

import com.example.nyxa_interview.domain.model.Cart
import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

data class CheckoutUiState(
    val cart: Cart? = null,
    val isPlacingOrder: Boolean = false,
    /** Variant IDs with a quantity change in flight; those lines' steppers are disabled meanwhile. */
    val linesUpdating: Set<String> = emptySet(),
    val orderConfirmation: OrderConfirmation? = null,
    val errorMessage: String? = null,
) : UiState {
    val canPlaceOrder: Boolean get() = cart != null && cart.lines.isNotEmpty() && !isPlacingOrder
}

data class OrderConfirmation(val orderId: String, val entriesAwarded: Long)

sealed interface CheckoutIntent : UiIntent {
    data object PlaceOrder : CheckoutIntent
    data object DismissConfirmation : CheckoutIntent
    data class IncreaseQuantity(val variantId: String, val currentQuantity: Int) : CheckoutIntent
    /** Decreasing past 1 removes the line entirely. */
    data class DecreaseQuantity(val variantId: String, val currentQuantity: Int) : CheckoutIntent
}

sealed interface CheckoutEffect : UiEffect
