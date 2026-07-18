package com.findev.fintrack.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.PeriodSelection
import com.findev.fintrack.data.StatPeriod
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.data.local.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Transactions of a single calendar day, newest day first. */
data class TransactionDayGroup(
    val dateEpochDay: Long,
    val items: List<TransactionListItem>,
)

/** Which side of the ledger the feed is showing. */
enum class TypeFilter { ALL, EXPENSE, INCOME }

data class TransactionsUiState(
    val groups: List<TransactionDayGroup> = emptyList(),
    val isLoaded: Boolean = false,
    val selection: PeriodSelection = PeriodSelection(),
    val typeFilter: TypeFilter = TypeFilter.ALL,
) {
    val period: StatPeriod get() = selection.period
    val isEmpty: Boolean get() = isLoaded && groups.isEmpty()

    /**
     * Empty because of the filter rather than because nothing was ever entered.
     *
     * Worth telling apart: "заведите первую операцию" is wrong and slightly insulting
     * advice for someone who has simply picked a quiet week.
     */
    val isFilteredEmpty: Boolean
        get() = isEmpty && (period != StatPeriod.ALL || typeFilter != TypeFilter.ALL)
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(PeriodSelection())
    private val typeFilter = MutableStateFlow(TypeFilter.ALL)

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            transactionRepository.observeList(),
            selection,
            typeFilter,
        ) { items, selected, types ->
            // Filtered in memory rather than in SQL: the feed is already fully observed for
            // the undo flow, and re-querying per period would trade a live list for a
            // reload. If the history ever outgrows that, this becomes a DAO range query.
            val bounds = selected.bounds(LocalDate.now())
            val visible = items
                .filter { bounds == null || it.dateEpochDay in bounds.first..bounds.second }
                .filter {
                    when (types) {
                        TypeFilter.ALL -> true
                        TypeFilter.EXPENSE -> it.type == TransactionType.EXPENSE
                        TypeFilter.INCOME -> it.type == TransactionType.INCOME
                    }
                }
            TransactionsUiState(
                // The query already sorts by day descending, so grouping preserves that order.
                groups = visible
                    .groupBy { it.dateEpochDay }
                    .map { (day, dayItems) -> TransactionDayGroup(day, dayItems) },
                isLoaded = true,
                selection = selected,
                typeFilter = types,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsUiState(),
        )

    fun onPeriodChange(period: StatPeriod) {
        selection.update { it.copy(period = period) }
    }

    fun onCustomRangeChange(fromEpochDay: Long?, toEpochDay: Long?) {
        selection.value = PeriodSelection(StatPeriod.CUSTOM, fromEpochDay, toEpochDay)
    }

    fun onTypeFilterChange(filter: TypeFilter) {
        typeFilter.value = filter
    }

    fun onDelete(id: String) {
        viewModelScope.launch { transactionRepository.softDelete(id) }
    }

    fun onUndoDelete(id: String) {
        viewModelScope.launch { transactionRepository.restore(id) }
    }
}
