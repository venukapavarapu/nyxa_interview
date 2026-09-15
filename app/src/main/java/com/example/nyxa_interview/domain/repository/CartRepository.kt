package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.Cart
import com.example.nyxa_interview.domain.model.Product
import kotlinx.coroutines.flow.StateFlow

interface CartRepository {
    val cart: StateFlow<Cart?>
    suspend fun addLine(product: Product, variantId: String, quantity: Int): AppResult<Cart>
    suspend fun clear()
}
