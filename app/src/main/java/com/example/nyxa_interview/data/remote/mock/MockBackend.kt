package com.example.nyxa_interview.data.remote.mock

import com.example.nyxa_interview.data.remote.dto.ProductDto
import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.domain.model.Cart
import com.example.nyxa_interview.domain.model.CartLine
import com.example.nyxa_interview.domain.model.LedgerEntry
import com.example.nyxa_interview.domain.model.LedgerType
import com.example.nyxa_interview.domain.model.Member
import com.example.nyxa_interview.domain.model.MembershipTier
import com.example.nyxa_interview.domain.model.Prize
import com.example.nyxa_interview.domain.model.PrizeType
import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.domain.model.SpinResult
import com.example.nyxa_interview.data.remote.mapper.ProductNormalizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class ServerCheckoutRecord(val orderId: String, val entriesAwarded: Long)

/**
 * In-process fake standing in for the real backend described in the assignment brief. Holds
 * authoritative "server-side" state (wallet, ledger, catalogue, spins, boxes, orders) in memory
 * for the lifetime of the process.
 *
 * Every public method here is one HTTP endpoint from section 3.1 of the brief. Each pays the
 * same 400-1500ms latency + 15% random failure simulation a real network call would (see
 * [NetworkConditions]), and every *charging* endpoint (spin/box/checkout) is idempotent on the
 * client's idempotencyKey: replaying the same key returns the original result instead of
 * re-charging. That common "simulate the network, then settle exactly once per key" shape lives
 * in [settleOnce] so each endpoint below only has to describe what makes it unique.
 */
