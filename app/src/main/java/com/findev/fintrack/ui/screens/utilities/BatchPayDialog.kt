package com.findev.fintrack.ui.screens.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.ui.ChipRow
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMinor

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Confirms paying several ticked services at once. Each line becomes its own expense in the
 * ЖКХ category settling its own service, so the paid marks stay per-service - but the user
 * sees one list, one total, and presses once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPayDialog(
    state: BatchPayDialogState,
    accounts: List<AccountEntity>,
    onAccountChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.pay_batch_title, state.items.size, state.items.size))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = item.note, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.money_with_currency, formatMinor(item.amountMinor)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.pay_batch_total), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.money_with_currency, formatMinor(state.totalMinor)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (accounts.size > 1) {
                    Text(
                        text = stringResource(R.string.details_account),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChipRow(
                        items = accounts.map { it.id to it.name },
                        selectedId = state.accountId,
                        onSelected = onAccountChange,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.details_date), style = MaterialTheme.typography.bodyMedium)
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(dateLabel(state.dateEpochDay)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canSave) {
                Text(stringResource(R.string.pay_selected))
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
