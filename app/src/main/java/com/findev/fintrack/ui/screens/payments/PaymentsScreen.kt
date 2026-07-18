package com.findev.fintrack.ui.screens.payments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.RecurrencePeriod
import com.findev.fintrack.ui.NotificationPermissionRequest
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.floatingBottomBarSpace
import com.findev.fintrack.ui.formatMinor

/**
 * "Paid" is the one state Material's scheme has no colour for - primary means actionable
 * and it is the opposite. Same green as the default categories use.
 *
 * Internal, because the loan card shows the same mark and it must be the same green.
 */
internal val PAID_GREEN = Color(0xFF4CAF50)

@Composable
fun PaymentsScreen(
    onAddLoan: () -> Unit,
    onAddRecurring: () -> Unit,
    onOpenLoan: (String) -> Unit,
    onOpenRecurring: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val undo by viewModel.paidUndo.collectAsStateWithLifecycle()
    val payDialog by viewModel.payDialog.collectAsStateWithLifecycle()
    val payAheadDialog by viewModel.payAheadDialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Every due date and balance on this screen moves with the date.
    LifecycleResumeEffect(Unit) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }

    NotificationPermissionRequest(
        needed = state.payments.any { it is PaymentItem.Recurring && it.reminderEnabled },
    )

    val paidMessage = stringResource(R.string.payment_paid_snackbar)
    val undoLabel = stringResource(R.string.transactions_undo)
    LaunchedEffect(undo) {
        if (undo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = paidMessage,
            actionLabel = undoLabel,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
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
            SnackbarHost(snackbarHostState, Modifier.padding(bottom = barSpace))
        },
        floatingActionButton = {
            AddPaymentButton(
                onAddLoan = onAddLoan,
                onAddRecurring = onAddRecurring,
                modifier = Modifier.padding(bottom = barSpace),
            )
        },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyPayments(Modifier.padding(innerPadding))
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.payments, key = { it.id }) { payment ->
                    when (payment) {
                        is PaymentItem.Loan -> LoanCard(
                            loan = payment,
                            onClick = { onOpenLoan(payment.id) },
                            onMarkPaid = { viewModel.onMarkPaidClick(payment) },
                        )

                        is PaymentItem.Recurring -> RecurringCard(
                            payment = payment,
                            onClick = { onOpenRecurring(payment.id) },
                            onMarkPaid = { viewModel.onMarkPaidClick(payment) },
                            onPayAhead = { viewModel.onPayAheadClick(payment) },
                        )
                    }
                }
            }
        }
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

    payAheadDialog?.let { dialog ->
        PayAheadDialog(
            state = dialog,
            onCountChange = viewModel::onPayAheadCountChange,
            onConfirm = viewModel::onPayAheadConfirm,
            onDismiss = viewModel::onPayAheadDismiss,
        )
    }
}

/** One button, two kinds of payment: the list holds both, so adding has to offer both. */
@Composable
private fun AddPaymentButton(
    onAddLoan: () -> Unit,
    onAddRecurring: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.payments_add))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.payments_add_loan)) },
                onClick = {
                    expanded = false
                    onAddLoan()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.payments_add_recurring)) },
                onClick = {
                    expanded = false
                    onAddRecurring()
                },
            )
        }
    }
}

