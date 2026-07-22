package com.findev.fintrack.ui.screens.loans

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.GlassAlertDialog
import com.findev.fintrack.ui.fieldColors

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Adds a step to the loan's rate.
 *
 * The date is what the contract's own schedule implies, not necessarily the date printed
 * beside the rate: a contract can say "from 02.05.2026" while charging the whole
 * 01.05 - 01.06 period at the new rate, in which case the change takes effect on the 1st.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateChangeDialog(
    state: RateDialogState,
    onRateChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loan_rate_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = state.rateText,
                    onValueChange = onRateChange,
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors(),
                    label = { Text(stringResource(R.string.loan_rate)) },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loan_rate_effective_from),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppAssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(dateLabel(state.dateEpochDay)) },
                    )
                }

                Text(
                    text = stringResource(R.string.loan_rate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
