package com.example.nyxa_interview.presentation.box

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nyxa_interview.domain.model.BoxResult

private const val CHECKPOINT_STEP_MS = 260

/**
 * Native reveal animation driven entirely by the server-provided [BoxResult.revealSequence]:
 * each checkpoint is a fractional 0..1 pause/scale beat (a "shake and build tension" cadence)
 * before landing on the final prize scale. The prize amount itself is never computed here —
 * it is read straight from [result].
 */
@Composable
fun BoxRevealAnimation(
    result: BoxResult,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(result.resultId) {
        for (checkpoint in result.revealSequence) {
            scale.animateTo(
                targetValue = 0.85f + checkpoint * 0.1f,
                animationSpec = tween(CHECKPOINT_STEP_MS, easing = LinearEasing),
            )
        }
        scale.animateTo(1.15f, animationSpec = tween(220))
        scale.animateTo(1f, animationSpec = tween(160))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale.value)
            .clip(RoundedCornerShape(24.dp))
            .background(tierColor(result.tier.name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$${"%.2f".format(result.prize.amountCents / 100.0)}",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

private fun tierColor(tier: String): Color = when (tier) {
    "BRONZE" -> Color(0xFFCD7F32)
    "SILVER" -> Color(0xFFB0B0B0)
    "GOLD" -> Color(0xFFD4AF37)
    "PLATINUM" -> Color(0xFF8892A0)
    else -> Color(0xFF7EC8E3)
}
