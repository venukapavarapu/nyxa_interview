package com.example.nyxa_interview.presentation.box

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyxa_interview.domain.model.BoxResult
import com.example.nyxa_interview.domain.model.BoxTier
import com.example.nyxa_interview.presentation.wallet.WalletHeader
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxRoute(viewModel: BoxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var revealingResult by remember { mutableStateOf<BoxResult?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BoxEffect.PlayReveal -> revealingResult = effect.result
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Mystery Box") })
                WalletHeader(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    ) { padding ->
        BoxScreen(
            state = state,
            revealingResult = revealingResult,
            padding = padding,
            onIntent = viewModel::onIntent,
            onRevealFinished = {
                revealingResult = null
                viewModel.onIntent(BoxIntent.RevealFinished)
            },
        )
    }
}

@Composable
private fun BoxScreen(
    state: BoxUiState,
    revealingResult: BoxResult?,
    padding: PaddingValues,
    onIntent: (BoxIntent) -> Unit,
    onRevealFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (revealingResult != null) {
                BoxRevealAnimation(result = revealingResult, onFinished = onRevealFinished)
            } else {
                TierGrid(selectedTier = state.selectedTier, onSelect = { onIntent(BoxIntent.TierSelected(it)) })
            }
        }

        if (state.phase == BoxPhase.RESOLVING) {
            Text(
                text = "Confirming your last box…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Button(
            onClick = { onIntent(BoxIntent.OpenTapped) },
            enabled = state.isOpenEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!state.isOpenEnabled) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Open ${state.selectedTier.displayName} — $${"%.2f".format(state.selectedTier.priceCents / 100.0)}")
            }
        }
    }

    if (state.phase == BoxPhase.SETTLED && state.lastResult != null) {
        AlertDialog(
            onDismissRequest = { onIntent(BoxIntent.DismissResult) },
            title = { Text("You won!") },
            text = { Text("$${"%.2f".format(state.lastResult.prize.amountCents / 100.0)} cash") },
            confirmButton = {
                Button(onClick = { onIntent(BoxIntent.DismissResult) }) { Text("Nice") }
            },
        )
    }
}

@Composable
private fun TierGrid(selectedTier: BoxTier, onSelect: (BoxTier) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        BoxTier.entries.forEach { tier ->
            FilterChip(
                selected = tier == selectedTier,
                onClick = { onSelect(tier) },
                label = { Text(tier.displayName) },
            )
        }
    }
}
