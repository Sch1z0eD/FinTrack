package com.findev.fintrack.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import kotlin.math.cos
import kotlin.math.sin

/**
 * A slow, living gradient for one hero surface.
 *
 * Not the same thing as the gradients that were stripped out of this app earlier. Those
 * were static top-to-bottom fades painted on every card to fake a lit bevel - the trick
 * that dates an interface instantly. This is a single large field of colour that drifts,
 * used on exactly one surface, where the movement is the point and there is no bevel being
 * imitated.
 *
 * Rules that keep it from becoming the thing it replaced:
 *  - one surface per screen, never on list rows;
 *  - a full cycle takes half a minute, so it is never caught moving, only noticed;
 *  - the colours are neighbours on the wheel, so it reads as one material shifting rather
 *    than two colours sliding past each other.
 */
@Composable
fun Modifier.heroGradient(): Modifier {
    val scheme = MaterialTheme.colorScheme

    val transition = rememberInfiniteTransition(label = "heroGradient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            // Long and linear: an eased loop visibly slows at the turn, which draws the eye
            // to the animation instead of leaving it in the background.
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "heroGradientPhase",
    )

    val start = scheme.primaryContainer
    val mid = scheme.secondaryContainer
    val end = scheme.surfaceContainerHigh

    return drawBehind {
        // The centre travels a small ellipse, so the light appears to move across the card
        // rather than the card changing colour.
        val travel = size.minDimension * 0.35f
        val centre = Offset(
            x = size.width / 2f + cos(phase) * travel,
            y = size.height / 2f + sin(phase) * travel * 0.6f,
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(start, mid, end),
                center = centre,
                // Comfortably larger than the card: a radius that ends inside it draws a
                // visible edge to the gradient, which looks like a rendering fault.
                radius = size.maxDimension * 1.1f,
            ),
        )
    }
}
