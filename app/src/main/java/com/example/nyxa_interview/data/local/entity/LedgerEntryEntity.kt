package com.example.nyxa_interview.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val entriesDelta: Long,
    val cashDeltaCents: Long,
    val ref: String,
    val atEpochMillis: Long,
    val isPending: Boolean = false,
)

@Entity(tableName = "wallet_summary")
data class WalletSummaryEntity(
    @PrimaryKey val id: Int = 0,
    val entries: Long,
    val spinCredits: Int,
)
