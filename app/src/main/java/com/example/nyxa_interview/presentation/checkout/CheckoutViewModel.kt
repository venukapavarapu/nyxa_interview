package com.example.nyxa_interview.presentation.checkout

import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.repository.CartRepository
import com.example.nyxa_interview.domain.usecase.CheckoutUseCase
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val checkoutUseCase: CheckoutUseCase,
) : MviViewModel<CheckoutUiState, CheckoutIntent, CheckoutEffect>(CheckoutUiState()) {

    /**
     * Generated once when a checkout attempt starts and reused across every retry of that same
     * attempt, so a double-tap on "Place order" (guarded by [CheckoutUiState.canPlaceOrder]
     * disabling the button while in flight) or a retry after a timeout replays the same key
     * instead of minting a new order.
     */
    private var activeIdempotencyKey: String? = null

    init {
        viewModelScope.launch {
            cartRepository.cart.collect { cart -> setState { copy(cart = cart) } }
        }
    }

    override fun onIntent(intent: CheckoutIntent) {
        when (intent) {
            CheckoutIntent.PlaceOrder -> placeOrder()
            CheckoutIntent.DismissConfirmation -> setState { copy(orderConfirmation = null) }
        }
    }

    private fun placeOrder() {
        val cart = currentState.cart ?: return
        if (!currentState.canPlaceOrder) return

        val idempotencyKey = activeIdempotencyKey ?: checkoutUseCase.newIdempotencyKey().also {
            activeIdempotencyKey = it
        }

        setState { copy(isPlacingOrder = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = checkoutUseCase(cart.id, idempotencyKey)) {
                is AppResult.Success -> {
                    activeIdempotencyKey = null
                    setState {
                        copy(
                            isPlacingOrder = false,
                            orderConfirmation = OrderConfirmation(result.data.orderId, result.data.entriesAwarded),
                        )
                    }
                }

                is AppResult.Error -> setState {
                    // activeIdempotencyKey is deliberately kept so the next tap retries the same order.
                    copy(isPlacingOrder = false, errorMessage = "Checkout failed, tap Place order to retry.")
                }
            }
        }
    }
}
