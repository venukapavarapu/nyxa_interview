package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.data.remote.AuthenticatedApiGateway
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.repository.ProductPage
import com.example.nyxa_interview.domain.repository.ProductRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val mockBackend: MockBackend,
    private val gateway: AuthenticatedApiGateway,
) : ProductRepository {

    override suspend fun getProducts(collection: String, cursor: String?): AppResult<ProductPage> {
        return when (val result = gateway.call { mockBackend.getProducts(cursor) }) {
            is AppResult.Success -> {
                val (items, nextCursor) = result.data
                AppResult.Success(ProductPage(items, nextCursor))
            }

            is AppResult.Error -> AppResult.Error(result.error)
        }
    }
}
