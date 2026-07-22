package com.findev.fintrack.ui.screens.payments

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.findev.fintrack.ui.AppTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.findev.fintrack.data.local.entity.RecurrencePeriod
import com.findev.fintrack.ui.AppAssistChip
import com.findev.fintrack.ui.ChipRow
import com.findev.fintrack.ui.PillSelector
import com.findev.fintrack.ui.dateLabel

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringFormScreen(
    onDone: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: RecurringFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var datePickerFor by remember { mutableStateOf<DateField?>(null) }

    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) {
                                R.string.recurring_form_edit
                            } else {
                                R.string.recurring_form_new
                            },
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
                        IconButton(onClick = viewModel::onDelete) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.recurring_delete),
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
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                singleLine = true,
                label = { Text(stringResource(R.string.recurring_name)) },
                placeholder = { Text(stringResource(R.string.recurring_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            AppTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                singleLine = true,
                label = { Text(stringResource(R.string.recurring_amount)) },
                suffix = { Text(stringResource(R.string.money_with_currency, "")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(R.string.recurring_period)
            PeriodSelector(selected = state.period, onPeriodChange = viewModel::onPeriodChange)

            DateRow(
                labelRes = R.string.recurring_start,
                label = dateLabel(state.startDateEpochDay),
                onClick = { datePickerFor = DateField.START },
            )

            // Open-ended is the common case - a subscription runs until it is cancelled -
            // so the end date only appears once it is actually wanted.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recurring_end),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val end = state.endDateEpochDay
                if (end == null) {
                    AppAssistChip(
                        onClick = { datePickerFor = DateField.END },
                        label = { Text(stringResource(R.string.recurring_end_set)) },
                    )
                    Text(
                        text = stringResource(R.string.recurring_end_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AppAssistChip(
                        onClick = { datePickerFor = DateField.END },
                        label = { Text(dateLabel(end)) },
                    )
                    TextButton(onClick = { viewModel.onEndDateChange(null) }) {
                        Text(stringResource(R.string.recurring_end_never))
                    }
                }
            }

            FieldLabel(R.string.recurring_account)
            // Without an account there is nothing to charge the payment to, and the save
            // button would just sit there disabled with no explanation.
            if (state.accounts.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.quick_entry_no_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppAssistChip(
                        onClick = onOpenAccounts,
                        label = { Text(stringResource(R.string.quick_entry_no_account_action)) },
                    )
                }
            } else {
                ChipRow(
                    items = state.accounts.map { it.id to it.name },
                    selectedId = state.selectedAccountId,
                    onSelected = viewModel::onAccountSelected,
                )
            }

            FieldLabel(R.string.recurring_category)
            ChipRow(
                items = state.categories.map { it.id to "${it.icon} ${it.name}" },
                selectedId = state.selectedCategoryId,
                onSelected = viewModel::onCategorySelected,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recurring_reminder),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.reminderEnabled,
                    onCheckedChange = viewModel::onReminderChange,
                )
            }

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

    datePickerFor?.let { field ->
        val initial = when (field) {
            DateField.START -> state.startDateEpochDay
            DateField.END -> state.endDateEpochDay ?: state.startDateEpochDay
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val epochDay = millis / MILLIS_PER_DAY
                            when (field) {
                                DateField.START -> viewModel.onStartDateChange(epochDay)
                                DateField.END -> viewModel.onEndDateChange(epochDay)
                            }
                        }
                        datePickerFor = null
                    },
                ) {
                    Text(stringResource(R.string.quick_entry_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerFor = null }) {
                    Text(stringResource(R.string.account_create_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Which date the one picker is currently editing. */
private enum class DateField { START, END }

@Composable
private fun FieldLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DateRow(
    labelRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        AppAssistChip(onClick = onClick, label = { Text(label) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    selected: RecurrencePeriod,
    onPeriodChange: (RecurrencePeriod) -> Unit,
) {
    val options = RecurrencePeriod.entries

    PillSelector(
        options = options.map { it to stringResource(it.shortLabelRes()) },
        selected = selected,
        onSelected = onPeriodChange,
        modifier = Modifier.fillMaxWidth(),
    )
}
