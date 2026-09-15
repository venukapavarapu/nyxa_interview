package com.example.nyxa_interview.presentation.store.grid

import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.repository.ProductCache
import com.example.nyxa_interview.domain.repository.ProductRepository
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_COLLECTION = "featured"

@HiltViewModel
class StoreGridViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productCache: ProductCache,
) : MviViewModel<StoreGridUiState, StoreGridIntent, StoreGridEffect>(StoreGridUiState()) {

    private var nextCursor: String? = null

    init {
        onIntent(StoreGridIntent.LoadInitial)
    }

    override fun onIntent(intent: StoreGridIntent) {
        when (intent) {
            StoreGridIntent.LoadInitial -> loadInitial()
            StoreGridIntent.LoadMore -> loadMore()
            is StoreGridIntent.ProductClicked -> sendEffect(StoreGridEffect.NavigateToDetail(intent.productId))
        }
    }

    private fun loadInitial() {
        if (currentState.products.isNotEmpty() || currentState.isInitialLoading) return
        setState { copy(isInitialLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = productRepository.getProducts(DEFAULT_COLLECTION, cursor = null)) {
                is AppResult.Success -> {
                    nextCursor = result.data.nextCursor
                    productCache.put(result.data.items)
                    setState {
                        copy(
                            products = result.data.items,
                            isInitialLoading = false,
                            endReached = result.data.nextCursor == null,
                        )
                    }
                }

                is AppResult.Error -> setState {
                    copy(isInitialLoading = false, errorMessage = "Couldn't load products. Pull to retry.")
                }
            }
        }
    }

    private fun loadMore() {
        if (currentState.isLoadingMore || currentState.endReached || currentState.isInitialLoading) return
        val cursor = nextCursor ?: return
        setState { copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = productRepository.getProducts(DEFAULT_COLLECTION, cursor)) {
                is AppResult.Success -> {
                    nextCursor = result.data.nextCursor
                    productCache.put(result.data.items)
                    setState {
                        copy(
                            products = products + result.data.items,
                            isLoadingMore = false,
                            endReached = result.data.nextCursor == null,
                        )
                    }
                }

                is AppResult.Error -> setState { copy(isLoadingMore = false) }
            }
        }
    }
}
