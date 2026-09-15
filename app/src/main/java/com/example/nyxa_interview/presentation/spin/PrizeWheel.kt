package com.example.nyxa_interview.presentation.spin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private const val SPIN_DURATION_MS = 4200
private const val EXTRA_FULL_ROTATIONS = 6

/**
 * A fully native prize wheel: segments are drawn from server data, and [targetSegmentIndex]
 * (null while idle) is the *already known* server result. The wheel spins several full
 * rotations then decelerates (ease-out cubic) onto the exact center of that segment — it never
 * generates its own outcome, it only visualizes one it has already been given.
 */
@Composable
fun PrizeWheel(
    segments: List<String>,
    targetSegmentIndex: Int?,
    onLandingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val segmentColors = remember(segments.size) { wheelColors(segments.size) }
    val sweep = 360f / segments.size

    LaunchedEffect(targetSegmentIndex) {
        val target = targetSegmentIndex ?: return@LaunchedEffect
        // Land on the middle of the target segment; pointer is fixed at the top (270deg / -90deg).
        val segmentCenter = target * sweep + sweep / 2f
        val currentMod = rotation.value % 360f
        val delta = (360f - segmentCenter - currentMod + 360f) % 360f
        val finalRotation = rotation.value + delta + EXTRA_FULL_ROTATIONS * 360f

        rotation.animateTo(
            targetValue = finalRotation,
            animationSpec = tween(
                durationMillis = SPIN_DURATION_MS,
                easing = CubicBezierEasing(0.12f, 0.85f, 0.2f, 1f),
            ),
        )
        onLandingFinished()
    }

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            rotate(degrees = rotation.value, pivot = center) {
                segments.forEachIndexed { index, label ->
                    val startAngle = index * sweep - 90f
                    drawArc(
                        color = segmentColors[index],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    )

                    val midAngleRad = Math.toRadians((startAngle + sweep / 2).toDouble())
                    val textRadius = radius * 0.62f
                    val textX = center.x + textRadius * cos(midAngleRad).toFloat()
                    val textY = center.y + textRadius * sin(midAngleRad).toFloat()
                    val measured = textMeasurer.measure(
                        text = label,
                        style = TextStyle(color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(textX - measured.size.width / 2f, textY - measured.size.height / 2f),
                    )
                }
            }

            // Fixed pointer at the top.
            val pointerPath = Path().apply {
                moveTo(center.x - 14f, 12f)
                lineTo(center.x + 14f, 12f)
                lineTo(center.x, 40f)
                close()
            }
            drawPath(pointerPath, color = Color(0xFFC8102E))
        }
    }
}

private fun wheelColors(count: Int): List<Color> {
    val palette = listOf(
        Color(0xFFC8102E), Color(0xFF1B1B1B), Color(0xFF9C6B00), Color(0xFF2E2E2E),
    )
    return List(count) { palette[it % palette.size] }
}
