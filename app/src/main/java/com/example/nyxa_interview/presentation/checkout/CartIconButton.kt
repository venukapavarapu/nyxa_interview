package com.example.nyxa_interview.presentation.checkout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

/** Cart icon with a live item-count badge, reused on the Store grid and Product detail app bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartIconButton(
    onClick: () -> Unit,
    viewModel: CartCountViewModel = hiltViewModel(),
) {
    val itemCount by viewModel.itemCount.collectAsState()

    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (itemCount > 0) {
                    Badge { Text(if (itemCount > 99) "99+" else itemCount.toString()) }
                }
            }
        ) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart, $itemCount items")
        }
    }
}
