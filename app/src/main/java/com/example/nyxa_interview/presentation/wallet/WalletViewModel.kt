package com.example.nyxa_interview.presentation.wallet

import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.WalletRepository
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val pendingGameActionRepository: PendingGameActionRepository,
) : MviViewModel<WalletUiState, WalletIntent, WalletEffect>(WalletUiState()) {

    init {
        viewModelScope.launch {
            walletRepository.observeWallet()
                .combine(pendingGameActionRepository.observePending()) { wallet, pending -> wallet to pending }
                .collect { (wallet, pending) ->
                    setState {
                        copy(
                            entries = wallet.entries,
                            spinCredits = wallet.spinCredits,
                            ledger = wallet.ledger,
                            hasPendingGames = pending.isNotEmpty(),
                        )
                    }
                }
        }
        refresh()
    }

    override fun onIntent(intent: WalletIntent) {
        when (intent) {
            WalletIntent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        setState { copy(isRefreshing = true) }
        viewModelScope.launch {
            walletRepository.refresh()
            setState { copy(isRefreshing = false) }
        }
    }
}
