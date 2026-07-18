package com.findev.fintrack.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/*
 * Shared surfaces for the app.
 *
 * Flat on purpose. Depth here comes from tonal steps in the colour scheme - a panel sits a
 * shade above the background and that is the whole effect. No gradients, no drop shadows,
 * no borders drawn to fake a lit edge: those read as dated the moment they are noticed,
 * and they fight the dynamic-colour palette instead of using it.
 *
 * The one genuinely translucent surface in the app is the floating bottom bar in
 * FinTrackApp, which samples and blurs the NavHost's GraphicsLayer. That is real, so it
 * looks real. Nothing here tries to imitate it.
 */

/*
 * Corner radii follow the Material 3 shape scale: XS 4, S 8, M 12, L 16, XL 28.
 *
 * 16 (Large) is the card and list-row size; 24-28 is what dialogs and bottom sheets use.
 * Rows were built at 24 first and read as bloated - at list density the corner eats the
 * row, and a column of them looks like a bag of pills rather than a list.
 */
val RowCorner = 16.dp
val PanelCorner = 20.dp
val FieldShape = RoundedCornerShape(14.dp)

/** Flat tonal fill: one step above the background, nothing else. */
@Composable
fun Modifier.panelSurface(
    shape: Shape = RoundedCornerShape(PanelCorner),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier = this
    .clip(shape)
    .background(color)

/** Panel with padding. Replaces Material's [androidx.compose.material3.Card] and its elevation. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .panelSurface(color = color)
            // Ripple has to land after the clip, or it paints square corners.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Context menu.
 *
 * Opaque, and the only place a shadow is used: a menu floats above the content it covers,
 * and without any lift it reads as part of the list underneath it.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        content = content,
    )
}

/**
 * Progress bar, animated to its target instead of snapping.
 *
 * Material 3 draws a "stop indicator" dot at the end of the track by default, in the same
 * colour as the bar. It is always there, at every progress value, which reads as a stray
 * blob of primary parked on the right. [drawStopIndicator] is emptied to remove it.
 */
@Composable
fun FinTrackProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "progress",
    )

    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        trackColor = progressTrackColor(),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Indeterminate variant, styled to match [FinTrackProgress]. */
@Composable
fun FinTrackProgress(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        trackColor = progressTrackColor(),
        strokeCap = StrokeCap.Round,
    )
}

/**
 * The unfilled part of a progress bar.
 *
 * Deliberately an alpha over onSurface rather than a surface tone. A tonal step only shows
 * up if you know which surface the bar was dropped on, and these bars sit on panels that
 * are already surfaceContainer - so the track vanished and only the filled stripe was
 * visible, with nothing to show how far along it was. An alpha contrasts against whatever
 * is behind it, in both themes.
 */
@Composable
private fun progressTrackColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

/**
 * Colours for the filled [androidx.compose.material3.TextField], not the outlined one.
 *
 * The outlined field draws its label in a gap cut out of its own border, and fills that gap
 * with whatever is behind the field rather than with the field's own container. Give it a
 * filled container and every floating label ends up sitting on a dark patch of the parent
 * surface - the "чёрные плашки под названиями". The filled variant has no cutout: the label
 * lives inside the fill, so the box stays whole.
 *
 * Always the top tonal step, so fields stand clear of [dialogContainerColor] two below.
 */
@Composable
fun fieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = scheme.surfaceContainerHighest,
        unfocusedContainerColor = scheme.surfaceContainerHighest,
        disabledContainerColor = scheme.surfaceContainerHigh,
        // The filled variant underlines instead of boxing, so the indicator carries focus.
        focusedIndicatorColor = scheme.primary,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
}

/**
 * Dialog background. Sits low on the tonal scale on purpose, so the fields inside it can
 * sit high and still read as separate boxes - see [fieldColors].
 */
@Composable
fun dialogContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow
