package com.example.nyxa_interview.domain.model

import java.time.Instant

enum class LedgerType { PURCHASE, SPIN, BOX, ADJUSTMENT }

data class Wallet(
    val entries: Long,
    val spinCredits: Int,
    val ledger: List<LedgerEntry>,
)

data class LedgerEntry(
    val id: String,
    val type: LedgerType,
    val entriesDelta: Long,
    val cashDeltaCents: Long,
    val ref: String,
    val at: Instant,
    /** True when this entry reflects a locally-optimistic write not yet confirmed by the server. */
    val isPending: Boolean = false,
)
