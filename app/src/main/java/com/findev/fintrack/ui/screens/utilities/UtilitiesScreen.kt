package com.findev.fintrack.ui.screens.utilities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.ui.UndoSnackbarHost
import com.findev.fintrack.ui.showUndo
import com.findev.fintrack.data.monthlyChargeMinor
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.ui.AppMenu
import com.findev.fintrack.ui.NotificationPermissionRequest
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.floatingBottomBarSpace
import com.findev.fintrack.ui.formatMilli
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.screens.payments.PAID_GREEN

@Composable
fun UtilitiesScreen(
    onAddMeter: () -> Unit,
    onOpenMeter: (String) -> Unit,
    onSubmitGroupReadings: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UtilitiesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readingDialog by viewModel.readingDialog.collectAsStateWithLifecycle()
    val groupDialog by viewModel.groupDialog.collectAsStateWithLifecycle()
    val payDialog by viewModel.payDialog.collectAsStateWithLifecycle()
    val paidUndo by viewModel.paidUndo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    // A metered meter with a reminder day is the reason the reading reminder needs to be
    // able to post - ask once one exists.
    NotificationPermissionRequest(
        needed = state.sections.any { section ->
            section.items.any { it.meter.billing == BillingKind.METERED && it.meter.reminderDay in 1..31 }
        },
    )

    val paidMessage = stringResource(R.string.payment_paid_snackbar)
    val undoLabel = stringResource(R.string.transactions_undo)
    LaunchedEffect(paidUndo) {
        if (paidUndo == null) return@LaunchedEffect
        keyboard?.hide()
        if (snackbarHostState.showUndo(paidMessage, undoLabel)) {
            viewModel.onUndoPaid()
        } else {
            viewModel.onUndoDismissed()
        }
    }

    // The floating bar overlaps the bottom of the screen, so everything anchored there
    // is lifted clear of it by hand.
    val barSpace = floatingBottomBarSpace()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            UndoSnackbarHost(snackbarHostState, bottomPadding = barSpace)
        },
        floatingActionButton = {
            if (state.selectedCount == 0) {
                AddUtilityButton(
                    onAddMeter = onAddMeter,
                    onCreateGroup = viewModel::onCreateGroupClick,
                    modifier = Modifier.padding(bottom = barSpace),
                )
            }
        },
        bottomBar = {
            if (state.selectedCount > 0) {
                PaySelectedBar(
                    count = state.selectedCount,
                    totalMinor = state.selectedTotalMinor,
                    onPay = viewModel::onPaySelectedClick,
                    modifier = Modifier.padding(bottom = barSpace),
                )
            }
        },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyMeters(Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 88.dp + barSpace,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.sections.forEach { section ->
                    item(key = "hdr-${section.group?.id ?: "other"}") {
                        val payable = section.items.filter { it.isPayable }
                        GroupHeader(
                            section = section,
                            hasPayable = payable.isNotEmpty(),
                            allSelected = payable.isNotEmpty() && payable.all { it.meter.id in state.selectedIds },
                            onRename = { section.group?.let(viewModel::onRenameGroupClick) },
                            onDelete = { section.group?.let(viewModel::onDeleteGroup) },
                            onSubmitReadings = { section.group?.let { onSubmitGroupReadings(it.id) } },
                            onToggleGroup = { viewModel.onToggleGroup(section) },
                        )
                    }
                    items(section.items, key = { it.meter.id }) { item ->
                        MeterCard(
                            item = item,
                            isSelected = item.meter.id in state.selectedIds,
                            onClick = { onOpenMeter(item.meter.id) },
                            onAddReading = { viewModel.onAddReadingClick(item) },
                            onToggleSelect = { viewModel.onToggleSelect(item.meter.id) },
                        )
                    }
                }
            }
        }
    }

    readingDialog?.let { dialog ->
        ReadingDialog(
            state = dialog,
            onValueChange = viewModel::onReadingValueChange,
            onDateChange = viewModel::onReadingDateChange,
            onConfirm = viewModel::onReadingConfirm,
            onDismiss = viewModel::onReadingDismiss,
        )
    }

    groupDialog?.let { dialog ->
        GroupDialog(
            state = dialog,
            onNameChange = viewModel::onGroupNameChange,
            onConfirm = viewModel::onGroupConfirm,
            onDismiss = viewModel::onGroupDismiss,
        )
    }

    payDialog?.let { dialog ->
        BatchPayDialog(
            state = dialog,
            accounts = viewModel.accountsForPicker(),
            onAccountChange = viewModel::onPayAccountChange,
            onDateChange = viewModel::onPayDateChange,
            onConfirm = viewModel::onPayConfirm,
            onDismiss = viewModel::onPayDismiss,
        )
    }
}

