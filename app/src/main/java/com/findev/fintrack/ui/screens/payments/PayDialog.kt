package com.findev.fintrack.ui.screens.payments

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextField
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
import com.findev.fintrack.ui.FieldShape
import com.findev.fintrack.ui.GlassAlertDialog
import com.findev.fintrack.ui.fieldColors
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.shortDate

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Confirms what was actually paid before any of it reaches the balance.
 *
 * The amount arrives prefilled but stays editable, and the date defaults to today rather
 * than to the due date: a bill can be settled late, and for anything metered - utilities
 * above all - the number owed and the number paid are rarely the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayDialog(
    state: PayDialogState,
    onAmountChange: (String) -> Unit,
    onPartialChange: (Boolean) -> Unit,
    onDateChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.name, maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.payment_settles, shortDate(state.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextField(
                    value = state.amountText,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors(),
                    label = { Text(stringResource(R.string.recurring_amount)) },
                    suffix = { Text(stringResource(R.string.money_with_currency, "")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Only offered once the amount differs from what was due: when they match
                // there is nothing to decide, and a switch with one sensible position is
                // just another thing to read.
                if (state.dueAmountMinor > 0 && state.amountMinor != state.dueAmountMinor) {
                    val options = listOf(
                        false to R.string.payment_full,
                        true to R.string.payment_partial,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (partial, labelRes) ->
                            SegmentedButton(
                                selected = state.isPartial == partial,
                                onClick = { onPartialChange(partial) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            if (state.isPartial) {
                                R.string.payment_partial_hint
                            } else {
                                R.string.payment_full_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
