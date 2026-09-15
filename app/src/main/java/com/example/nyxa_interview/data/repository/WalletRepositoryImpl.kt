package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.data.local.dao.WalletDao
import com.example.nyxa_interview.data.local.entity.LedgerEntryEntity
import com.example.nyxa_interview.data.local.entity.WalletSummaryEntity
import com.example.nyxa_interview.data.remote.AuthenticatedApiGateway
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.model.LedgerEntry
import com.example.nyxa_interview.domain.model.LedgerType
import com.example.nyxa_interview.domain.model.Wallet
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nyxa_interview.domain.repository.WalletRepository

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val mockBackend: MockBackend,
    private val gateway: AuthenticatedApiGateway,
) : WalletRepository {

    override fun observeWallet() = walletDao.observeSummary().combine(walletDao.observeLedger()) { summary, ledger ->
        Wallet(
            entries = summary?.entries ?: 0L,
            spinCredits = summary?.spinCredits ?: 0,
            ledger = ledger.map { it.toDomain() },
        )
    }

    override suspend fun refresh(): AppResult<Unit> {
        val result = gateway.call { mockBackend.getWallet() }
        return when (result) {
            is AppResult.Success -> {
                val (entries, spinCredits, ledger) = result.data
                walletDao.replaceAll(
                    summary = WalletSummaryEntity(entries = entries, spinCredits = spinCredits),
                    entries = ledger.map { it.toEntity() },
                )
                AppResult.Success(Unit)
            }

            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    private fun LedgerEntryEntity.toDomain() = LedgerEntry(
        id = id,
        type = LedgerType.valueOf(type),
        entriesDelta = entriesDelta,
        cashDeltaCents = cashDeltaCents,
        ref = ref,
        at = Instant.ofEpochMilli(atEpochMillis),
        isPending = isPending,
    )

    private fun LedgerEntry.toEntity() = LedgerEntryEntity(
        id = id,
        type = type.name,
        entriesDelta = entriesDelta,
        cashDeltaCents = cashDeltaCents,
        ref = ref,
        atEpochMillis = at.toEpochMilli(),
        isPending = isPending,
    )
}
