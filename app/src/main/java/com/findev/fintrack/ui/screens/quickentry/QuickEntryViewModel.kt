package com.findev.fintrack.ui.screens.quickentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.ui.navigation.QUICK_ENTRY_ARG_TRANSACTION_ID
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput
import com.findev.fintrack.ui.screens.overview.monthBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class QuickEntryUiState(
    val amountMinor: Long = 0,
    /** Raw text of the amount field, kept for the system-keyboard input. */
    val amountText: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    /** Destination of a transfer; unused by income and expense. */
    val selectedToAccountId: String? = null,
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val note: String = "",
    /** Monthly budget of the selected expense category, or null when it has none. */
    val selectedCategoryLimitMinor: Long? = null,
    /** Already spent in the selected category this month, before the amount being entered. */
    val selectedCategorySpentMinor: Long = 0,
    /** False until the database has been read, so "no accounts yet" is not confused with "still loading". */
    val isLoaded: Boolean = false,
    /** True when an existing transaction is being edited rather than created. */
    val isEditing: Boolean = false,
) {
    val hasNoAccounts: Boolean get() = isLoaded && accounts.isEmpty()

    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    /** A transfer needs a second account, so one alone cannot make one. */
    val canTransfer: Boolean get() = accounts.size >= 2

    val canSave: Boolean
        get() = amountMinor > 0 && selectedAccountId != null && when (type) {
            // No category on a transfer - it is not spending - but it does need somewhere
            // for the money to land, and somewhere different from where it came from.
            TransactionType.TRANSFER ->
                selectedToAccountId != null && selectedToAccountId != selectedAccountId
            else -> selectedCategoryId != null
        }
}

