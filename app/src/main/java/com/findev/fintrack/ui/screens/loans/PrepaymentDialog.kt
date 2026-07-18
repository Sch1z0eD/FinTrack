package com.findev.fintrack.ui.screens.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.PrepaymentMode
import com.findev.fintrack.loanengine.PrepaymentEffect
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.shortDate
import kotlin.math.abs

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Ask for a prepayment and answer what it would do, both ways, before it is made.
 *
 * The comparison is not a preview of the form - it is the form: the two modes are the
 * two choices, so seeing what each costs is how the choice gets made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepaymentDialog(
    state: PrepaymentDialogState,
    onAmountChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onModeChange: (PrepaymentMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prepayment_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.prepayment_amount)) },
                    suffix = { Text(stringResource(R.string.money_with_currency, "")) },
                    // Done, not Next: this is the only field worth typing into. Some IMEs
                    // (Samsung's numeric pad among them) ignore the action and keep the
                    // keyboard up over the dialog's buttons; there the user closes it the
                    // usual way, because a dialog cannot resize around an IME.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    ),
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
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(dateLabel(state.dateEpochDay)) },
                    )
                }

                val dateError = state.dateError
                val simulation = state.simulation
                when {
                    dateError != null -> Text(
                        text = stringResource(
                            when (dateError) {
                                PrepaymentDateError.BEFORE_START -> R.string.prepayment_error_before_start
                                PrepaymentDateError.AFTER_CLOSING -> R.string.prepayment_error_after_closing
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    simulation == null -> Text(
                        text = stringResource(R.string.prepayment_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> {
                        ModeCard(
                            titleRes = R.string.prepayment_mode_term,
                            effect = simulation.reduceTerm,
                            selected = state.mode == PrepaymentMode.REDUCE_TERM,
                            onClick = { onModeChange(PrepaymentMode.REDUCE_TERM) },
                        )
                        ModeCard(
                            titleRes = R.string.prepayment_mode_payment,
                            effect = simulation.reducePayment,
                            selected = state.mode == PrepaymentMode.REDUCE_PAYMENT,
                            onClick = { onModeChange(PrepaymentMode.REDUCE_PAYMENT) },
                        )
                    }
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

/** One mode's answer. Both cards carry the same three facts so they can be read side by side. */
@Composable
private fun ModeCard(
    titleRes: Int,
    effect: PrepaymentEffect,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The card is the click target; the button only shows which one won.
                RadioButton(selected = selected, onClick = null)
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            }
            // A prepayment can cost more than it saves - see PrepaymentEffect.savedMinor.
            // Rare and small, but it is the whole answer, so it gets said plainly.
            val saved = effect.savedMinor
            Text(
                text = stringResource(
                    if (saved >= 0) R.string.prepayment_saved else R.string.prepayment_costs_more,
                    stringResource(R.string.money_with_currency, formatMinor(abs(saved))),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (saved >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = effect.paymentAfterMinor?.let {
                    stringResource(
                        R.string.prepayment_payment_after,
                        stringResource(R.string.money_with_currency, formatMinor(it)),
                    )
                } ?: stringResource(R.string.prepayment_closes_loan),
                style = MaterialTheme.typography.bodySmall,
            )
            val closing = stringResource(R.string.prepayment_closing, shortDate(effect.closingDate))
            val earlier = if (effect.paymentsSaved > 0) {
                pluralStringResource(
                    R.plurals.prepayment_payments_earlier,
                    effect.paymentsSaved,
                    effect.paymentsSaved,
                )
            } else {
                null
            }
            Text(
                text = listOfNotNull(closing, earlier).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
