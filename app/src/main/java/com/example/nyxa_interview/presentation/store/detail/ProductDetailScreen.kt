package com.example.nyxa_interview.presentation.store.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.domain.model.ProductVariant
import com.example.nyxa_interview.presentation.checkout.CartIconButton
import com.example.nyxa_interview.presentation.common.QuantityStepper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailRoute(
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ProductDetailEffect.AddedToCart -> coroutineScope.launch {
                    snackbarHostState.showSnackbar("${effect.productTitle} added to cart")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.product?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { CartIconButton(onClick = onOpenCart) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val product = state.product
        if (product == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Product not found")
            }
        } else {
            ProductDetailScreen(
                product = product,
                state = state,
                padding = padding,
                onIntent = viewModel::onIntent,
            )
        }
    }
}

@Composable
private fun ProductDetailScreen(
    product: Product,
    state: ProductDetailUiState,
    padding: PaddingValues,
    onIntent: (ProductDetailIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        Text(
            text = "$${"%.2f".format(product.priceCents / 100.0)}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "${product.entryCount} entries" + (product.multiplier?.let { " (${it}X)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Text(
            text = "Variant",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            product.variants.forEach { variant ->
                VariantChip(
                    variant = variant,
                    selected = variant.id == state.selectedVariantId,
                    onClick = { onIntent(ProductDetailIntent.VariantSelected(variant.id)) },
                )
            }
        }

        Text(
            text = "Quantity",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        QuantityStepper(
            quantity = state.quantity,
            onIncrease = { onIntent(ProductDetailIntent.QuantityChanged(state.quantity + 1)) },
            onDecrease = { onIntent(ProductDetailIntent.QuantityChanged(state.quantity - 1)) },
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = { onIntent(ProductDetailIntent.AddToCart) },
            enabled = state.canAddToCart,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
        ) {
            if (state.isAddingToCart) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Add to cart")
            }
        }
    }
}

@Composable
private fun VariantChip(variant: ProductVariant, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = variant.available,
        label = { Text(if (variant.available) variant.label else "${variant.label} (Sold out)") },
    )
}
