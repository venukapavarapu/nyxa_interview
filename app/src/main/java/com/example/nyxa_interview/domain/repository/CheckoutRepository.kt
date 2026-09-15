package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.core.result.AppResult

data class CheckoutReceipt(val orderId: String, val entriesAwarded: Long)

interface CheckoutRepository {
    /**
     * [idempotencyKey] must be generated once per user checkout attempt and reused across
     * retries so a dropped response or a double-tap never creates two orders.
     */
    suspend fun checkout(cartId: String, idempotencyKey: String): AppResult<CheckoutReceipt>
}