/** User input; accounts and categories are observed from the database separately. */
private data class InputState(
    /** Source of truth for the amount; kopecks are derived from it. */
    val amountText: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val selectedToAccountId: String? = null,
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val note: String = "",
)

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when creating a new transaction. */
    private val editedTransactionId: String? =
        savedStateHandle[QUICK_ENTRY_ARG_TRANSACTION_ID]

    private val input = MutableStateFlow(InputState())

    // Budgets track the calendar month the entry screen is opened in.
    private val month = monthBounds(LocalDate.now())

    /**
     * When editing an expense that already counts toward this month's spending, its own amount
     * is subtracted from the category total so the "after this entry" projection is not doubled.
     */
    private var editedExpenseThisMonth: Pair<String, Long>? = null

    init {
        editedTransactionId?.let(::prefillFrom)
    }

    private fun prefillFrom(transactionId: String) {
        viewModelScope.launch {
            val existing = transactionRepository.getById(transactionId) ?: return@launch
            val categoryId = existing.categoryId
            if (existing.type == TransactionType.EXPENSE &&
                categoryId != null &&
                existing.dateEpochDay in month
            ) {
                editedExpenseThisMonth = categoryId to existing.amountMinor
            }
            input.value = InputState(
                amountText = formatAmountForInput(existing.amountMinor),
                type = existing.type,
                selectedAccountId = existing.accountId,
                selectedToAccountId = existing.accountToId,
                selectedCategoryId = existing.categoryId,
                dateEpochDay = existing.dateEpochDay,
                note = existing.note.orEmpty(),
            )
        }
    }

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once a transaction is stored, so the screen can navigate back. */
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categories: Flow<List<CategoryEntity>> = input
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeByType(type.toCategoryType()) }

    /** This month's spending per expense category, so the budget hint stays live while entering. */
    private val monthlySpendingByCategory: Flow<Map<String, Long>> =
        transactionRepository.observeExpensesByCategory(month.first, month.last)
            .map { totals -> totals.associate { it.categoryId to it.totalMinor } }

    val uiState: kotlinx.coroutines.flow.StateFlow<QuickEntryUiState> = combine(
        input,
        // Archived accounts must not accept new transactions.
        accountRepository.observeActive(),
        categories,
        monthlySpendingByCategory,
    ) { input, accounts, categories, spending ->
        val selectedCategory = categories.firstOrNull { it.id == input.selectedCategoryId }
        // Exclude the edited transaction's own amount so its projection is not counted twice.
        val editedSameCategory = editedExpenseThisMonth
            ?.takeIf { it.first == input.selectedCategoryId }
            ?.second ?: 0L
        val spentThisMonth = (spending[input.selectedCategoryId] ?: 0L) - editedSameCategory
        QuickEntryUiState(
            amountMinor = parseAmountToMinor(input.amountText),
            amountText = input.amountText,
            type = input.type,
            accounts = accounts,
            // Fall back to the first account so the common case needs no extra tap.
            selectedAccountId = input.selectedAccountId ?: accounts.firstOrNull()?.id,
            categories = categories,
            selectedCategoryId = input.selectedCategoryId,
            selectedToAccountId = input.selectedToAccountId,
            dateEpochDay = input.dateEpochDay,
            note = input.note,
            selectedCategoryLimitMinor = selectedCategory?.monthlyLimitMinor,
            selectedCategorySpentMinor = spentThisMonth.coerceAtLeast(0L),
            isLoaded = true,
            isEditing = editedTransactionId != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuickEntryUiState(),
    )

    fun onAmountTextChange(text: String) = input.update {
        it.copy(amountText = sanitizeAmountInput(text))
    }

    fun onTypeChange(type: TransactionType) = input.update {
        // The previously picked category belongs to the other type.
        it.copy(type = type, selectedCategoryId = null)
    }

    fun onAccountSelected(accountId: String) = input.update {
        // Picking the source that is already the destination would be a transfer to itself;
        // clear the other end rather than silently saving nothing.
        it.copy(
            selectedAccountId = accountId,
            selectedToAccountId = it.selectedToAccountId?.takeIf { to -> to != accountId },
        )
    }

    fun onToAccountSelected(accountId: String) = input.update {
        it.copy(
            selectedToAccountId = accountId,
            selectedAccountId = it.selectedAccountId?.takeIf { from -> from != accountId },
        )
    }

    fun onCategorySelected(categoryId: String) = input.update { it.copy(selectedCategoryId = categoryId) }

    fun onDateSelected(epochDay: Long) = input.update { it.copy(dateEpochDay = epochDay) }

    fun onNoteChange(note: String) = input.update { it.copy(note = note) }

    fun onSave() {
        val state = uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val accountId = requireNotNull(state.selectedAccountId)
            // An empty comment is stored as NULL, not as a blank string.
            val note = state.note.trim().takeIf { it.isNotEmpty() }

            if (state.isTransfer) {
                val toAccountId = requireNotNull(state.selectedToAccountId)
                if (editedTransactionId == null) {
                    transactionRepository.addTransfer(
                        amountMinor = state.amountMinor,
                        fromAccountId = accountId,
                        toAccountId = toAccountId,
                        dateEpochDay = state.dateEpochDay,
                        note = note,
                    )
                } else {
                    transactionRepository.updateTransfer(
                        id = editedTransactionId,
                        amountMinor = state.amountMinor,
                        fromAccountId = accountId,
                        toAccountId = toAccountId,
                        dateEpochDay = state.dateEpochDay,
                        note = note,
                    )
                }
                savedChannel.send(Unit)
                return@launch
            }

            val categoryId = requireNotNull(state.selectedCategoryId)
            if (editedTransactionId == null) {
                transactionRepository.addIncomeOrExpense(
                    type = state.type,
                    amountMinor = state.amountMinor,
                    accountId = accountId,
                    categoryId = categoryId,
                    dateEpochDay = state.dateEpochDay,
                    note = note,
                )
            } else {
                transactionRepository.updateIncomeOrExpense(
                    id = editedTransactionId,
                    type = state.type,
                    amountMinor = state.amountMinor,
                    accountId = accountId,
                    categoryId = categoryId,
                    dateEpochDay = state.dateEpochDay,
                    note = note,
                )
            }
            savedChannel.send(Unit)
        }
    }
}

/** Which category grid to show. A transfer has none, so it never asks. */
private fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    TransactionType.EXPENSE, TransactionType.TRANSFER -> CategoryType.EXPENSE
}