@Singleton
class MockBackend @Inject constructor(
    private val productNormalizer: ProductNormalizer,
    private val conditions: NetworkConditions,
) {
    private val mutex = Mutex()

    private val catalogue: List<ProductDto> by lazy { MockCatalogueFactory.generate() }
    private val normalizedCatalogue: List<Product> by lazy { catalogue.map(productNormalizer::normalize) }

    private var entries: Long = 480_000L
    private var spinCredits: Int = 3
    private val ledger = mutableListOf<LedgerEntry>(
        LedgerEntry(
            id = "l_seed_1",
            type = LedgerType.ADJUSTMENT,
            entriesDelta = 480_000L,
            cashDeltaCents = 0L,
            ref = "welcome_bonus",
            at = Instant.now().minusSeconds(86_400),
        )
    )

    private var cart: Cart? = null

    private val spinResultsByKey = mutableMapOf<String, SpinResult>()
    private val spinResultsById = mutableMapOf<String, SpinResult>()
    private val boxResultsByKey = mutableMapOf<String, BoxResult>()
    private val checkoutByKey = mutableMapOf<String, ServerCheckoutRecord>()

    fun currentMember(): Member = Member(
        id = "m_101",
        displayName = "Kyle H.",
        membership = MembershipTier.ANNUAL,
        state = "TX",
    )

    suspend fun getWallet(): Triple<Long, Int, List<LedgerEntry>> = simulated {
        Triple(entries, spinCredits, ledger.toList())
    }

    suspend fun getProducts(cursor: String?, pageSize: Int = 40): Pair<List<Product>, String?> = simulated {
        val start = cursor?.toIntOrNull() ?: 0
        val end = (start + pageSize).coerceAtMost(normalizedCatalogue.size)
        val page = if (start >= normalizedCatalogue.size) emptyList() else normalizedCatalogue.subList(start, end)
        val nextCursor = if (end < normalizedCatalogue.size) end.toString() else null
        page to nextCursor
    }

    suspend fun addCartLine(variantId: String, quantity: Int): Cart = simulated {
        val product = normalizedCatalogue.firstOrNull { p -> p.variants.any { it.id == variantId } }
            ?: throw MockApiException.NotFound("variant $variantId")
        val variant = product.variants.first { it.id == variantId }

        val lines = (cart?.lines ?: emptyList()).toMutableList()
        val existingIndex = lines.indexOfFirst { it.variant.id == variantId }
        if (existingIndex >= 0) {
            lines[existingIndex] = lines[existingIndex].let { it.copy(quantity = it.quantity + quantity) }
        } else {
            lines += CartLine(product, variant, quantity)
        }
        (cart ?: Cart(id = "cart_${UUID.randomUUID()}", lines = emptyList()))
            .copy(lines = lines)
            .also { cart = it }
    }

    /** Sets a line's quantity to an exact value. A quantity of 0 or less removes the line entirely. */
    suspend fun updateCartLineQuantity(variantId: String, quantity: Int): Cart = simulated {
        val existing = cart ?: throw MockApiException.NotFound("cart")
        val lines = existing.lines.toMutableList()
        val index = lines.indexOfFirst { it.variant.id == variantId }
        if (index < 0) throw MockApiException.NotFound("line for variant $variantId")

        if (quantity <= 0) lines.removeAt(index) else lines[index] = lines[index].copy(quantity = quantity)
        existing.copy(lines = lines).also { cart = it }
    }

    suspend fun checkout(cartId: String, idempotencyKey: String): ServerCheckoutRecord =
        settleOnce(idempotencyKey, checkoutByKey) {
            val currentCart = cart?.takeIf { it.id == cartId } ?: throw MockApiException.NotFound("cart $cartId")
            val orderId = "order_${UUID.randomUUID()}"
            val entriesAwarded = currentCart.totalEntries

            entries += entriesAwarded
            ledger += LedgerEntry(
                id = "l_${UUID.randomUUID()}",
                type = LedgerType.PURCHASE,
                entriesDelta = entriesAwarded,
                cashDeltaCents = 0L,
                ref = orderId,
                at = Instant.now(),
            )
            cart = null

            ServerCheckoutRecord(orderId, entriesAwarded)
        }

    suspend fun spin(idempotencyKey: String): SpinResult = settleOnce(idempotencyKey, spinResultsByKey) {
        // Credit check happens inside the same lock as settlement, so a burst of concurrent
        // requests (e.g. a slipped-through double tap) can never settle more spins than the user
        // has actually paid for.
        if (spinCredits <= 0) throw MockApiException.InsufficientSpinCredits()

        val segments = listOf("$250K", "$100K", "$10K", "$5", "VIP Merch", "$5", "$10K", "$5")
        val segmentIndex = Random.nextInt(segments.size)
        val result = SpinResult(
            resultId = "sr_${UUID.randomUUID()}",
            segmentIndex = segmentIndex,
            segments = segments,
            prize = prizeForSegment(segments[segmentIndex]),
            serverSeed = UUID.randomUUID().toString(),
            clientSeedEcho = idempotencyKey,
            settledAt = Instant.now(),
        )

        spinCredits -= 1
        ledger += LedgerEntry(
            id = "l_${UUID.randomUUID()}",
            type = LedgerType.SPIN,
            entriesDelta = 0,
            cashDeltaCents = result.prize.amountCents,
            ref = result.resultId,
            at = result.settledAt,
        )
        spinResultsById[result.resultId] = result
        spinResultsByKey[idempotencyKey] = result

        if (conditions.consumeForcedSpinDrop()) throw MockApiException.DroppedConnection()

        result
    }

    suspend fun getSpinResult(resultId: String): SpinResult = simulated {
        spinResultsById[resultId] ?: throw MockApiException.NotFound("spin result $resultId")
    }

    suspend fun findSpinByIdempotencyKey(idempotencyKey: String): SpinResult? = simulated {
        spinResultsByKey[idempotencyKey]
    }

    suspend fun openBox(tier: BoxTier, idempotencyKey: String): BoxResult = settleOnce(idempotencyKey, boxResultsByKey) {
        val result = BoxResult(
            resultId = "br_${UUID.randomUUID()}",
            tier = tier,
            prize = Prize(PrizeType.CASH, randomAmountInTierRange(tier), "Cash"),
            revealSequence = listOf(0.1f, 0.4f, 0.9f),
        )

        ledger += LedgerEntry(
            id = "l_${UUID.randomUUID()}",
            type = LedgerType.BOX,
            entriesDelta = 0,
            cashDeltaCents = result.prize.amountCents,
            ref = result.resultId,
            at = Instant.now(),
        )

        result
    }

    suspend fun findBoxByIdempotencyKey(idempotencyKey: String): BoxResult? = simulated {
        boxResultsByKey[idempotencyKey]
    }

    private fun randomAmountInTierRange(tier: BoxTier): Long {
        val min = tier.priceCents / 2
        val max = tier.priceCents * 20
        return Random.nextLong(min, max)
    }

    private fun prizeForSegment(segment: String): Prize = when (segment) {
        "VIP Merch" -> Prize(PrizeType.VIP_MERCH, 0, segment)
        "$250K" -> Prize(PrizeType.CASH, 25_000_000, segment)
        "$100K" -> Prize(PrizeType.CASH, 10_000_000, segment)
        "$10K" -> Prize(PrizeType.CASH, 1_000_000, segment)
        else -> Prize(PrizeType.CASH, 500, segment)
    }

    /** Every call pays simulated latency, then may throw the simulated random server error. */
    private suspend fun <T> simulated(block: () -> T): T = mutex.withLock {
        conditions.simulateLatency()
        maybeFail()
        block()
    }

    /**
     * The shared shape of every charging endpoint: pay latency, then if [idempotencyKey] has
     * already settled in [cache] return that exact result (no re-charge, and — critically — no
     * chance to fail on a replay, since the real work already happened). Only a genuinely new key
     * risks the simulated random failure and runs [settle].
     */
    private suspend fun <T> settleOnce(idempotencyKey: String, cache: MutableMap<String, T>, settle: () -> T): T =
        mutex.withLock {
            conditions.simulateLatency()
            cache[idempotencyKey]?.let { return@withLock it }
            maybeFail()
            settle().also { cache[idempotencyKey] = it }
        }

    private fun maybeFail() {
        if (conditions.shouldFail()) throw MockApiException.ServerError()
    }
}
