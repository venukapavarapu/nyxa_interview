package com.example.nyxa_interview.presentation.spin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

/** Constant angular speed of the wheel while waiting on the server: one full lap every 700ms. */
private const val FREE_SPIN_DEGREES_PER_MS = 360f / 700f

/**
 * Extra full rotations the landing animation spins through before settling. Kept small: since
 * the animation's duration is solved from distance / FREE_SPIN_DEGREES_PER_MS (see below) to
 * guarantee it never exceeds the free-spin speed, more laps directly means a longer landing —
 * this is the deliberate trade-off of "truly never speeds up" over "always exactly 4 seconds".
 */
private const val LANDING_EXTRA_ROTATIONS = 2

/**
 * Quadratic ease-out: f(t) = 1 - (1-t)^2. Its slope at t=0 is exactly 2 — i.e. the
 * instantaneous speed at the very start of a tween using this easing is always 2x the tween's
 * *average* speed (distance / duration), then decays smoothly and monotonically to a full stop
 * at t=1. It never speeds up anywhere in between.
 */
private const val LANDING_EASING_PEAK_FACTOR = 2f
private val LandingEasing = Easing { t -> 1f - (1f - t) * (1f - t) }

/**
 * A fully native prize wheel: segments are drawn from server data, and [targetSegmentIndex]
 * (null while idle) is the *already known* server result — the wheel never generates its own
 * outcome, it only visualizes one it has already been given.
 *
 * - While [isRequesting] is true and no target is known yet, the wheel spins at a constant
 *   angular speed, driven directly off the frame clock (60fps, tied to the display's own
 *   refresh) so tapping feels instantly responsive even before the network call resolves.
 * - The moment [targetSegmentIndex] arrives, the wheel decelerates smoothly onto the exact
 *   center of that segment. The deceleration's *duration* is computed per spin (not fixed) so
 *   that its peak instantaneous speed — which happens at the very first frame of landing,
 *   right when the API response arrives — is always exactly [FREE_SPIN_DEGREES_PER_MS], the
 *   same rate the wheel was already spinning at. It can only ever decelerate from there, never
 *   speed up, no matter how far away the target segment happens to land this time.
 *
 * The wheel itself is tappable as an alternate hit target for starting a spin, alongside the
 * Spin button. Both routes call the exact same [onSpinTapped] callback, which the caller gates
 * on the same `isSpinEnabled` state as the button — [enabled] here only controls whether this
 * tap target is reachable at all, so there is a single source of truth for the double-tap /
 * out-of-credits guard rather than two independently debounced click paths that could drift out
 * of sync.
 */
@Composable
fun PrizeWheel(
    segments: List<String>,
    targetSegmentIndex: Int?,
    isRequesting: Boolean,
    enabled: Boolean,
    onSpinTapped: () -> Unit,
    onLandingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return

    val rotation = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val segmentColors = remember(segments.size) { wheelColors(segments.size) }
    val sweep = 360f / segments.size

    LaunchedEffect(isRequesting, targetSegmentIndex) {

        // ------------------------------------------------------------
        // PHASE 1 — API request is in flight.
        // Wheel rotates at a constant angular velocity, driven directly off the frame clock
        // (not chained tween() calls, which would re-sync — and briefly change speed — at
        // every lap boundary).
        // ------------------------------------------------------------
        if (isRequesting && targetSegmentIndex == null) {
            var lastFrameNanos = 0L
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                if (lastFrameNanos != 0L) {
                    val deltaMs = (frameNanos - lastFrameNanos) / 1_000_000f
                    rotation.snapTo(rotation.value + FREE_SPIN_DEGREES_PER_MS * deltaMs)
                }
                lastFrameNanos = frameNanos
            }
            return@LaunchedEffect
        }

        // ------------------------------------------------------------
        // PHASE 2 — API response arrived. Do not increase speed: compute the exact final
        // rotation, then decelerate onto it starting from exactly the free-spin's own speed.
        // ------------------------------------------------------------
        if (targetSegmentIndex != null) {
            val targetIndex = targetSegmentIndex.coerceIn(0, segments.lastIndex)

            // Pointer is fixed at the top (-90deg); segment i's center sits at i*sweep + sweep/2.
            val targetAngle = targetIndex * sweep + sweep / 2f
            val currentAngle = ((rotation.value % 360f) + 360f) % 360f
            val delta = ((360f - targetAngle - currentAngle) % 360f + 360f) % 360f
            val distance = delta + LANDING_EXTRA_ROTATIONS * 360f
            val finalRotation = rotation.value + distance

            // duration is solved so that this tween's peak instantaneous speed — which for
            // LandingEasing occurs at t=0, right as landing begins — equals exactly
            // FREE_SPIN_DEGREES_PER_MS: peakSpeed = LANDING_EASING_PEAK_FACTOR * (distance /
            // duration), so duration = LANDING_EASING_PEAK_FACTOR * distance / FREE_SPIN_DEGREES_PER_MS.
            // A longer distance (more laps needed to reach the target) simply takes proportionally
            // longer at the same starting speed — it never has to move faster to compensate.
            val durationMs = (LANDING_EASING_PEAK_FACTOR * distance / FREE_SPIN_DEGREES_PER_MS).toInt()

            rotation.animateTo(
                targetValue = finalRotation,
                animationSpec = tween(durationMillis = durationMs, easing = LandingEasing),
            )
            // Remove any floating point rounding difference from the exact target.
            rotation.snapTo(finalRotation)

            onLandingFinished()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Spin",
                onClick = onSpinTapped,
            ),
        contentAlignment = Alignment.Center,
    ) {
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
