package com.example.nyxa_interview.presentation.wallet

import com.example.nyxa_interview.domain.model.LedgerEntry
import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

data class WalletUiState(
    val entries: Long = 0,
    val spinCredits: Int = 0,
    val ledger: List<LedgerEntry> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasPendingGames: Boolean = false,
) : UiState

sealed interface WalletIntent : UiIntent {
    data object Refresh : WalletIntent
}

sealed interface WalletEffect : UiEffect
