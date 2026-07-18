package com.findev.fintrack.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.MonthlyObligations
import com.findev.fintrack.data.ObligationsRepository
import com.findev.fintrack.data.OverviewRepository
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

data class OverviewUiState(
    val balanceMinor: Long = 0,
    val accounts: List<AccountBalance> = emptyList(),
    val monthExpenseMinor: Long = 0,
    val monthIncomeMinor: Long = 0,
    val obligations: MonthlyObligations = MonthlyObligations(),
    val isLoaded: Boolean = false,
) {
    /**
     * What is left once this month's remaining obligations are covered. Can go negative,
     * and that is the point: hiding it would turn the one number worth acting on into a
     * reassuring zero.
     */
    val freeAfterObligationsMinor: Long get() = balanceMinor - obligations.remainingMinor
}

/** Inclusive epoch-day bounds of the calendar month containing [date]. */
fun monthBounds(date: LocalDate): LongRange {
    val first = date.withDayOfMonth(1)
    val last = date.withDayOfMonth(date.lengthOfMonth())
    return first.toEpochDay()..last.toEpochDay()
}

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val overviewRepository: OverviewRepository,
    private val obligationsRepository: ObligationsRepository,
) : ViewModel() {

    /** Re-read on every refresh so the screen does not stay on last month after midnight. */
    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<OverviewUiState> = today
        .flatMapLatest { date ->
            val bounds = monthBounds(date)
            combine(
                overviewRepository.observeTotalBalance(),
                overviewRepository.observeAccountBalances(),
                overviewRepository.observeTotals(bounds.first, bounds.last),
                obligationsRepository.observeForMonth(bounds),
            ) { balance, accounts, totals, obligations ->
                OverviewUiState(
                    balanceMinor = balance,
                    accounts = accounts,
                    monthExpenseMinor = totals.expenseMinor,
                    monthIncomeMinor = totals.incomeMinor,
                    obligations = obligations,
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
