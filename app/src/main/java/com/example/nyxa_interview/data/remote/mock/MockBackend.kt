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
 * for the lifetime of the process, and is the single source of truth for idempotency
 * de-duplication: every mutating call is keyed by the client's idempotencyKey, so replays of
 * the same key return the original result instead of re-charging the user.
 *
 * Every public method here represents one HTTP endpoint from section 3.1 of the brief and pays
 * the same latency + 15% failure simulation a real network call would.
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

    /** idempotencyKey -> settled result. Acts as the server's own de-dup ledger. */
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

    suspend fun getWallet(): Triple<Long, Int, List<LedgerEntry>> = mutex.withLock {
        conditions.simulateLatency()
        maybeFail()
        Triple(entries, spinCredits, ledger.toList())
    }

    suspend fun getProducts(cursor: String?, pageSize: Int = 40): Pair<List<Product>, String?> {
        conditions.simulateLatency()
        maybeFail()
        val start = cursor?.toIntOrNull() ?: 0
        val end = (start + pageSize).coerceAtMost(normalizedCatalogue.size)
        val page = if (start >= normalizedCatalogue.size) emptyList() else normalizedCatalogue.subList(start, end)
        val nextCursor = if (end < normalizedCatalogue.size) end.toString() else null
        return page to nextCursor
    }

    suspend fun addCartLine(variantId: String, quantity: Int): Cart = mutex.withLock {
        conditions.simulateLatency()
        maybeFail()
        val product = normalizedCatalogue.firstOrNull { p -> p.variants.any { it.id == variantId } }
            ?: throw MockApiException.NotFound("variant $variantId")
        val variant = product.variants.first { it.id == variantId }

        val existing = cart ?: Cart(id = "cart_${UUID.randomUUID()}", lines = emptyList())
        val updatedLines = existing.lines.toMutableList()
        val existingIndex = updatedLines.indexOfFirst { it.variant.id == variantId }
        if (existingIndex >= 0) {
            val line = updatedLines[existingIndex]
            updatedLines[existingIndex] = line.copy(quantity = line.quantity + quantity)
        } else {
            updatedLines += CartLine(product, variant, quantity)
        }
        val updated = existing.copy(lines = updatedLines)
        cart = updated
        updated
    }

    suspend fun checkout(cartId: String, idempotencyKey: String): ServerCheckoutRecord = mutex.withLock {
        conditions.simulateLatency()

        checkoutByKey[idempotencyKey]?.let { return@withLock it }

        maybeFail()

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

        val record = ServerCheckoutRecord(orderId, entriesAwarded)
        checkoutByKey[idempotencyKey] = record
        record
    }

    suspend fun spin(idempotencyKey: String): SpinResult = mutex.withLock {
        conditions.simulateLatency()

        spinResultsByKey[idempotencyKey]?.let { return@withLock it }

        val settled = settleSpin(idempotencyKey)

        if (conditions.consumeForcedSpinDrop()) {
            throw MockApiException.DroppedConnection()
        }
        maybeFail()

        settled
    }

    /** Server-side settlement: charges the credit and records the result, independent of whether the response is delivered. */
    private fun settleSpin(idempotencyKey: String): SpinResult {
        val segments = listOf("$250K", "$100K", "$10K", "$5", "VIP Merch", "$5", "$10K", "$5")
        val segmentIndex = Random.nextInt(segments.size)
        val prize = prizeForSegment(segments[segmentIndex])
        val result = SpinResult(
            resultId = "sr_${UUID.randomUUID()}",
            segmentIndex = segmentIndex,
            segments = segments,
            prize = prize,
            serverSeed = UUID.randomUUID().toString(),
            clientSeedEcho = idempotencyKey,
            settledAt = Instant.now(),
        )

        spinCredits = (spinCredits - 1).coerceAtLeast(0)
        ledger += LedgerEntry(
            id = "l_${UUID.randomUUID()}",
            type = LedgerType.SPIN,
            entriesDelta = 0,
            cashDeltaCents = prize.amountCents,
            ref = result.resultId,
            at = result.settledAt,
        )

        spinResultsByKey[idempotencyKey] = result
        spinResultsById[result.resultId] = result
        return result
    }

    suspend fun getSpinResult(resultId: String): SpinResult = mutex.withLock {
        conditions.simulateLatency()
        maybeFail()
        spinResultsById[resultId] ?: throw MockApiException.NotFound("spin result $resultId")
    }

    suspend fun findSpinByIdempotencyKey(idempotencyKey: String): SpinResult? = mutex.withLock {
        conditions.simulateLatency()
        maybeFail()
        spinResultsByKey[idempotencyKey]
    }

    suspend fun openBox(tier: BoxTier, idempotencyKey: String): BoxResult = mutex.withLock {
        conditions.simulateLatency()

        boxResultsByKey[idempotencyKey]?.let { return@withLock it }

        maybeFail()

        val prizeAmount = randomAmountInTierRange(tier)
        val result = BoxResult(
            resultId = "br_${UUID.randomUUID()}",
            tier = tier,
            prize = Prize(PrizeType.CASH, prizeAmount, "Cash"),
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

        boxResultsByKey[idempotencyKey] = result
        result
    }

    suspend fun findBoxByIdempotencyKey(idempotencyKey: String): BoxResult? = mutex.withLock {
        conditions.simulateLatency()
        maybeFail()
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

    private fun maybeFail() {
        if (conditions.shouldFail()) throw MockApiException.ServerError()
    }
}
