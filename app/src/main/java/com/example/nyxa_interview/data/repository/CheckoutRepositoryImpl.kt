package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.data.remote.AuthenticatedApiGateway
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.repository.CheckoutReceipt
import com.example.nyxa_interview.domain.repository.CheckoutRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckoutRepositoryImpl @Inject constructor(
    private val mockBackend: MockBackend,
    private val gateway: AuthenticatedApiGateway,
) : CheckoutRepository {

    override suspend fun checkout(cartId: String, idempotencyKey: String): AppResult<CheckoutReceipt> {
        return when (val result = gateway.call { mockBackend.checkout(cartId, idempotencyKey) }) {
            is AppResult.Success -> AppResult.Success(
                CheckoutReceipt(result.data.orderId, result.data.entriesAwarded)
            )

            is AppResult.Error -> AppResult.Error(result.error)
        }
    }
}
