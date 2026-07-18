package com.findev.fintrack.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Transactions of a single calendar day, newest day first. */
data class TransactionDayGroup(
    val dateEpochDay: Long,
    val items: List<TransactionListItem>,
)

data class TransactionsUiState(
    val groups: List<TransactionDayGroup> = emptyList(),
    val isLoaded: Boolean = false,
) {
    val isEmpty: Boolean get() = isLoaded && groups.isEmpty()
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = transactionRepository.observeList()
        .map { items ->
            TransactionsUiState(
                // The query already sorts by day descending, so grouping preserves that order.
                groups = items
                    .groupBy { it.dateEpochDay }
                    .map { (day, dayItems) -> TransactionDayGroup(day, dayItems) },
                isLoaded = true,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsUiState(),
        )

    fun onDelete(id: String) {
        viewModelScope.launch { transactionRepository.softDelete(id) }
    }

    fun onUndoDelete(id: String) {
        viewModelScope.launch { transactionRepository.restore(id) }
    }
}