/** Label on the left, number on the right - the label yields so the money never wraps. */
@Composable
private fun LabelledRow(
    label: String,
    value: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyPayments(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.payments_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.payments_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Card title on the left, the headline number on the right. */
@Composable
private fun CardHeader(
    name: String,
    value: String,
    highlighted: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun LoanCard(
    loan: PaymentItem.Loan,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(
                name = loan.name,
                value = if (loan.isClosed) {
                    stringResource(R.string.loans_closed)
                } else {
                    stringResource(R.string.money_with_currency, formatMinor(loan.balanceMinor))
                },
                highlighted = loan.isClosed,
            )

            if (loan.showsProgress) {
                LinearProgressIndicator(
                    progress = { loan.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            loan.dueDate?.let { due ->
                LabelledRow(
                    label = dueLabel(due, loan.isOverdue, R.string.loans_next_payment),
                    value = stringResource(
                        R.string.money_with_currency,
                        formatMinor(loan.dueAmountMinor),
                    ),
                    labelColor = overdueColor(loan.isOverdue),
                )
            }

            LabelledRow(
                label = stringResource(R.string.loans_overpayment_label),
                value = stringResource(
                    R.string.money_with_currency,
                    formatMinor(loan.overpaymentMinor),
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarkPaidButton(item = loan, onMarkPaid = onMarkPaid)
            }
        }
    }
}

/**
 * The button exists only while something is actually payable.
 *
 * Once a payment is settled the next due date is a month out, so there is nothing to press
 * and pressing again would only double-count. What replaces it is a green mark, and it is
 * shown only when a payment was really recorded - a payment that has never been marked
 * owes nothing today either, but it has not been paid, and a tick there would say it had.
 */
@Composable
private fun MarkPaidButton(
    item: PaymentItem,
    onMarkPaid: () -> Unit,
) {
    if (item.isSettled) {
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
        return
    }

    if (!item.isDue) return

    // Nothing is posted without somewhere to post it: a payment with no account named
    // gets the reason it has no button rather than a button that cannot work.
    val hasAccount = when (item) {
        is PaymentItem.Loan -> item.accountId != null && item.categoryId != null
        is PaymentItem.Recurring -> true
    }
    if (!hasAccount) {
        Text(
            text = stringResource(R.string.payment_no_account),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Button(onClick = onMarkPaid) {
        Text(stringResource(R.string.payment_mark_paid))
    }
}

@Composable
private fun overdueColor(isOverdue: Boolean) = if (isOverdue) {
    MaterialTheme.colorScheme.error
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun dueLabel(due: java.time.LocalDate, isOverdue: Boolean, normalRes: Int): String =
    if (isOverdue) {
        stringResource(R.string.payment_overdue, dateLabel(due.toEpochDay()))
    } else {
        stringResource(normalRes, dateLabel(due.toEpochDay()))
    }

@Composable
private fun RecurringCard(
    payment: PaymentItem.Recurring,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
    onPayAhead: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(
                name = payment.name,
                value = stringResource(
                    R.string.money_with_currency,
                    formatMinor(payment.dueAmountMinor),
                ),
                highlighted = false,
            )

            // Only a payment with an end date is going anywhere; an open-ended one has no
            // finish line and a bar towards nothing would be decoration.
            if (payment.showsProgress) {
                Text(
                    text = stringResource(
                        R.string.recurring_progress,
                        payment.settledCount,
                        payment.totalCount ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { payment.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                LabelledRow(
                    label = stringResource(R.string.recurring_remaining),
                    value = stringResource(
                        R.string.money_with_currency,
                        formatMinor(payment.remainingMinor),
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = payment.dueDate?.let {
                        dueLabel(it, payment.isOverdue, R.string.recurring_next)
                    } ?: stringResource(R.string.recurring_ended),
                    style = MaterialTheme.typography.bodyMedium,
                    color = overdueColor(payment.isOverdue),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (payment.reminderEnabled && !payment.hasEnded) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.recurring_reminder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(payment.period.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Paying ahead makes sense whenever the payment is still running - it is
                // not tied to something being due today.
                if (!payment.hasEnded) {
                    TextButton(onClick = onPayAhead) {
                        Text(stringResource(R.string.recurring_pay_ahead))
                    }
                }
                MarkPaidButton(item = payment, onMarkPaid = onMarkPaid)
            }
        }
    }
}

fun RecurrencePeriod.labelRes(): Int = when (this) {
    RecurrencePeriod.DAY -> R.string.recurring_period_day
    RecurrencePeriod.WEEK -> R.string.recurring_period_week
    RecurrencePeriod.MONTH -> R.string.recurring_period_month
    RecurrencePeriod.YEAR -> R.string.recurring_period_year
}
