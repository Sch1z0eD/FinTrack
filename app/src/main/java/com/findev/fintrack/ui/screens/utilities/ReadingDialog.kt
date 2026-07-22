package com.findev.fintrack.ui.screens.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import com.findev.fintrack.ui.AppAssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.findev.fintrack.ui.AppTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.ui.GlassAlertDialog
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMilli
import com.findev.fintrack.ui.formatMinor

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Entering a reading, with the money worked out as you type.
 *
 * A meter reading is easy to mistype and hard to check by eye - 16377 and 163770 look
 * much the same at a glance. Showing the consumption and the roubles before saving turns
 * a stray digit into an obvious one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDialog(
    state: ReadingDialogState,
    onValueChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val unit = stringResource(state.unitRes)

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_title, state.meterName), maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = state.previousMilli?.let {
                        stringResource(R.string.reading_previous, formatMilli(it), unit)
                    } ?: stringResource(R.string.reading_previous_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AppTextField(
                    value = state.valueText,
                    onValueChange = onValueChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.reading_value)) },
                    suffix = { Text(unit) },
                    isError = state.isBelowPrevious,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.details_date),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppAssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(dateLabel(state.dateEpochDay)) },
                    )
                }

                if (state.isBelowPrevious) {
                    Text(
                        text = stringResource(R.string.reading_below_previous),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val consumed = state.consumedMilli
                val charge = state.chargeMinor
                if (state.isBaseline) {
                    Text(
                        text = stringResource(R.string.reading_baseline_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.valueText.isNotEmpty() && consumed != null && charge != null) {
                    Text(
                        text = stringResource(R.string.reading_consumed, formatMilli(consumed), unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Water: show supply and drainage apart so the total is not a mystery.
                    val supply = state.supplyMinor
                    val drainage = state.drainageMinor
                    if (state.hasDrainage && supply != null && drainage != null) {
                        Text(
                            text = stringResource(
                                R.string.reading_water_split,
                                formatMinor(supply),
                                formatMinor(drainage),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.money_with_currency, formatMinor(charge)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canSave) {
                Text(stringResource(R.string.quick_entry_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_create_cancel))
            }
        },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dateEpochDay * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateChange(it / MILLIS_PER_DAY) }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.quick_entry_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.account_create_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
