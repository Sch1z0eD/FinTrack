package com.findev.fintrack.ui.screens.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import com.findev.fintrack.ui.AppTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.ui.GlassAlertDialog
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.shortDate

/**
 * Settling several future occurrences at once.
 *
 * Deliberately asks for a count rather than a sum: a recurring payment has no interest and
 * no balance to reduce, so paying ahead is not a question of money doing anything clever -
 * it is "how many of these am I covering". The sum follows from the count, and the dialog
 * says which date it reaches so the answer is never a guess.
 */
@Composable
fun PayAheadDialog(
    state: PayAheadDialogState,
    onCountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_pay_ahead_title, state.name), maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = state.countText,
                    onValueChange = onCountChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.recurring_pay_ahead_count)) },
                    supportingText = state.maxCount?.let {
                        { Text(stringResource(R.string.recurring_pay_ahead_max, it)) }
                    },
                    isError = state.maxCount != null && state.count > state.maxCount,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.coversUpTo?.let { covers ->
                    Text(
                        text = stringResource(R.string.recurring_pay_ahead_covers, shortDate(covers)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.count > 0) {
                    Text(
                        text = stringResource(
                            R.string.money_with_currency,
                            formatMinor(state.totalMinor),
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canSave) {
                Text(stringResource(R.string.prepayment_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_create_cancel))
            }
        },
    )
}
