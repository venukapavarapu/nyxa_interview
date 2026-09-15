package com.example.nyxa_interview.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.nyxa_interview.data.local.dao.PendingGameActionDao
import com.example.nyxa_interview.data.local.dao.WalletDao
import com.example.nyxa_interview.data.local.entity.LedgerEntryEntity
import com.example.nyxa_interview.data.local.entity.PendingGameActionEntity
import com.example.nyxa_interview.data.local.entity.WalletSummaryEntity

@Database(
    entities = [
        LedgerEntryEntity::class,
        WalletSummaryEntity::class,
        PendingGameActionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun pendingGameActionDao(): PendingGameActionDao
}
