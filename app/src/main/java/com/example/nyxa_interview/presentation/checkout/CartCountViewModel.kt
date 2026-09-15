package com.example.nyxa_interview.presentation.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.domain.repository.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Drives the small cart-count badge shown on the Store grid and Product detail app bars. */
@HiltViewModel
class CartCountViewModel @Inject constructor(
    cartRepository: CartRepository,
) : ViewModel() {

    val itemCount = cartRepository.cart
        .map { it?.totalQuantity ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
