package com.example.nyxa_interview.domain.model

import java.time.Instant

enum class PrizeType { CASH, VIP_MERCH, ENTRIES }

data class Prize(
    val type: PrizeType,
    val amountCents: Long,
    val label: String,
)

data class SpinResult(
    val resultId: String,
    val segmentIndex: Int,
    val segments: List<String>,
    val prize: Prize,
    val serverSeed: String,
    val clientSeedEcho: String,
    val settledAt: Instant,
)

enum class BoxTier(val displayName: String, val priceCents: Long) {
    BRONZE("Bronze", 5_000),
    SILVER("Silver", 15_000),
    GOLD("Gold", 30_000),
    PLATINUM("Platinum", 60_000),
    ICE("Ice", 100_000),
}

data class BoxResult(
    val resultId: String,
    val tier: BoxTier,
    val prize: Prize,
    /** Fractional 0..1 checkpoints the reveal animation pauses/accelerates through. */
    val revealSequence: List<Float>,
)

/**
 * Outcome of requesting a spin/box open which may have succeeded on the server without the
 * client ever seeing the response body (dropped connection). [Pending] means "charged, outcome
 * unknown to this client yet" and callers MUST resolve it via the recovery endpoint before
 * treating the credit/wallet as authoritative.
 */
sealed interface GameOutcome<out T> {
    data class Resolved<T>(val result: T) : GameOutcome<T>
    data class Pending(val idempotencyKey: String) : GameOutcome<Nothing>
}
