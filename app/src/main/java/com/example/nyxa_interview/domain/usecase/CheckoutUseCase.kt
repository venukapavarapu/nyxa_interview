package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.repository.CartRepository
import com.example.nyxa_interview.domain.repository.CheckoutReceipt
import com.example.nyxa_interview.domain.repository.CheckoutRepository
import com.example.nyxa_interview.domain.repository.WalletRepository
import javax.inject.Inject

/**
 * A single idempotency key is generated once per checkout *attempt* (not per tap) and held by
 * the caller (the ViewModel) across retries, so a double-tap on "Place order" or a retry after
 * a timeout replays the same key and the server returns the original order instead of creating
 * a second one.
 */
class CheckoutUseCase @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
    private val cartRepository: CartRepository,
    private val walletRepository: WalletRepository,
    private val idempotencyKeyGenerator: IdempotencyKeyGenerator,
) {
    fun newIdempotencyKey(): String = idempotencyKeyGenerator.generate()

    suspend operator fun invoke(cartId: String, idempotencyKey: String): AppResult<CheckoutReceipt> {
        val result = checkoutRepository.checkout(cartId, idempotencyKey)
        if (result is AppResult.Success) {
            cartRepository.clear()
            walletRepository.refresh()
        }
        return result
    }
}
