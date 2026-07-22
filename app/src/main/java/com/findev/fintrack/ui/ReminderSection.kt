package com.findev.fintrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R

/**
 * Reminder on/off plus how far ahead - shared by loans and recurring payments so both offer
 * the same lead-time presets and multi-select.
 *
 * The switch exists because the previous version had none: a reminder was turned off by
 * clearing the days field, which nobody discovers, so the screen read as having no reminder
 * setting at all. Multi-select because one warning is either too early to act on or too late
 * to move money, so "за неделю" and "за день" are meant to be picked together.
 */
@Composable
fun ReminderSection(
    enabled: Boolean,
    selectedDays: List<Int>,
    onEnabledChange: (Boolean) -> Unit,
    onDayToggle: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .toggleable(value = enabled, onValueChange = onEnabledChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reminder_enabled),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // Null: the row owns the gesture, so the switch must not claim it too.
        Switch(checked = enabled, onCheckedChange = null)
    }

    AnimatedVisibility(visible = enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                REMINDER_PRESETS.forEach { (days, labelRes) ->
                    AppFilterChip(
                        selected = days in selectedDays,
                        onClick = { onDayToggle(days) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            if (selectedDays.isEmpty()) {
                Text(
                    text = stringResource(R.string.reminder_pick_at_least_one),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Lead times worth one tap. */
private val REMINDER_PRESETS = listOf(
    0 to R.string.reminder_same_day,
    1 to R.string.reminder_one_day,
    3 to R.string.reminder_three_days,
    7 to R.string.reminder_week,
)
