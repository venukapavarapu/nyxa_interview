package com.example.nyxa_interview.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.nyxa_interview.data.local.entity.LedgerEntryEntity
import com.example.nyxa_interview.data.local.entity.WalletSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    @Query("SELECT * FROM wallet_summary WHERE id = 0")
    fun observeSummary(): Flow<WalletSummaryEntity?>

    @Query("SELECT * FROM ledger_entries ORDER BY atEpochMillis DESC")
    fun observeLedger(): Flow<List<LedgerEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: WalletSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedgerEntries(entries: List<LedgerEntryEntity>)

    @Query("DELETE FROM ledger_entries")
    suspend fun clearLedger()

    @Transaction
    suspend fun replaceAll(summary: WalletSummaryEntity, entries: List<LedgerEntryEntity>) {
        clearLedger()
        upsertLedgerEntries(entries)
        upsertSummary(summary)
    }
}
