package com.findev.fintrack.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.MonthlyBar
import com.findev.fintrack.data.StatPeriod
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.buildMonthlyBars
import com.findev.fintrack.data.local.CategoryTotal
import com.findev.fintrack.data.monthlyBarsFromEpochDay
import com.findev.fintrack.data.range
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One category's share of the period's spending. */
data class CategorySlice(
    val total: CategoryTotal,
    /** 0..1 of the period total, for the ring and the legend percentage. */
    val fraction: Float,
)

/** An account the stats can be scoped to. The "all accounts" choice is a null id, added by the UI. */
data class AccountOption(val id: String, val name: String)

/** The category whose trend the monthly chart is showing, kept so its label/color survive period changes. */
data class CategoryRef(val id: String, val name: String, val icon: String, val color: Long)

data class StatisticsUiState(
    val period: StatPeriod = StatPeriod.THIS_MONTH,
    val slices: List<CategorySlice> = emptyList(),
    val totalMinor: Long = 0,
    /** Last months' bars: income+expense in overview, one category's expense when [selectedCategory] is set. */
    val months: List<MonthlyBar> = emptyList(),
    val accounts: List<AccountOption> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedCategory: CategoryRef? = null,
    val isLoaded: Boolean = false,
) {
    val isEmpty: Boolean get() = isLoaded && slices.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val period = MutableStateFlow(StatPeriod.THIS_MONTH)
    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val selectedCategory = MutableStateFlow<CategoryRef?>(null)

    private val accounts = accountRepository.observeActive().map { list ->
        list.map { AccountOption(it.id, it.name) }
    }

    private val categorySlices = combine(period, selectedAccountId) { p, acc -> p to acc }
        .flatMapLatest { (p, acc) ->
            val (from, to) = p.range(LocalDate.now())
            transactionRepository.observeExpensesByCategory(from, to, acc).map { categories ->
                val total = categories.sumOf { it.totalMinor }
                val slices = categories.map {
                    CategorySlice(it, if (total > 0) it.totalMinor.toFloat() / total else 0f)
                }
                Triple(p, slices, total)
            }
        }

    // The monthly trend ignores the period chip (a bar-per-month view needs many months) but
    // follows the account filter and the tapped category.
    private val monthlyBars = combine(selectedAccountId, selectedCategory) { acc, cat -> acc to cat }
        .flatMapLatest { (acc, cat) ->
            val from = monthlyBarsFromEpochDay(LocalDate.now())
            val source = if (cat == null) {
                transactionRepository.observeMonthlyTotals(from, acc)
            } else {
                transactionRepository.observeMonthlyCategoryExpenses(from, cat.id, acc)
            }
            source.map { totals -> buildMonthlyBars(totals, LocalDate.now()) }
        }

    val uiState: StateFlow<StatisticsUiState> = combine(
        categorySlices,
        monthlyBars,
        accounts,
        selectedAccountId,
        selectedCategory,
    ) { (p, slices, total), months, accountList, accountId, category ->
        StatisticsUiState(
            period = p,
            slices = slices,
            totalMinor = total,
            months = months,
            accounts = accountList,
            selectedAccountId = accountId,
            selectedCategory = category,
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState(),
    )

    fun onPeriodChange(newPeriod: StatPeriod) {
        period.value = newPeriod
    }

    fun onAccountChange(accountId: String?) {
        selectedAccountId.value = accountId
    }

    /** Tapping a category shows its monthly trend; tapping the selected one again clears it. */
    fun onCategoryToggle(category: CategoryTotal) {
        selectedCategory.value = if (selectedCategory.value?.id == category.categoryId) {
            null
        } else {
            CategoryRef(category.categoryId, category.categoryName, category.categoryIcon, category.categoryColor)
        }
    }

    fun onClearCategory() {
        selectedCategory.value = null
    }
}
