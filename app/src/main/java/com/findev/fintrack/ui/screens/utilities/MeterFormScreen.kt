package com.findev.fintrack.ui.screens.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.findev.fintrack.ui.AppTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterType
import com.findev.fintrack.ui.ChipRow
import com.findev.fintrack.ui.ConfirmDeleteDialog
import com.findev.fintrack.ui.formatMinor

/** Sentinel chip id for "no group" - ChipRow keys on strings, and null is not one. */
private const val NO_GROUP_ID = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterFormScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: MeterFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }
    LaunchedEffect(Unit) { viewModel.deleted.collect { onDeleted() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.meter_form_edit else R.string.meter_form_new,
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
                                contentDescription = stringResource(R.string.meter_delete),
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
                label = { Text(stringResource(R.string.meter_name)) },
                placeholder = { Text(stringResource(R.string.meter_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.meter_type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                items = MeterType.entries.map { it.name to stringResource(it.labelRes()) },
                selectedId = state.type.name,
                onSelected = { viewModel.onTypeChange(MeterType.valueOf(it)) },
            )

            Text(
                text = stringResource(R.string.meter_billing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                items = BillingKind.entries.map { it.name to stringResource(it.labelRes()) },
                selectedId = state.billing.name,
                onSelected = { viewModel.onBillingChange(BillingKind.valueOf(it)) },
            )

            // A fixed fee stores a monthly sum, not a per-unit tariff, so the label changes;
            // otherwise it is priced per whatever the meter counts, so it follows the type.
            AppTextField(
                value = state.tariffText,
                onValueChange = viewModel::onTariffChange,
                singleLine = true,
                label = {
                    Text(
                        if (state.isFixed) {
                            stringResource(R.string.meter_fixed_amount)
                        } else {
                            stringResource(R.string.meter_tariff, stringResource(state.type.unitRes()))
                        },
                    )
                },
                suffix = { Text(stringResource(R.string.money_with_currency, "")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            // The hint is about past readings keeping their tariff - only metered has those.
            if (state.isEditing && state.isMetered) {
                Text(
                    text = stringResource(R.string.meter_tariff_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Water is billed for supply and for drainage on the same volume, so a water
            // meter takes a second tariff; other meters have no such line.
            if (state.isWater) {
                AppTextField(
                    value = state.drainageText,
                    onValueChange = viewModel::onDrainageChange,
                    singleLine = true,
                    label = {
                        Text(stringResource(R.string.meter_drainage, stringResource(state.type.unitRes())))
                    },
                    suffix = { Text(stringResource(R.string.money_with_currency, "")) },
                    supportingText = { Text(stringResource(R.string.meter_drainage_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.isNorm) {
                // No readings to submit, so no reminder day: what is asked for instead is the
                // fixed monthly volume, and what it costs is shown straight away.
                AppTextField(
                    value = state.normText,
                    onValueChange = viewModel::onNormChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.meter_norm)) },
                    suffix = { Text(stringResource(state.type.unitRes())) },
                    supportingText = { Text(stringResource(R.string.meter_norm_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.monthlyChargeMinor?.let { charge ->
                    Text(
                        text = stringResource(
                            R.string.meter_norm_monthly,
                            stringResource(R.string.money_with_currency, formatMinor(charge)),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else if (state.isMetered) {
                AppTextField(
                    value = state.reminderDayText,
                    onValueChange = viewModel::onReminderDayChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.meter_reminder_day)) },
                    supportingText = { Text(stringResource(R.string.meter_reminder_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // FIXED needs nothing more - the amount above is the whole month.

            // Groups are made on the ЖКХ screen; here a service just picks one, or none.
            if (state.groups.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.meter_group),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow(
                    items = listOf(NO_GROUP_ID to stringResource(R.string.group_none)) +
                        state.groups.map { it.id to it.name },
                    selectedId = state.groupId ?: NO_GROUP_ID,
                    onSelected = { viewModel.onGroupChange(it.takeIf { id -> id != NO_GROUP_ID }) },
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

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.meter_delete),
            message = stringResource(R.string.meter_delete_confirm, state.name),
            onConfirm = viewModel::onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}
