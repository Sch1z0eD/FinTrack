package com.findev.fintrack.ui.screens.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.PrepaymentMode
import com.findev.fintrack.loanengine.LoanSummary
import com.findev.fintrack.loanengine.ScheduleEntry
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.screens.payments.PAID_GREEN
import com.findev.fintrack.ui.screens.payments.PayDialog
import com.findev.fintrack.ui.shortDate
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: LoanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val payDialog by viewModel.payDialog.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        text = state.loan?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quick_entry_back),
                        )
                    }
                },
                actions = {
                    state.loan?.let { loan ->
                        IconButton(onClick = { onEdit(loan.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.loan_form_edit),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val summary = state.summary
        if (state.isLoaded && summary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.loan_detail_not_found))
            }
            return@Scaffold
        }
        if (summary == null) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                SummaryCard(
                    state = state,
                    summary = summary,
                    onMarkPaid = viewModel::onMarkPaidClick,
                )
            }

            item(key = "prepayments") {
                PrepaymentsCard(
                    prepayments = state.prepayments,
                    todayEpochDay = state.todayEpochDay,
                    onAdd = viewModel::onAddPrepaymentClick,
                    onDelete = viewModel::onDeletePrepayment,
                )
            }

            item(key = "chart") {
                BalanceChartCard(schedule = state.schedule)
            }

            item(key = "schedule-header") {
                Text(
                    text = stringResource(R.string.loan_detail_schedule),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(state.schedule, key = { it.number }) { entry ->
                ScheduleRow(entry)
                HorizontalDivider()
            }
        }
    }

    state.dialog?.let { dialog ->
        PrepaymentDialog(
            state = dialog,
            onAmountChange = viewModel::onDialogAmountChange,
            onDateChange = viewModel::onDialogDateChange,
            onModeChange = viewModel::onDialogModeChange,
            onConfirm = viewModel::onDialogConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
    }

    payDialog?.let { dialog ->
        PayDialog(
            state = dialog,
            onAmountChange = viewModel::onPayAmountChange,
            onDateChange = viewModel::onPayDateChange,
            onConfirm = viewModel::onPayConfirm,
            onDismiss = viewModel::onPayDismiss,
        )
    }
}

@Composable
private fun PrepaymentsCard(
    prepayments: List<LoanPrepaymentEntity>,
    todayEpochDay: Long,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                    text = stringResource(R.string.prepayment_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.prepayment_add))
                }
            }

            if (prepayments.isEmpty()) {
                Text(
                    text = stringResource(R.string.prepayment_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            prepayments.forEach { prepayment ->
                PrepaymentRow(
                    prepayment = prepayment,
                    isPlanned = prepayment.dateEpochDay > todayEpochDay,
                    onDelete = { onDelete(prepayment.id) },
                )
            }
        }
    }
}

@Composable
private fun PrepaymentRow(
    prepayment: LoanPrepaymentEntity,
    isPlanned: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shortDate(LocalDate.ofEpochDay(prepayment.dateEpochDay)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPlanned) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.money_with_currency,
                    formatMinor(prepayment.amountMinor),
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            // A dated-in-the-future prepayment has not happened, and saying so is the
            // difference between "the app ignored me" and "not yet".
            Text(
                text = stringResource(
                    when (prepayment.mode) {
                        PrepaymentMode.REDUCE_TERM -> R.string.prepayment_mode_term
                        PrepaymentMode.REDUCE_PAYMENT -> R.string.prepayment_mode_payment
                    },
                ).let {
                    if (isPlanned) "$it · ${stringResource(R.string.prepayment_planned)}" else it
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlanned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.prepayment_delete),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    state: LoanDetailUiState,
    summary: LoanSummary,
    onMarkPaid: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(state.balanceMinor)),
                style = MaterialTheme.typography.headlineMedium,
            )
            // An interest-only loan repays nothing until the balloon, so a progress bar
            // would sit at zero for years and then jump to full. That is not progress,
            // it is a countdown - so it says so instead of drawing a flat line.
            if (state.loan?.type == LoanType.INTEREST_ONLY) {
                Text(
                    text = stringResource(
                        R.string.loan_balloon_note,
                        shortDate(summary.closingDate),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val repaid = summary.principalMinor - state.balanceMinor
                Text(
                    text = stringResource(
                        R.string.loans_progress,
                        stringResource(R.string.money_with_currency, formatMinor(repaid)),
                        stringResource(
                            R.string.money_with_currency,
                            formatMinor(summary.principalMinor),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    // Float drives the bar only; the kopecks behind it stay Long.
                    progress = {
                        if (summary.principalMinor == 0L) 1f
                        else repaid.toFloat() / summary.principalMinor.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            state.nextPayment?.let { next ->
                SummaryRow(
                    label = stringResource(R.string.loans_next_payment, shortDate(next.date)),
                    value = stringResource(R.string.money_with_currency, formatMinor(next.paymentMinor)),
                )
            }
            SummaryRow(
                label = stringResource(R.string.loans_overpayment_label),
                value = stringResource(
                    R.string.money_with_currency,
                    formatMinor(summary.overpaymentMinor),
                ),
            )
            // Without fees the overpayment IS the interest; showing the same number
            // twice just makes the card look padded.
            if (summary.totalFeesMinor > 0) {
                SummaryRow(
                    label = stringResource(R.string.loan_detail_interest),
                    value = stringResource(
                        R.string.money_with_currency,
                        formatMinor(summary.totalInterestMinor),
                    ),
                )
                SummaryRow(
                    label = stringResource(R.string.loan_detail_fees),
                    value = stringResource(
                        R.string.money_with_currency,
                        formatMinor(summary.totalFeesMinor),
                    ),
                )
            }
            SummaryRow(
                label = stringResource(R.string.loan_detail_total_paid),
                value = stringResource(
                    R.string.money_with_currency,
                    formatMinor(summary.totalPaidMinor),
                ),
            )
            SummaryRow(
                label = stringResource(R.string.loan_detail_closing),
                value = shortDate(summary.closingDate),
            )

            // Same rule as the payments list: a button only while something is payable,
            // a green mark once it is settled, and nothing at all in between.
            if (state.isSettled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = PAID_GREEN,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.payment_paid_done),
                        style = MaterialTheme.typography.labelLarge,
                        color = PAID_GREEN,
                    )
                }
            } else if (state.canMarkPaid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onMarkPaid) {
                        Text(stringResource(R.string.payment_mark_paid))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun BalanceChartCard(schedule: List<ScheduleEntry>) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(schedule) {
        if (schedule.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                // Roubles, not kopecks: this is a picture, and the axis would be
                // unreadable otherwise. The money itself never leaves Long.
                series(schedule.map { it.balanceAfterMinor / 100.0 })
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.loan_detail_balance_chart),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${entry.number}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = shortDate(entry.date), style = MaterialTheme.typography.bodyMedium)
            // No currency sign in the breakdown: it is obvious from the payment above,
            // and the row has to survive six-figure numbers on one line.
            Text(
                text = stringResource(
                    R.string.loan_detail_row_split,
                    formatMinor(entry.interestMinor),
                    formatMinor(entry.principalMinor),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.prepaymentMinor > 0) {
                Text(
                    text = stringResource(
                        R.string.loan_detail_prepayment,
                        formatMinor(entry.prepaymentMinor),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(entry.paymentMinor)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    R.string.loan_detail_row_balance,
                    formatMinor(entry.balanceAfterMinor),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
