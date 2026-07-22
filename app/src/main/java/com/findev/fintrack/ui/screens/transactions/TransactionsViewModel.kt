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

/** One category the feed can be narrowed to, drawn from the transactions actually present. */
data class CategoryOption(
    val id: String,
    val name: String,
    val icon: String?,
)

data class TransactionsUiState(
    val groups: List<TransactionDayGroup> = emptyList(),
    val isLoaded: Boolean = false,
    val selection: PeriodSelection = PeriodSelection(),
    val typeFilter: TypeFilter = TypeFilter.ALL,
    /** Categories to choose from - only those that appear under the current type filter. */
    val categoryOptions: List<CategoryOption> = emptyList(),
    /** Category id the feed is narrowed to, or null for every category. */
    val categoryFilter: String? = null,
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
        get() = isEmpty &&
            (period != StatPeriod.ALL || typeFilter != TypeFilter.ALL || categoryFilter != null)
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(PeriodSelection())
    private val typeFilter = MutableStateFlow(TypeFilter.ALL)
    private val categoryFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            transactionRepository.observeList(),
            selection,
            typeFilter,
            categoryFilter,
        ) { items, selected, types, category ->
            // Filtered in memory rather than in SQL: the feed is already fully observed for
            // the undo flow, and re-querying per period would trade a live list for a
            // reload. If the history ever outgrows that, this becomes a DAO range query.
            val bounds = selected.bounds(LocalDate.now())
            val ofType = items.filter {
                when (types) {
                    TypeFilter.ALL -> true
                    TypeFilter.EXPENSE -> it.type == TransactionType.EXPENSE
                    TypeFilter.INCOME -> it.type == TransactionType.INCOME
                }
            }

            // The category picker offers only categories that actually occur under the current
            // type, so it never lists an option that would show nothing. Transfers carry none.
            val options = ofType
                .filter { it.categoryId != null }
                .distinctBy { it.categoryId }
                .map { CategoryOption(it.categoryId!!, it.categoryName.orEmpty(), it.categoryIcon) }
                .sortedBy { it.name.lowercase() }
            // A category that no longer exists under this type (type switched) stops filtering.
            val activeCategory = category?.takeIf { id -> options.any { it.id == id } }

            val visible = ofType
                .filter { bounds == null || it.dateEpochDay in bounds.first..bounds.second }
                .filter { activeCategory == null || it.categoryId == activeCategory }
            TransactionsUiState(
                // The query already sorts by day descending, so grouping preserves that order.
                groups = visible
                    .groupBy { it.dateEpochDay }
                    .map { (day, dayItems) -> TransactionDayGroup(day, dayItems) },
                isLoaded = true,
                selection = selected,
                typeFilter = types,
                categoryOptions = options,
                categoryFilter = activeCategory,
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
        // Categories differ between income and expense; a stale pick would filter to nothing.
        categoryFilter.value = null
    }

    fun onCategoryFilterChange(categoryId: String?) {
        categoryFilter.value = categoryId
    }

    fun onDelete(id: String) {
        viewModelScope.launch { transactionRepository.softDelete(id) }
    }

    fun onUndoDelete(id: String) {
        viewModelScope.launch { transactionRepository.restore(id) }
    }
}
