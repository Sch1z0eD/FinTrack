package com.findev.fintrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.PeriodSelection
import com.findev.fintrack.data.StatPeriod
import com.findev.fintrack.ui.screens.statistics.labelRes
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The filters above a list, as two dropdowns on one line.
 *
 * They were a scrolling chip row plus a segmented control: seven period chips meant the
 * later ones were off-screen and the current choice could scroll out of sight, and the
 * segmented control had to be squeezed to a fixed width to sit beside them. A dropdown
 * shows the current value in one word and hides the rest until asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodFilterBar(
    selection: PeriodSelection,
    onPeriodChange: (StatPeriod) -> Unit,
    onCustomRangeChange: (Long?, Long?) -> Unit,
    modifier: Modifier = Modifier,
    /** Rendered on the same line, before the period control. */
    leading: (@Composable () -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val from = selection.customFromEpochDay
    val to = selection.customToEpochDay

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()

        // A custom range names its dates instead of the word "свой период": the dates are
        // the useful part, and the label is where you tap to change them.
        val periodLabel = if (selection.period == StatPeriod.CUSTOM && from != null && to != null) {
            stringResource(
                R.string.period_range,
                shortDate(LocalDate.ofEpochDay(from)),
                shortDate(LocalDate.ofEpochDay(to)),
            )
        } else {
            stringResource(selection.period.labelRes())
        }

        FilterDropdown(
            label = periodLabel,
            options = StatPeriod.entries.map { it to stringResource(it.labelRes()) },
            selected = selection.period,
            onSelected = { picked ->
                onPeriodChange(picked)
                if (picked == StatPeriod.CUSTOM) showPicker = true
            },
        )

        if (selection.period == StatPeriod.CUSTOM) {
            TextButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.period_custom_pick))
            }
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from?.times(MILLIS_PER_DAY),
            initialSelectedEndDateMillis = to?.times(MILLIS_PER_DAY),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    // Both ends or nothing: a range with one end set filters nothing, so
                    // confirming it would look like the picker had failed.
                    enabled = state.selectedStartDateMillis != null &&
                        state.selectedEndDateMillis != null,
                    onClick = {
                        onCustomRangeChange(
                            state.selectedStartDateMillis?.div(MILLIS_PER_DAY),
                            state.selectedEndDateMillis?.div(MILLIS_PER_DAY),
                        )
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.quick_entry_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.account_create_cancel))
                }
            },
        ) {
            DateRangePicker(state = state)
        }
    }
}

/**
 * A chip that opens a menu of choices.
 *
 * Deliberately not ExposedDropdownMenuBox: that renders a text field, which reads as
 * something to type into. This is a filter, and a chip says so.
 */
@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            // No outline: a filled tonal pill instead, so a filter reads as one soft chip
            // rather than a bordered box.
            border = null,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                    trailingIcon = {
                        if (value == selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}
