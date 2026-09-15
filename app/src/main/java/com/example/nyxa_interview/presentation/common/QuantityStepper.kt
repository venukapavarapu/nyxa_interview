package com.example.nyxa_interview.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A +/- quantity control shared by the product detail "how many to add" picker and the cart
 * line "increase/decrease what's already in the cart" control, so both look and behave
 * identically. [onDecrease] fires even at [quantity] == [minQuantity] so a caller that wants
 * "decrease past 1 removes the line" (the cart page) can implement that by observing the call
 * rather than needing a separate remove button; the product detail picker instead floors at 1
 * by simply not going below it in its own state update.
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 1,
    maxQuantity: Int = 10,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease, enabled = quantity > minQuantity || minQuantity <= 0) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(18.dp))
            }
            Box(modifier = Modifier.size(width = 28.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                Text(text = quantity.toString(), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onIncrease, enabled = quantity < maxQuantity) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
            }
        }
    }
}
