package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.data.remote.AuthenticatedApiGateway
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.model.Cart
import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.domain.repository.CartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepositoryImpl @Inject constructor(
    private val mockBackend: MockBackend,
    private val gateway: AuthenticatedApiGateway,
) : CartRepository {

    private val cartState = MutableStateFlow<Cart?>(null)
    override val cart = cartState.asStateFlow()

    override suspend fun addLine(product: Product, variantId: String, quantity: Int): AppResult<Cart> {
        val result = gateway.call { mockBackend.addCartLine(variantId, quantity) }
        if (result is AppResult.Success) {
            cartState.value = result.data
        }
        return result
    }

    override suspend fun updateQuantity(variantId: String, quantity: Int): AppResult<Cart> {
        val result = gateway.call { mockBackend.updateCartLineQuantity(variantId, quantity) }
        if (result is AppResult.Success) {
            cartState.value = result.data
        }
        return result
    }

    override suspend fun clear() {
        cartState.value = null
    }
}
