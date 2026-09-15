package com.example.nyxa_interview.presentation.checkout

import app.cash.turbine.test
import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.Cart
import com.example.nyxa_interview.domain.model.CartLine
import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.domain.model.ProductVariant
import com.example.nyxa_interview.domain.repository.CartRepository
import com.example.nyxa_interview.domain.usecase.CheckoutUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Covers the cart page's increase/decrease-quantity controls: a change calls
 * [CartRepository.updateQuantity] with the right target value (current +/- 1, never re-derived
 * from possibly-stale UI state), marks only the affected line as updating so other lines' steppers
 * stay usable, and a second tap on the same line while one is already in flight is a no-op rather
 * than firing a duplicate request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelQuantityTest {

    private val cartFlow = MutableStateFlow<Cart?>(null)
    private val cartRepository: CartRepository = mockk {
        every { cart } returns cartFlow
    }
    private val checkoutUseCase: CheckoutUseCase = mockk()

    private lateinit var viewModel: CheckoutViewModel

    private val variant = ProductVariant(id = "v1", label = "M / Black", available = true)
    private val product = Product(
        id = "p1", title = "Redline Tee", priceCents = 2000, entryCount = 100,
        multiplier = null, imageUrl = null, variants = listOf(variant),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CheckoutViewModel(cartRepository, checkoutUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `increasing quantity calls updateQuantity with currentQuantity plus one`() = runTest {
        coEvery { cartRepository.updateQuantity("v1", 3) } returns AppResult.Success(
            Cart("cart1", listOf(CartLine(product, variant, quantity = 3)))
        )

        viewModel.onIntent(CheckoutIntent.IncreaseQuantity(variantId = "v1", currentQuantity = 2))

        coVerify(exactly = 1) { cartRepository.updateQuantity("v1", 3) }
    }

    @Test
    fun `decreasing quantity to zero calls updateQuantity with zero, which removes the line server-side`() = runTest {
        coEvery { cartRepository.updateQuantity("v1", 0) } returns AppResult.Success(Cart("cart1", emptyList()))

        viewModel.onIntent(CheckoutIntent.DecreaseQuantity(variantId = "v1", currentQuantity = 1))

        coVerify(exactly = 1) { cartRepository.updateQuantity("v1", 0) }
    }

    @Test
    fun `linesUpdating clears the variant after the update resolves, success or failure`() = runTest {
        coEvery { cartRepository.updateQuantity("v1", 3) } returns AppResult.Success(
            Cart("cart1", listOf(CartLine(product, variant, quantity = 3)))
        )

        viewModel.state.test {
            assertThat(awaitItem().linesUpdating).isEmpty()

            viewModel.onIntent(CheckoutIntent.IncreaseQuantity(variantId = "v1", currentQuantity = 2))

            assertThat(awaitItem().linesUpdating).containsExactly("v1")
            assertThat(awaitItem().linesUpdating).isEmpty()
        }
    }

    @Test
    fun `a failed quantity update surfaces an error message and still clears the in-flight flag`() = runTest {
        coEvery { cartRepository.updateQuantity("v1", 3) } returns AppResult.Error(AppError.Network)

        viewModel.onIntent(CheckoutIntent.IncreaseQuantity(variantId = "v1", currentQuantity = 2))

        assertThat(viewModel.state.value.linesUpdating).isEmpty()
        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    @Test
    fun `a second change on the same line while one is already in flight is ignored`() = runTest {
        // Never completes within this test, simulating an in-flight request.
        coEvery { cartRepository.updateQuantity("v1", any()) } coAnswers {
            delay(Long.MAX_VALUE)
            AppResult.Success(Cart("cart1", emptyList()))
        }

        viewModel.onIntent(CheckoutIntent.IncreaseQuantity(variantId = "v1", currentQuantity = 2))
        viewModel.onIntent(CheckoutIntent.IncreaseQuantity(variantId = "v1", currentQuantity = 2))

        coVerify(exactly = 1) { cartRepository.updateQuantity("v1", any()) }
    }
}
