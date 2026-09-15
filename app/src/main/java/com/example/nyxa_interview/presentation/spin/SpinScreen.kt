package com.example.nyxa_interview.presentation.spin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.nyxa_interview.presentation.wallet.WalletHeader
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinRoute(
    showDebugControls: Boolean,
    viewModel: SpinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var animatingTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SpinEffect.AnimateToSegment -> animatingTarget = effect.segmentIndex
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Spin to Win") })
                WalletHeader(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    ) { padding ->
        SpinScreen(
            state = state,
            animatingTarget = animatingTarget,
            padding = padding,
            showDebugControls = showDebugControls,
            onIntent = viewModel::onIntent,
            onLandingFinished = {
                animatingTarget = null
                viewModel.onIntent(SpinIntent.AnimationFinished)
            },
        )
    }
}

@Composable
private fun SpinScreen(
    state: SpinUiState,
    animatingTarget: Int?,
    padding: PaddingValues,
    showDebugControls: Boolean,
    onIntent: (SpinIntent) -> Unit,
    onLandingFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PrizeWheel(
            segments = state.segments,
            targetSegmentIndex = animatingTarget,
            isRequesting = state.phase == SpinPhase.REQUESTING || state.phase == SpinPhase.RESOLVING,
            enabled = state.isSpinEnabled,
            onSpinTapped = { onIntent(SpinIntent.SpinTapped) },
            onLandingFinished = onLandingFinished,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        val isBusy = state.phase != SpinPhase.IDLE
        Button(
            onClick = { onIntent(SpinIntent.SpinTapped) },
            enabled = state.isSpinEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            when {
                isBusy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                state.spinCredits <= 0 -> Text("No spin credits left")
                else -> Text("Spin")
            }
        }
    }

    if (state.phase == SpinPhase.SETTLED && state.lastResult != null) {
        AlertDialog(
            onDismissRequest = { onIntent(SpinIntent.DismissResult) },
            title = { Text("You won!") },
            text = { Text(state.lastResult.prize.label) },
            confirmButton = {
                Button(onClick = { onIntent(SpinIntent.DismissResult) }) { Text("Nice") }
            },
        )
    }
}
