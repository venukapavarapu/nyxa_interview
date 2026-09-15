package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.Product

data class ProductPage(val items: List<Product>, val nextCursor: String?)

interface ProductRepository {
    suspend fun getProducts(collection: String, cursor: String?): AppResult<ProductPage>
}
