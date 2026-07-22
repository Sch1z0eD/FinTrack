package com.findev.fintrack.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.findev.fintrack.data.MonthlyObligations
import com.findev.fintrack.data.local.AccountBalance
import com.findev.fintrack.ui.FinTrackProgress
import com.findev.fintrack.ui.PanelCorner
import com.findev.fintrack.ui.floatingBottomBarSpace
import com.findev.fintrack.ui.heroGradient
import com.findev.fintrack.ui.theme.MoneyColors
import com.findev.fintrack.ui.formatMinor

@Composable
fun OverviewScreen(
    onOpenAccounts: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The month can roll over while the app sits in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .padding(bottom = 16.dp + floatingBottomBarSpace()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BalanceCard(
            balanceMinor = state.balanceMinor,
            freeMinor = state.freeAfterObligationsMinor,
            hasObligations = state.obligations.remainingMinor > 0,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TotalCard(
                labelRes = R.string.overview_expense_month,
                amountMinor = state.monthExpenseMinor,
                amountColor = MoneyColors.expense,
                modifier = Modifier.weight(1f),
            )
            TotalCard(
                labelRes = R.string.overview_income_month,
                amountMinor = state.monthIncomeMinor,
                amountColor = MoneyColors.income,
                modifier = Modifier.weight(1f),
            )
        }

        ObligationsCard(obligations = state.obligations)

        AccountsCard(accounts = state.accounts, onOpenAccounts = onOpenAccounts)

        StatisticsCard(onOpenStatistics = onOpenStatistics)

        NavigationCard(
            title = stringResource(R.string.overview_settings),
            hint = stringResource(R.string.overview_settings_hint),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun NavigationCard(title: String, hint: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = hint, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatisticsCard(onOpenStatistics: () -> Unit) {
    Surface(
        onClick = onOpenStatistics,
        shape = RoundedCornerShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.overview_statistics),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.overview_statistics_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.overview_statistics),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountsCard(
    accounts: List<AccountBalance>,
    onOpenAccounts: () -> Unit,
) {
    Surface(
        onClick = onOpenAccounts,
        shape = RoundedCornerShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.overview_accounts),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.accounts_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.accounts_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val (active, archived) = accounts.partition { !it.isArchived }

            active.forEach { account -> AccountBalanceRow(account, archived = false) }

            if (archived.isNotEmpty()) {
                // Separate section: these are closed accounts and are NOT part of the
                // balance above, so listing them inline would not add up.
                Text(
                    text = stringResource(R.string.overview_archived_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                archived.forEach { account -> AccountBalanceRow(account, archived = true) }
            }
        }
    }
}

@Composable
private fun AccountBalanceRow(
    account: AccountBalance,
    archived: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (archived) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(account.balanceMinor)),
            style = MaterialTheme.typography.titleMedium,
            color = if (archived) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun BalanceCard(balanceMinor: Long, freeMinor: Long, hasObligations: Boolean) {
    // The one animated surface in the app - see Modifier.heroGradient.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heroGradient()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.overview_balance),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(balanceMinor)),
                style = MaterialTheme.typography.displaySmall,
            )

            // Only worth the line when something is actually owed - otherwise it just
            // repeats the balance in smaller type.
            if (hasObligations) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
                Text(
                    text = stringResource(R.string.overview_free),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.money_with_currency, formatMinor(freeMinor)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (freeMinor < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
    }
}

@Composable
private fun ObligationsCard(obligations: MonthlyObligations) {
    Surface(
        shape = RoundedCornerShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.overview_obligations),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (obligations.totalMinor == 0L) {
                Text(
                    text = stringResource(R.string.overview_obligations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(
                        R.string.money_with_currency,
                        formatMinor(obligations.totalMinor),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(
                            if (obligations.remainingMinor == 0L) {
                                R.string.overview_obligations_all_paid
                            } else {
                                R.string.overview_obligations_left
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (obligations.remainingMinor > 0) {
                        Text(
                            text = stringResource(
                                R.string.money_with_currency,
                                formatMinor(obligations.remainingMinor),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            FinTrackProgress(
                progress = {
                    if (obligations.totalMinor == 0L) {
                        0f
                    } else {
                        // Float drives the bar only; the kopecks behind it stay Long.
                        (obligations.paidMinor.toFloat() / obligations.totalMinor.toFloat())
                            .coerceIn(0f, 1f)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(
                    R.string.overview_obligations_paid,
                    formatMinor(obligations.paidMinor),
                    formatMinor(obligations.totalMinor),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (obligations.loansMinor > 0) {
                ObligationRow(
                    label = stringResource(R.string.overview_obligations_loans),
                    amountMinor = obligations.loansMinor,
                )
            }
            if (obligations.recurringMinor > 0) {
                ObligationRow(
                    label = stringResource(R.string.overview_obligations_recurring),
                    amountMinor = obligations.recurringMinor,
                )
            }
            if (obligations.utilitiesMinor > 0) {
                ObligationRow(
                    label = stringResource(R.string.overview_obligations_utilities),
                    amountMinor = obligations.utilitiesMinor,
                )
            }
        }
    }
}

@Composable
private fun ObligationRow(label: String, amountMinor: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(amountMinor)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TotalCard(
    labelRes: Int,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    amountColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(amountMinor)),
                style = MaterialTheme.typography.titleLarge,
                color = amountColor,
            )
        }
    }
}
