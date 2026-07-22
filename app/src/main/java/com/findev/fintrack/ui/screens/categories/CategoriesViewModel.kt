package com.findev.fintrack.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.CategoryInUseException
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.ui.screens.overview.monthBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** An expense category paired with what has been spent against it in the current month. */
data class CategoryRow(
    val category: CategoryEntity,
    val spentThisMonthMinor: Long,
)

data class CategoriesUiState(
    val expense: List<CategoryRow> = emptyList(),
    val income: List<CategoryEntity> = emptyList(),
    val isLoaded: Boolean = false,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val deleteBlockedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits when a delete was refused because the category is still used. */
    val deleteBlocked: Flow<Unit> = deleteBlockedChannel.receiveAsFlow()

    // Budgets track the calendar month the screen is opened in.
    private val month = monthBounds(LocalDate.now())

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.observeAll(),
        transactionRepository.observeExpensesByCategory(month.first, month.last),
    ) { categories, spending ->
        val spentByCategory = spending.associate { it.categoryId to it.totalMinor }
        CategoriesUiState(
            expense = categories.filter { it.type == CategoryType.EXPENSE }
                .map { CategoryRow(it, spentByCategory[it.id] ?: 0L) },
            income = categories.filter { it.type == CategoryType.INCOME },
            isLoaded = true,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    fun onCreate(name: String, type: CategoryType, icon: String, color: Long, monthlyLimitMinor: Long?) {
        viewModelScope.launch { categoryRepository.create(name, type, icon, color, monthlyLimitMinor) }
    }

    fun onSaveEdit(id: String, name: String, icon: String, color: Long, monthlyLimitMinor: Long?) {
        viewModelScope.launch { categoryRepository.update(id, name, icon, color, monthlyLimitMinor) }
    }

    fun onArchiveToggle(id: String, archived: Boolean) {
        viewModelScope.launch { categoryRepository.setArchived(id, archived) }
    }

    fun onDelete(id: String) {
        viewModelScope.launch {
            try {
                categoryRepository.delete(id)
            } catch (_: CategoryInUseException) {
                deleteBlockedChannel.send(Unit)
            }
        }
    }

    /** Persists a new order for one type's categories after a drag; [orderedIds] top to bottom. */
    fun onReorder(orderedIds: List<String>) {
        viewModelScope.launch { categoryRepository.reorder(orderedIds) }
    }
}
