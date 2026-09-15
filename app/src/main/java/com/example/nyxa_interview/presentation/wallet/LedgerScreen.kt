package com.example.nyxa_interview.presentation.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyxa_interview.domain.model.LedgerEntry
import com.example.nyxa_interview.domain.model.LedgerType
import com.example.nyxa_interview.ui.theme.PendingAmber
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerRoute(
    onBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LedgerScreen(state = state, padding = padding)
    }
}

@Composable
private fun LedgerScreen(state: WalletUiState, padding: PaddingValues) {
    if (state.ledger.isEmpty() && !state.isRefreshing) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("No activity yet", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.ledger, key = { it.id }) { entry ->
            LedgerRow(entry)
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
    val formatter = remember(entry.id) {
        DateTimeFormatter.ofPattern("MMM d, h:mm a")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = entry.type.label(), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = entry.at.atZone(ZoneId.systemDefault()).format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.isPending) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PendingAmber.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "Pending",
                            color = PendingAmber,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (entry.entriesDelta != 0L) {
                    Text(
                        text = "${if (entry.entriesDelta > 0) "+" else ""}${entry.entriesDelta} entries",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (entry.cashDeltaCents != 0L) {
                    Text(
                        text = "$${"%.2f".format(entry.cashDeltaCents / 100.0)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun LedgerType.label(): String = when (this) {
    LedgerType.PURCHASE -> "Purchase"
    LedgerType.SPIN -> "Spin to Win"
    LedgerType.BOX -> "Mystery Box"
    LedgerType.ADJUSTMENT -> "Adjustment"
}
