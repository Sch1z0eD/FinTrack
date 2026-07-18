package com.findev.fintrack.ui.screens.payments

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.dao.SettlementRow
import com.findev.fintrack.ui.FinTrackProgress
import com.findev.fintrack.ui.PanelCard
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.shortDate
import com.findev.fintrack.ui.theme.MoneyColors
import java.time.LocalDate

/**
 * A recurring payment in full: what it is, when it is next due, and everything ever paid
 * against it.
 *
 * Tapping a payment used to open its edit form, which answers the wrong question - the one
 * people ask is "did I pay this, and how much". Editing sits in the toolbar, where it
 * cannot be reached by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: RecurringDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                        text = state.payment?.name.orEmpty(),
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
                    state.payment?.let { payment ->
                        IconButton(onClick = { onEdit(payment.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.accounts_edit),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val payment = state.payment
        if (state.isLoaded && payment == null) {
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
        if (payment == null) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.money_with_currency,
                            formatMinor(payment.amountMinor),
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                    )

                    state.dueDate?.let { due ->
                        LabelledRow(
                            label = dueLabel(due, state.isOverdue, R.string.loans_next_payment),
                            value = stringResource(
                                R.string.money_with_currency,
                                formatMinor(payment.amountMinor),
                            ),
                        )
                    }

                    // Only a payment with an end is going anywhere; a subscription has no
                    // finish line and a bar towards nothing would be decoration.
                    state.totalCount?.let { total ->
                        Text(
                            text = stringResource(
                                R.string.recurring_progress,
                                state.settledCount,
                                total,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FinTrackProgress(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    state.remainingMinor?.let { remaining ->
                        LabelledRow(
                            label = stringResource(R.string.recurring_remaining),
                            value = stringResource(
                                R.string.money_with_currency,
                                formatMinor(remaining),
                            ),
                        )
                    }

                    LabelledRow(
                        label = stringResource(R.string.payment_history_total_label),
                        value = stringResource(
                            R.string.money_with_currency,
                            formatMinor(state.paidTotalMinor),
                        ),
                    )
                }
            }

            item(key = "history-header") {
                Text(
                    text = stringResource(R.string.payment_history_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.settlements.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        text = stringResource(R.string.payment_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.settlements, key = { it.id }) { row ->
                SettlementRowItem(row)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettlementRowItem(row: SettlementRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = dateLabel(row.dateEpochDay), style = MaterialTheme.typography.bodyLarge)
            Text(
                // Which occurrence it covered: a payment made late belongs to the month it
                // was due for, and without this the list cannot show that.
                text = stringResource(
                    R.string.payment_history_for,
                    shortDate(LocalDate.ofEpochDay(row.dueEpochDay)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(row.amountMinor)),
                style = MaterialTheme.typography.titleMedium,
                color = MoneyColors.expense,
            )
            Text(
                text = stringResource(
                    if (row.isPartial) R.string.payment_partial else R.string.payment_full,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (row.isPartial) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
