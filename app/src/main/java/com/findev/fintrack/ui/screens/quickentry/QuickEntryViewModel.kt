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
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val note: String = "",
    /** False until the database has been read, so "no accounts yet" is not confused with "still loading". */
    val isLoaded: Boolean = false,
    /** True when an existing transaction is being edited rather than created. */
    val isEditing: Boolean = false,
) {
    val hasNoAccounts: Boolean get() = isLoaded && accounts.isEmpty()
    val canSave: Boolean
        get() = amountMinor > 0 && selectedAccountId != null && selectedCategoryId != null
}

/** User input; accounts and categories are observed from the database separately. */
private data class InputState(
    /** Source of truth for the amount; kopecks are derived from it. */
    val amountText: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
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

    init {
        editedTransactionId?.let(::prefillFrom)
    }

    private fun prefillFrom(transactionId: String) {
        viewModelScope.launch {
            val existing = transactionRepository.getById(transactionId) ?: return@launch
            input.value = InputState(
                amountText = formatAmountForInput(existing.amountMinor),
                type = existing.type,
                selectedAccountId = existing.accountId,
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

    val uiState: kotlinx.coroutines.flow.StateFlow<QuickEntryUiState> = combine(
        input,
        // Archived accounts must not accept new transactions.
        accountRepository.observeActive(),
        categories,
    ) { input, accounts, categories ->
        QuickEntryUiState(
            amountMinor = parseAmountToMinor(input.amountText),
            amountText = input.amountText,
            type = input.type,
            accounts = accounts,
            // Fall back to the first account so the common case needs no extra tap.
            selectedAccountId = input.selectedAccountId ?: accounts.firstOrNull()?.id,
            categories = categories,
            selectedCategoryId = input.selectedCategoryId,
            dateEpochDay = input.dateEpochDay,
            note = input.note,
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

    fun onAccountSelected(accountId: String) = input.update { it.copy(selectedAccountId = accountId) }

    fun onCategorySelected(categoryId: String) = input.update { it.copy(selectedCategoryId = categoryId) }

    fun onDateSelected(epochDay: Long) = input.update { it.copy(dateEpochDay = epochDay) }

    fun onNoteChange(note: String) = input.update { it.copy(note = note) }

    fun onSave() {
        val state = uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val accountId = requireNotNull(state.selectedAccountId)
            val categoryId = requireNotNull(state.selectedCategoryId)
            // An empty comment is stored as NULL, not as a blank string.
            val note = state.note.trim().takeIf { it.isNotEmpty() }

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

private fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    TransactionType.EXPENSE -> CategoryType.EXPENSE
    TransactionType.TRANSFER -> error("Quick entry does not create transfers")
}
