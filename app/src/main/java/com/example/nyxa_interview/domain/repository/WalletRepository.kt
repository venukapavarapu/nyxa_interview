package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.Wallet
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    /** Emits from local cache immediately, then refreshes from network in the background. */
    fun observeWallet(): Flow<Wallet>
    suspend fun refresh(): AppResult<Unit>
}
