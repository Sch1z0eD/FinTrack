package com.findev.fintrack.ui.screens.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.MonthlyConsumption
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.ui.screens.payments.PAID_GREEN
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMilli
import com.findev.fintrack.ui.formatMinor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: MeterDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val payDialog by viewModel.payDialog.collectAsStateWithLifecycle()
    val paidUndo by viewModel.paidUndo.collectAsStateWithLifecycle()
    val meter = state.meter
    val snackbarHostState = remember { SnackbarHostState() }

    // The meter is read once, so coming back from the edit form has to re-read it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val paidMessage = stringResource(R.string.payment_paid_snackbar)
    val undoLabel = stringResource(R.string.transactions_undo)
    LaunchedEffect(paidUndo) {
        if (paidUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = paidMessage,
            actionLabel = undoLabel,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoPaid() else viewModel.onUndoDismissed()
    }

    Scaffold(
        // The app-level Scaffold already applies the status bar inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(meter?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quick_entry_back),
                        )
                    }
                },
                actions = {
                    if (meter != null) {
                        IconButton(onClick = { onEdit(meter.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.meter_form_edit),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (meter == null) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryCard(
                    meter = meter,
                    monthlyChargeMinor = state.monthlyChargeMinor,
                    monthPaidThisMonth = state.monthPaidThisMonth,
                    canPay = state.accounts.isNotEmpty(),
                    onPayMonth = viewModel::onPayMonthClick,
                )
            }

            // A non-metered service (norm or fixed) has no readings and no history.
            if (meter.billing != BillingKind.METERED) return@LazyColumn

            if (state.months.isNotEmpty()) {
                item { HistoryChartCard(months = state.months, unit = stringResource(meter.type.unitRes())) }
            }

            if (state.readings.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.reading_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.reading_history),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.readings, key = { it.reading.id }) { item ->
                    ReadingRow(
                        item = item,
                        unit = stringResource(meter.type.unitRes()),
                        canPay = state.accounts.isNotEmpty(),
                        onPay = { viewModel.onPayReadingClick(item) },
                        onDelete = { viewModel.onDeleteReading(item.reading.id) },
                    )
                }
            }
        }
    }

    payDialog?.let { dialog ->
        MeterPayDialog(
            state = dialog,
            accounts = state.accounts,
            onAmountChange = viewModel::onPayAmountChange,
            onAccountChange = viewModel::onPayAccountChange,
            onDateChange = viewModel::onPayDateChange,
            onConfirm = viewModel::onPayConfirm,
            onDismiss = viewModel::onPayDismiss,
        )
    }
}

@Composable
private fun SummaryCard(
    meter: MeterEntity,
    monthlyChargeMinor: Long?,
    monthPaidThisMonth: Boolean,
    canPay: Boolean,
    onPayMonth: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(meter.type.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            // A fixed fee has no per-unit tariff to state; norm and metered do.
            if (meter.billing != BillingKind.FIXED) {
                Text(
                    text = stringResource(
                        R.string.meter_tariff_now,
                        stringResource(R.string.money_with_currency, formatMinor(meter.tariffMinor)),
                        stringResource(meter.type.unitRes()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (monthlyChargeMinor != null) {
                Text(
                    text = when (meter.billing) {
                        BillingKind.NORM -> stringResource(
                            R.string.meter_norm_line,
                            formatMilli(meter.normMilli),
                            stringResource(meter.type.unitRes()),
                            stringResource(R.string.money_with_currency, formatMinor(monthlyChargeMinor)),
                        )
                        else -> stringResource(
                            R.string.meter_fixed_line,
                            stringResource(R.string.money_with_currency, formatMinor(monthlyChargeMinor)),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PayAction(
                    isPaid = monthPaidThisMonth,
                    canPay = canPay,
                    payLabel = stringResource(R.string.meter_pay_month),
                    onPay = onPayMonth,
                )
            }
        }
    }
}

/** "Оплатить" until an expense settles it, then a green "Оплачено" that cannot fire twice. */
@Composable
private fun PayAction(
    isPaid: Boolean,
    canPay: Boolean,
    payLabel: String,
    onPay: () -> Unit,
) {
    if (isPaid) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = PAID_GREEN,
            )
            Text(
                text = stringResource(R.string.meter_paid),
                style = MaterialTheme.typography.labelLarge,
                color = PAID_GREEN,
            )
        }
    } else if (canPay) {
        TextButton(onClick = onPay) { Text(payLabel) }
    }
}

@Composable
private fun ReadingRow(
    item: ReadingItem,
    unit: String,
    canPay: Boolean,
    onPay: () -> Unit,
    onDelete: () -> Unit,
) {
    val reading = item.reading
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.reading_row_value, formatMilli(reading.valueMilli), unit),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = dateLabel(reading.dateEpochDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A baseline reading bills nothing, so there is nothing to pay for it.
            if (reading.amountMinor > 0) {
                PayAction(
                    isPaid = item.isPaid,
                    canPay = canPay,
                    payLabel = stringResource(R.string.meter_pay),
                    onPay = onPay,
                )
            }
        }
        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(reading.amountMinor)),
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.reading_delete),
            )
        }
    }
}

@Composable
private fun HistoryChartCard(months: List<MonthlyConsumption>, unit: String) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLL", Locale("ru")) }

    LaunchedEffect(months) {
        modelProducer.runTransaction {
            columnSeries {
                // Whole units, not thousandths: this is a picture, and 197 reads where
                // 197000 does not. The stored value stays Long either way.
                series(months.map { it.consumedMilli / 1000.0 })
            }
        }
    }

    // Vico plots x as 0..n, so the month names have to be looked up by position.
    val bottomFormatter = CartesianValueFormatter { _, x, _ ->
        months.getOrNull(x.toInt())?.month?.format(monthFormat).orEmpty()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.reading_chart_title, unit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
