package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.data.remote.AuthenticatedApiGateway
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.model.Wallet
import com.example.nyxa_interview.domain.repository.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Held purely in memory for the lifetime of the process — nothing is written to disk besides
 * the auth token. That means the wallet/ledger does not survive a real process kill and needs a
 * network round-trip on cold start, unlike the brief's "renders from cache with no network" ask;
 * a deliberate scope cut given the assignment's time limit (see DECISIONS.md).
 */
@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val mockBackend: MockBackend,
    private val gateway: AuthenticatedApiGateway,
) : WalletRepository {

    private val walletState = MutableStateFlow(Wallet(entries = 0, spinCredits = 0, ledger = emptyList()))

    override fun observeWallet() = walletState.asStateFlow()

    override suspend fun refresh(): AppResult<Unit> {
        return when (val result = gateway.call { mockBackend.getWallet() }) {
            is AppResult.Success -> {
                val (entries, spinCredits, ledger) = result.data
                walletState.value = Wallet(
                    entries = entries,
                    spinCredits = spinCredits,
                    ledger = ledger.sortedByDescending { it.at },
                )
                AppResult.Success(Unit)
            }

            is AppResult.Error -> AppResult.Error(result.error)
        }
    }
}
