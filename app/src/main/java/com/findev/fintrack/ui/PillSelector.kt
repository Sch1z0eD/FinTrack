package com.findev.fintrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A borderless segmented control.
 *
 * Replaces Material's SingleChoiceSegmentedButtonRow, whose outlined buttons and check mark
 * read as heavy furniture here. This is a tonal capsule track with the chosen option filled
 * by the accent; the selection animates its colour rather than snapping, and there is no
 * outline anywhere. Options share the width equally, like the segmented control did.
 */
@Composable
fun <T> PillSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(track)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val container by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "pillContainer",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "pillContent",
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(track)
                    .background(container)
                    .clickable { onSelected(value) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            )
        }
    }
}

/** Assist chip without its outline: a soft tonal fill instead of a border. */
@Composable
fun borderlessAssistChipColors(): ChipColors = AssistChipDefaults.assistChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

/** Filter chip without its outline: tonal fill unselected, accent container selected. */
@Composable
fun borderlessFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
)

/** Assist chip (a tap target - date pickers, actions) without its outline. */
@Composable
fun AppAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        border = null,
        colors = borderlessAssistChipColors(),
    )
}

/** Filter chip (a toggle) without its outline. */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        border = null,
        colors = borderlessFilterChipColors(),
    )
}
