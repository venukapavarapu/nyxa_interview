package com.example.nyxa_interview.presentation.store.grid

import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

data class StoreGridUiState(
    val products: List<Product> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface StoreGridIntent : UiIntent {
    data object LoadInitial : StoreGridIntent
    data object LoadMore : StoreGridIntent
    data class ProductClicked(val productId: String) : StoreGridIntent
}

sealed interface StoreGridEffect : UiEffect {
    data class NavigateToDetail(val productId: String) : StoreGridEffect
}
