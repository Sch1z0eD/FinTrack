package com.findev.fintrack.ui.screens.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.ui.ChipRow
import com.findev.fintrack.ui.dateLabel

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanFormScreen(
    onDone: () -> Unit,
    viewModel: LoanFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.loan_form_edit else R.string.loan_form_new,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quick_entry_back),
                        )
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.loan_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                singleLine = true,
                label = { Text(stringResource(R.string.loan_name)) },
                placeholder = { Text(stringResource(R.string.loan_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            LoanTypeSelector(selected = state.type, onTypeChange = viewModel::onTypeChange)

            MoneyField(
                value = state.principalText,
                onValueChange = viewModel::onPrincipalChange,
                labelRes = R.string.loan_principal,
            )

            if (state.showsRate) {
                OutlinedTextField(
                    value = state.rateText,
                    onValueChange = viewModel::onRateChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.loan_rate)) },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.loan_installment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // This one surprises people, so it says so out loud rather than only in a total.
            if (state.type == LoanType.INTEREST_ONLY) {
                Text(
                    text = stringResource(R.string.loan_interest_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.loan_start_date),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateLabel(state.startDateEpochDay)) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.termText,
                    onValueChange = viewModel::onTermChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.loan_term)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.paymentDayText,
                    onValueChange = viewModel::onPaymentDayChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.loan_payment_day)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            MoneyField(
                value = state.upfrontFeeText,
                onValueChange = viewModel::onUpfrontFeeChange,
                labelRes = R.string.loan_upfront_fee,
            )
            MoneyField(
                value = state.monthlyFeeText,
                onValueChange = viewModel::onMonthlyFeeChange,
                labelRes = R.string.loan_monthly_fee,
            )

            OutlinedTextField(
                value = state.reminderDaysText,
                onValueChange = viewModel::onReminderDaysChange,
                singleLine = true,
                label = { Text(stringResource(R.string.loan_reminder_days)) },
                supportingText = { Text(stringResource(R.string.loan_reminder_days_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // Only needed once "Оплачено" has to post a real expense, so the loan stays
            // valid without them - but picking here beats picking every month.
            Text(
                text = stringResource(R.string.recurring_account),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                items = state.accounts.map { it.id to it.name },
                selectedId = state.selectedAccountId,
                onSelected = viewModel::onAccountSelected,
            )
            Text(
                text = stringResource(R.string.recurring_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                items = state.categories.map { it.id to "${it.icon} ${it.name}" },
                selectedId = state.selectedCategoryId,
                onSelected = viewModel::onCategorySelected,
            )

            Button(
                onClick = viewModel::onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Text(stringResource(R.string.loan_save))
            }
        }
    }

    // A loan drags its rate history and prepayments with it, and one stray tap on a
    // top-bar icon should not take all of that.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.loan_delete)) },
            text = { Text(stringResource(R.string.loan_delete_confirm, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.onDelete()
                    },
                ) {
                    Text(stringResource(R.string.accounts_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.account_create_cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDateEpochDay * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            viewModel.onStartDateChange(it / MILLIS_PER_DAY)
                        }
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

@Composable
private fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(stringResource(labelRes)) },
        suffix = { Text(stringResource(R.string.money_with_currency, "")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanTypeSelector(
    selected: LoanType,
    onTypeChange: (LoanType) -> Unit,
) {
    // Four labels do not fit across a phone as segments, so the type moved to chips.
    val options = listOf(
        LoanType.ANNUITY to R.string.loan_type_annuity,
        LoanType.DIFFERENTIATED to R.string.loan_type_differentiated,
        LoanType.INSTALLMENT to R.string.loan_type_installment,
        LoanType.INTEREST_ONLY to R.string.loan_type_interest_only,
    )

    ChipRow(
        items = options.map { (type, labelRes) -> type.name to stringResource(labelRes) },
        selectedId = selected.name,
        onSelected = { onTypeChange(LoanType.valueOf(it)) },
    )
}
