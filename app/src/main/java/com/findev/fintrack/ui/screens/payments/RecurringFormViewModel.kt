package com.findev.fintrack.ui.screens.payments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.RecurringPaymentRepository
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.data.local.entity.RecurrencePeriod
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.navigation.RECURRING_FORM_ARG_ID
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecurringFormUiState(
    val name: String = "",
    /** Kept as text; kopecks are derived, exactly like every other amount field. */
    val amountText: String = "",
    val period: RecurrencePeriod = RecurrencePeriod.MONTH,
    val startDateEpochDay: Long = LocalDate.now().toEpochDay(),
    /** Null means open-ended. */
    val endDateEpochDay: Long? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val reminderEnabled: Boolean = true,
    val isEditing: Boolean = false,
) {
    val amountMinor: Long get() = parseAmountToMinor(amountText)

    val canSave: Boolean
        get() = name.isNotBlank() &&
            amountMinor > 0 &&
            selectedAccountId != null &&
            selectedCategoryId != null &&
            // An end before the start would describe a payment that never comes due.
            (endDateEpochDay == null || endDateEpochDay >= startDateEpochDay)
}

@HiltViewModel
class RecurringFormViewModel @Inject constructor(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when creating. */
    private val editedId: String? = savedStateHandle[RECURRING_FORM_ARG_ID]

    /** What the user typed; accounts and categories are observed separately. */
    private data class Input(
        val name: String = "",
        val amountText: String = "",
        val period: RecurrencePeriod = RecurrencePeriod.MONTH,
        val startDateEpochDay: Long = LocalDate.now().toEpochDay(),
        val endDateEpochDay: Long? = null,
        val selectedAccountId: String? = null,
        val selectedCategoryId: String? = null,
        val reminderEnabled: Boolean = true,
    )

    private val input = MutableStateFlow(Input())

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    val uiState: StateFlow<RecurringFormUiState> = combine(
        input,
        accountRepository.observeActive(),
        // An obligation is always money going out, so only expense categories apply.
        categoryRepository.observeByType(CategoryType.EXPENSE),
    ) { current, accounts, categories ->
        RecurringFormUiState(
            name = current.name,
            amountText = current.amountText,
            period = current.period,
            startDateEpochDay = current.startDateEpochDay,
            endDateEpochDay = current.endDateEpochDay,
            accounts = accounts,
            // Fall back to the first of each so a fresh form is already valid enough to fill in.
            selectedAccountId = current.selectedAccountId ?: accounts.firstOrNull()?.id,
            categories = categories,
            selectedCategoryId = current.selectedCategoryId ?: categories.firstOrNull()?.id,
            reminderEnabled = current.reminderEnabled,
            isEditing = editedId != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringFormUiState(isEditing = editedId != null),
    )

    init {
        editedId?.let { id ->
            viewModelScope.launch {
                val payment = recurringPaymentRepository.getById(id) ?: return@launch
                input.value = Input(
                    name = payment.name,
                    amountText = formatAmountForInput(payment.amountMinor),
                    period = payment.period,
                    startDateEpochDay = payment.startDateEpochDay,
                    endDateEpochDay = payment.endDateEpochDay,
                    selectedAccountId = payment.accountId,
                    selectedCategoryId = payment.categoryId,
                    reminderEnabled = payment.reminderEnabled,
                )
            }
        }
    }

    fun onNameChange(name: String) = input.update { it.copy(name = name) }

    fun onAmountChange(text: String) = input.update { it.copy(amountText = sanitizeAmountInput(text)) }

    fun onPeriodChange(period: RecurrencePeriod) = input.update { it.copy(period = period) }

    fun onStartDateChange(epochDay: Long) = input.update { it.copy(startDateEpochDay = epochDay) }

    fun onEndDateChange(epochDay: Long?) = input.update { it.copy(endDateEpochDay = epochDay) }

    fun onAccountSelected(id: String) = input.update { it.copy(selectedAccountId = id) }

    fun onCategorySelected(id: String) = input.update { it.copy(selectedCategoryId = id) }

    fun onReminderChange(enabled: Boolean) = input.update { it.copy(reminderEnabled = enabled) }

    fun onSave() {
        val state = uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val accountId = state.selectedAccountId ?: return@launch
            val categoryId = state.selectedCategoryId ?: return@launch

            if (editedId == null) {
                recurringPaymentRepository.create(
                    name = state.name.trim(),
                    amountMinor = state.amountMinor,
                    period = state.period,
                    startDateEpochDay = state.startDateEpochDay,
                    endDateEpochDay = state.endDateEpochDay,
                    accountId = accountId,
                    categoryId = categoryId,
                    reminderEnabled = state.reminderEnabled,
                )
            } else {
                recurringPaymentRepository.update(
                    id = editedId,
                    name = state.name.trim(),
                    amountMinor = state.amountMinor,
                    period = state.period,
                    startDateEpochDay = state.startDateEpochDay,
                    endDateEpochDay = state.endDateEpochDay,
                    accountId = accountId,
                    categoryId = categoryId,
                    reminderEnabled = state.reminderEnabled,
                )
            }
            savedChannel.send(Unit)
        }
    }

    fun onDelete() {
        val id = editedId ?: return
        viewModelScope.launch {
            recurringPaymentRepository.delete(id)
            savedChannel.send(Unit)
        }
    }
}
