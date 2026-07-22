package com.findev.fintrack.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.MonthlyObligations
import com.findev.fintrack.data.ObligationsRepository
import com.findev.fintrack.data.OverviewRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.buildMonthlyBars
import com.findev.fintrack.data.monthlyBarsFromEpochDay
import com.findev.fintrack.data.local.AccountBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** Average spend or income per period, all in kopecks; derived from all-time totals. */
data class AverageStats(
    val perDayMinor: Long = 0,
    val perWeekMinor: Long = 0,
    val perMonthMinor: Long = 0,
    val perYearMinor: Long = 0,
)

data class OverviewUiState(
    val balanceMinor: Long = 0,
    val accounts: List<AccountBalance> = emptyList(),
    val monthExpenseMinor: Long = 0,
    val monthIncomeMinor: Long = 0,
    val obligations: MonthlyObligations = MonthlyObligations(),
    /** Averages for the tap-to-expand cards. */
    val expenseAverages: AverageStats = AverageStats(),
    val incomeAverages: AverageStats = AverageStats(),
    /** All-time income minus expense: the net the expanded cards show. */
    val saldoMinor: Long = 0,
    /** Monthly expense/income trend (oldest first) for the card sparklines, in kopecks. */
    val expenseSpark: List<Long> = emptyList(),
    val incomeSpark: List<Long> = emptyList(),
    val isLoaded: Boolean = false,
) {
    /**
     * What is left once this month's remaining obligations are covered. Can go negative,
     * and that is the point: hiding it would turn the one number worth acting on into a
     * reassuring zero.
     */
    val freeAfterObligationsMinor: Long get() = balanceMinor - obligations.remainingMinor
}

/**
 * Averages a total over the days it was earned/spent across.
 *
 * The span runs from the first transaction to today, so quiet days count too - "средние
 * расходы за день" is the true daily rate, not an average of only the days money moved. Weekly,
 * monthly and yearly figures scale the same daily rate (a month taken as 365/12 days).
 */
fun averageStats(totalMinor: Long, spanDays: Long): AverageStats {
    val span = spanDays.coerceAtLeast(1)
    return AverageStats(
        perDayMinor = totalMinor / span,
        perWeekMinor = totalMinor * 7 / span,
        perMonthMinor = totalMinor * 365 / (span * 12),
        perYearMinor = totalMinor * 365 / span,
    )
}

/** Inclusive epoch-day bounds of the calendar month containing [date]. */
fun monthBounds(date: LocalDate): LongRange {
    val first = date.withDayOfMonth(1)
    val last = date.withDayOfMonth(date.lengthOfMonth())
    return first.toEpochDay()..last.toEpochDay()
}

/** How many months of trend the card sparklines show. */
private const val SPARKLINE_MONTHS = 12

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val overviewRepository: OverviewRepository,
    private val obligationsRepository: ObligationsRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    /** Re-read on every refresh so the screen does not stay on last month after midnight. */
    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<OverviewUiState> = today
        .flatMapLatest { date ->
            val bounds = monthBounds(date)
            // Two extra series folded into one flow so the five-arg combine still fits.
            val extras = combine(
                overviewRepository.observeAverageBasis(),
                transactionRepository.observeMonthlyTotals(monthlyBarsFromEpochDay(date, SPARKLINE_MONTHS)),
            ) { basis, monthly -> basis to monthly }

            combine(
                overviewRepository.observeTotalBalance(),
                overviewRepository.observeAccountBalances(),
                overviewRepository.observeTotals(bounds.first, bounds.last),
                obligationsRepository.observeForMonth(bounds),
                extras,
            ) { balance, accounts, totals, obligations, (basis, monthly) ->
                val spanDays = basis.firstDayEpochDay
                    ?.let { date.toEpochDay() - it + 1 }
                    ?: 1
                val bars = buildMonthlyBars(monthly, date, SPARKLINE_MONTHS)
                OverviewUiState(
                    balanceMinor = balance,
                    accounts = accounts,
                    monthExpenseMinor = totals.expenseMinor,
                    monthIncomeMinor = totals.incomeMinor,
                    obligations = obligations,
                    expenseAverages = averageStats(basis.expenseMinor, spanDays),
                    incomeAverages = averageStats(basis.incomeMinor, spanDays),
                    saldoMinor = basis.incomeMinor - basis.expenseMinor,
                    expenseSpark = bars.map { it.expenseMinor },
                    incomeSpark = bars.map { it.incomeMinor },
                    isLoaded = true,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OverviewUiState(),
        )

    /** Called when the screen is shown again; picks up a date change since last time. */
    fun onRefresh() {
        today.value = LocalDate.now()
    }
}
