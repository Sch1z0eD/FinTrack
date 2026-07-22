package com.findev.fintrack.ui.screens.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

        TotalsRow(state = state)

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

private enum class TotalMetric { EXPENSE, INCOME }

/**
 * The two "за месяц" cards, each a tap-to-expand. Expanding does not push the layout: the
 * collapsed row keeps its place and the full card unfolds over it in a Popup, growing from the
 * tapped card's outer edge - right for expense, left for income - down and across to full width.
 */
@Composable
private fun TotalsRow(state: OverviewUiState) {
    var expanded by remember { mutableStateOf<TotalMetric?>(null) }

    // BoxWithConstraints so the popup can anchor to the row's own top-left and match its width,
    // rather than chasing absolute window coordinates (which drift by the status-bar inset).
    BoxWithConstraints {
        val rowWidth = maxWidth

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollapsedTotalCard(
                labelRes = R.string.overview_expense_month,
                amountMinor = state.monthExpenseMinor,
                spark = state.expenseSpark,
                color = MoneyColors.expense,
                onClick = { expanded = TotalMetric.EXPENSE },
                modifier = Modifier.weight(1f),
            )
            CollapsedTotalCard(
                labelRes = R.string.overview_income_month,
                amountMinor = state.monthIncomeMinor,
                spark = state.incomeSpark,
                color = MoneyColors.income,
                onClick = { expanded = TotalMetric.INCOME },
                modifier = Modifier.weight(1f),
            )
        }

        val metric = expanded
        if (metric != null) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { expanded = null },
                properties = PopupProperties(focusable = true),
            ) {
                // Unfold from the tapped card's own outer-top corner: the expense card (left)
                // from top-left so it grows rightward, the income card (right) from top-right so
                // it grows leftward - each toward the centre, down and across. A corner-pivoted
                // scale rather than expandIn, whose reveal a top-left-anchored popup was overriding.
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val progress by animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = tween(durationMillis = 200),
                    label = "cardExpand",
                )
                val pivotX = if (metric == TotalMetric.INCOME) 1f else 0f
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = progress
                        scaleY = progress
                        alpha = progress
                        transformOrigin = TransformOrigin(pivotX, 0f)
                    },
                ) {
                    ExpandedTotalCard(
                        labelRes = if (metric == TotalMetric.EXPENSE) {
                            R.string.overview_expense_month
                        } else {
                            R.string.overview_income_month
                        },
                        amountMinor = if (metric == TotalMetric.EXPENSE) {
                            state.monthExpenseMinor
                        } else {
                            state.monthIncomeMinor
                        },
                        spark = if (metric == TotalMetric.EXPENSE) state.expenseSpark else state.incomeSpark,
                        averages = if (metric == TotalMetric.EXPENSE) state.expenseAverages else state.incomeAverages,
                        saldoMinor = state.saldoMinor,
                        color = if (metric == TotalMetric.EXPENSE) MoneyColors.expense else MoneyColors.income,
                        onClose = { expanded = null },
                        modifier = Modifier.width(rowWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedTotalCard(
    labelRes: Int,
    amountMinor: Long,
    spark: List<Long>,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Hints the card unfolds; a chevron the user can tap.
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.overview_avg_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(amountMinor)),
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
            Sparkline(
                values = spark,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )
        }
    }
}

@Composable
private fun ExpandedTotalCard(
    labelRes: Int,
    amountMinor: Long,
    spark: List<Long>,
    averages: AverageStats,
    saldoMinor: Long,
    color: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClose,
        shape = RoundedCornerShape(PanelCorner),
        // Same tone as the collapsed card, only a slight lift, so it reads as that card
        // unfolding rather than a separate panel floating over the screen.
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Top matches the collapsed card exactly (same amount size, same sparkline) so the
            // header does not jump when it unfolds - only the averages appear below.
            Text(
                text = stringResource(R.string.money_with_currency, formatMinor(amountMinor)),
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
            Sparkline(
                values = spark,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            Text(
                text = stringResource(R.string.overview_avg_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatLine(R.string.overview_avg_day, averages.perDayMinor, color)
            StatLine(R.string.overview_avg_week, averages.perWeekMinor, color)
            StatLine(R.string.overview_avg_month, averages.perMonthMinor, color)
            StatLine(R.string.overview_avg_year, averages.perYearMinor, color)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            StatLine(
                labelRes = R.string.overview_saldo,
                amountMinor = saldoMinor,
                valueColor = if (saldoMinor < 0) MoneyColors.expense else MoneyColors.income,
            )
        }
    }
}

/** A tiny line-and-dots trend chart, drawn from the monthly series. */
@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier) {
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(1)
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        // Inset a touch so the dots at the top/bottom are not clipped.
        val pad = 4.dp.toPx()
        val usableH = (size.height - pad * 2).coerceAtLeast(1f)
        val points = values.mapIndexed { i, v ->
            val x = i * stepX
            val norm = (v - minV).toFloat() / range
            Offset(x, pad + (1f - norm) * usableH)
        }
        // A Catmull-Rom spline through the points: rounded rises and falls rather than a
        // jagged polyline. Each segment's Bézier controls are a sixth of the way along the
        // neighbours' chord, with the ends clamped to themselves.
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[(i - 1).coerceAtLeast(0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y,
                )
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEach { drawCircle(color = color, radius = 2.5.dp.toPx(), center = it) }
    }
}

@Composable
private fun StatLine(labelRes: Int, amountMinor: Long, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(amountMinor)),
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
        )
    }
}
