package com.example.nyxa_interview.presentation.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyxa_interview.domain.model.CartLine
import com.example.nyxa_interview.presentation.common.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutRoute(
    onBack: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val itemCount = state.cart?.totalQuantity ?: 0
                    Text(if (itemCount > 0) "Cart ($itemCount)" else "Cart")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        CheckoutScreen(state = state, padding = padding, onIntent = viewModel::onIntent)
    }

    state.orderConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(CheckoutIntent.DismissConfirmation) },
            title = { Text("Order placed") },
            text = { Text("You earned ${confirmation.entriesAwarded} entries. Order ${confirmation.orderId}.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.onIntent(CheckoutIntent.DismissConfirmation)
                    onBack()
                }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun CheckoutScreen(
    state: CheckoutUiState,
    padding: PaddingValues,
    onIntent: (CheckoutIntent) -> Unit,
) {
    val cart = state.cart
    if (cart == null || cart.lines.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Your cart is empty")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(cart.lines, key = { it.variant.id }) { line ->
                CartLineRow(
                    line = line,
                    isUpdating = line.variant.id in state.linesUpdating,
                    onIncrease = { onIntent(CheckoutIntent.IncreaseQuantity(line.variant.id, line.quantity)) },
                    onDecrease = { onIntent(CheckoutIntent.DecreaseQuantity(line.variant.id, line.quantity)) },
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", style = MaterialTheme.typography.titleMedium)
                Text("$${"%.2f".format(cart.subtotalCents / 100.0)}", style = MaterialTheme.typography.titleMedium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entries earned", style = MaterialTheme.typography.bodyMedium)
                Text("${cart.totalEntries}", style = MaterialTheme.typography.bodyMedium)
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = { onIntent(CheckoutIntent.PlaceOrder) },
                enabled = state.canPlaceOrder,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (state.isPlacingOrder) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Place order")
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(
    line: CartLine,
    isUpdating: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.product.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = line.variant.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$${"%.2f".format(line.lineTotalCents / 100.0)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isUpdating) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp).padding(8.dp))
        } else {
            QuantityStepper(
                quantity = line.quantity,
                onIncrease = onIncrease,
                onDecrease = onDecrease,
                minQuantity = 0,
            )
        }
    }
}
