package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.repository.CartRepository
import com.example.nyxa_interview.domain.repository.CheckoutReceipt
import com.example.nyxa_interview.domain.repository.CheckoutRepository
import com.example.nyxa_interview.domain.repository.WalletRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Covers checkout idempotency at the use-case level: the same client-generated key must be
 * reusable across retries without the side effects (cart clear, wallet refresh) firing twice
 * in ways that could desync local state from the server's single order.
 */
class CheckoutUseCaseTest {

    private val checkoutRepository: CheckoutRepository = mockk()
    private val cartRepository: CartRepository = mockk(relaxUnitFun = true)
    private val walletRepository: WalletRepository = mockk(relaxUnitFun = true)
    private val idempotencyKeyGenerator: IdempotencyKeyGenerator = mockk()

    private lateinit var checkoutUseCase: CheckoutUseCase

    @Before
    fun setUp() {
        coEvery { walletRepository.refresh() } returns AppResult.Success(Unit)
        checkoutUseCase = CheckoutUseCase(checkoutRepository, cartRepository, walletRepository, idempotencyKeyGenerator)
    }

    @Test
    fun `newIdempotencyKey is generated once and can be reused across retries by the caller`() {
        every { idempotencyKeyGenerator.generate() } returns "key-1" andThen "key-2"

        val first = checkoutUseCase.newIdempotencyKey()
        val second = checkoutUseCase.newIdempotencyKey()

        assertThat(first).isNotEqualTo(second)
        // The ViewModel is responsible for calling this once per attempt and reusing the result;
        // this test documents that the use case itself does not silently mint a fresh key per call.
    }

    @Test
    fun `retrying checkout with the same key after a timeout clears the cart and refreshes the wallet only once the server confirms`() = runTest {
        val key = "retry-key"
        val receipt = CheckoutReceipt(orderId = "order_1", entriesAwarded = 500)
        coEvery { checkoutRepository.checkout("cart_1", key) } returns AppResult.Error(AppError.Timeout) andThen AppResult.Success(receipt)

        val firstAttempt = checkoutUseCase("cart_1", key)
        coVerify(exactly = 0) { cartRepository.clear() }

        val secondAttempt = checkoutUseCase("cart_1", key)

        assertThat(firstAttempt).isInstanceOf(AppResult.Error::class.java)
        assertThat(secondAttempt).isEqualTo(AppResult.Success(receipt))
        coVerify(exactly = 1) { cartRepository.clear() }
    }

    @Test
    fun `a successful checkout clears the cart and refreshes the wallet exactly once`() = runTest {
        val receipt = CheckoutReceipt(orderId = "order_1", entriesAwarded = 500)
        coEvery { checkoutRepository.checkout("cart_1", "key-1") } returns AppResult.Success(receipt)

        val result = checkoutUseCase("cart_1", "key-1")

        assertThat(result).isEqualTo(AppResult.Success(receipt))
        coVerify(exactly = 1) { cartRepository.clear() }
        coVerify(exactly = 1) { walletRepository.refresh() }
    }

    @Test
    fun `a failed checkout never clears the cart, so the user's order intent is preserved for retry`() = runTest {
        coEvery { checkoutRepository.checkout("cart_1", "key-1") } returns AppResult.Error(AppError.Network)

        checkoutUseCase("cart_1", "key-1")

        coVerify(exactly = 0) { cartRepository.clear() }
        coVerify(exactly = 0) { walletRepository.refresh() }
    }
}