@Composable
private fun PaySelectedBar(
    count: Int,
    totalMinor: Long,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.pay_selected_summary,
                    count,
                    stringResource(R.string.money_with_currency, formatMinor(totalMinor)),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onPay) { Text(stringResource(R.string.pay_selected)) }
        }
    }
}

/** One button, two things to add: a service or a group to file services under. */
@Composable
private fun AddUtilityButton(
    onAddMeter: () -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.meter_add))
        }
        AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.meter_add)) },
                onClick = {
                    expanded = false
                    onAddMeter()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_create)) },
                onClick = {
                    expanded = false
                    onCreateGroup()
                },
            )
        }
    }
}

@Composable
private fun GroupHeader(
    section: MeterGroupSection,
    hasPayable: Boolean,
    allSelected: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSubmitReadings: () -> Unit,
    onToggleGroup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Batch entry needs a group to key on, and only makes sense if the group has meters that
    // take readings - «Прочее» and all-fixed groups get nothing to press.
    val hasMetered = section.group != null &&
        section.items.any { it.meter.billing == BillingKind.METERED }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tick the whole quittance at once - all its payable services together.
            if (hasPayable) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleGroup() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.group?.name ?: stringResource(R.string.group_other),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.group_total,
                        stringResource(R.string.money_with_currency, formatMinor(section.totalMinor)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // «Прочее» is not a real group, so it has nothing to rename or delete.
            if (section.group != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.group_menu))
                    }
                    AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_rename)) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_delete)) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                            ),
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
        if (hasMetered) {
            TextButton(onClick = onSubmitReadings) {
                Text(stringResource(R.string.group_submit_readings))
            }
        }
    }
}

@Composable
private fun EmptyMeters(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.meters_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.meters_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeterCard(
    item: MeterItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAddReading: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val meter = item.meter
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = meter.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (meter.billing != BillingKind.FIXED) {
                    Text(
                        text = stringResource(
                            R.string.meter_tariff_now,
                            stringResource(R.string.money_with_currency, formatMinor(meter.tariffMinor)),
                            stringResource(meter.type.unitRes()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = stringResource(meter.type.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val monthly = meter.monthlyChargeMinor()
            if (monthly != null) {
                // Nothing to submit and nothing to read: a norm or fixed service is the same
                // number every month, so the card just states it.
                Text(
                    text = when (meter.billing) {
                        BillingKind.NORM -> stringResource(
                            R.string.meter_norm_line,
                            formatMilli(meter.normMilli),
                            stringResource(meter.type.unitRes()),
                            stringResource(R.string.money_with_currency, formatMinor(monthly)),
                        )
                        else -> stringResource(
                            R.string.meter_fixed_line,
                            stringResource(R.string.money_with_currency, formatMinor(monthly)),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.lastReading?.let {
                            stringResource(
                                R.string.reading_last,
                                formatMilli(it.valueMilli),
                                stringResource(meter.type.unitRes()),
                                dateLabel(it.dateEpochDay),
                            )
                        } ?: stringResource(R.string.reading_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAddReading) {
                        Text(stringResource(R.string.reading_add))
                    }
                }
            }

            // What there is to pay: a green mark once settled, a tick to pay while it is not.
            if (item.isPaid) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PAID_GREEN)
                    Text(
                        text = stringResource(R.string.meter_paid),
                        style = MaterialTheme.typography.labelLarge,
                        color = PAID_GREEN,
                    )
                }
            } else if (item.isPayable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleSelect),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                    Text(
                        text = stringResource(
                            R.string.meter_to_pay,
                            stringResource(R.string.money_with_currency, formatMinor(item.amountMinor)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDialog(
    state: GroupDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.groupId == null) R.string.group_create else R.string.group_rename,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = state.nameText,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text(stringResource(R.string.group_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
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
}
