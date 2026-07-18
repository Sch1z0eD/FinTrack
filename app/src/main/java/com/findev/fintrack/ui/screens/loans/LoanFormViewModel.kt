package com.findev.fintrack.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.LoanRepository
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.PrepaymentMode
import com.findev.fintrack.ui.formatRateForInput
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.navigation.LOAN_FORM_ARG_LOAN_ID
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.parseRateToMilliPercent
import com.findev.fintrack.ui.sanitizeAmountInput
import com.findev.fintrack.ui.sanitizeRateInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LoanFormUiState(
    val name: String = "",
    val type: LoanType = LoanType.ANNUITY,
    /** Kept as text; kopecks are derived, exactly like the quick-entry amount. */
    val principalText: String = "",
    /** Percent per year as text. Basis points are hundredths of a percent, so the
     *  kopeck parser fits it unchanged: "16,9" -> 1690. */
    val rateText: String = "",
    val startDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val termText: String = "",
    val paymentDayText: String = "",
    val upfrontFeeText: String = "",
    val monthlyFeeText: String = "",
    /** Days before the payment to remind; blank means no reminder, "0" means on the day. */
    /** Lead times picked, furthest out first. Empty means reminders are off. */
    val reminderDays: List<Int> = listOf(3),
    val reminderEnabled: Boolean = true,
    /** Where "Оплачено" charges the payment from, and what it counts as. */
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val isEditing: Boolean = false,
    /** Payment copied from the contract; blank means "derive it from the rate". */
    val fixedPaymentText: String = "",
    /** The only mode the contract permits, or null when the bank allows both. */
    val allowedPrepaymentMode: PrepaymentMode? = null,
) {
    val principalMinor: Long get() = parseAmountToMinor(principalText)
    val rateMilliPercent: Int get() = parseRateToMilliPercent(rateText)
    val termMonths: Int get() = termText.toIntOrNull() ?: 0
    val paymentDay: Int get() = paymentDayText.toIntOrNull() ?: 0
    val fixedPaymentMinor: Long? get() = parseAmountToMinor(fixedPaymentText).takeIf { it > 0 }
    val upfrontFeeMinor: Long get() = parseAmountToMinor(upfrontFeeText)
    val monthlyFeeMinor: Long get() = parseAmountToMinor(monthlyFeeText)

    /** Blank field means "no reminder"; any digits are the lead days (0 = on the day). */
    /**
     * What gets saved. Driven by [reminderEnabled] rather than by the list being empty:
     * "unpick everything to switch it off" is not something anyone discovers, which is why
     * the loan screen looked like it had no reminder setting at all.
     */
    val savedReminderDays: List<Int> get() = if (reminderEnabled) reminderDays else emptyList()

    /** An instalment plan is 0% by definition, so its rate field is not asked for. */
    val showsRate: Boolean get() = type != LoanType.INSTALLMENT

    /** Only a level-payment loan has a payment to override - see Loan.fixedPaymentMinor. */
    val showsFixedPayment: Boolean get() = type == LoanType.ANNUITY

    val canSave: Boolean
        get() = name.isNotBlank() &&
            principalMinor > 0 &&
            termMonths > 0 &&
            paymentDay in 1..31 &&
            (!showsRate || rateMilliPercent >= 0)
}

@HiltViewModel
class LoanFormViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when creating a new loan. */
    private val editedLoanId: String? = savedStateHandle[LOAN_FORM_ARG_LOAN_ID]

    private val _uiState = MutableStateFlow(LoanFormUiState(isEditing = editedLoanId != null))
    val uiState: StateFlow<LoanFormUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        // Both writers use update{} rather than assignment: the loan being edited and the
        // account list arrive independently, and whichever lands second must not erase
        // what the first put there.
        viewModelScope.launch {
            combine(
                accountRepository.observeActive(),
                categoryRepository.observeByType(CategoryType.EXPENSE),
            ) { accounts, categories -> accounts to categories }
                .collect { (accounts, categories) ->
                    _uiState.update { state ->
                        state.copy(
                            accounts = accounts,
                            selectedAccountId = state.selectedAccountId ?: accounts.firstOrNull()?.id,
                            categories = categories,
                            selectedCategoryId = state.selectedCategoryId
                                ?: categories.firstOrNull()?.id,
                        )
                    }
                }
        }

        editedLoanId?.let { id ->
            viewModelScope.launch {
                val loan = loanRepository.getById(id) ?: return@launch
                _uiState.update { state ->
                    state.copy(
                        name = loan.name,
                        type = loan.type,
                        principalText = formatAmountForInput(loan.principalMinor),
                        rateText = formatRateForInput(loan.rateMilliPercent),
                        startDateEpochDay = loan.startDateEpochDay,
                        termText = loan.termMonths.toString(),
                        paymentDayText = loan.paymentDay.toString(),
                        fixedPaymentText = loan.fixedPaymentMinor?.let(::formatAmountForInput).orEmpty(),
                        allowedPrepaymentMode = loan.allowedPrepaymentMode,
                        upfrontFeeText = formatAmountForInput(loan.upfrontFeeMinor),
                        monthlyFeeText = formatAmountForInput(loan.monthlyFeeMinor),
                        reminderDays = loan.reminderDaysList.ifEmpty { listOf(3) },
                        reminderEnabled = loan.reminderDaysList.isNotEmpty(),
                        // A loan saved before these existed keeps whatever default landed.
                        selectedAccountId = loan.accountId ?: state.selectedAccountId,
                        selectedCategoryId = loan.categoryId ?: state.selectedCategoryId,
                        isEditing = true,
                    )
                }
            }
        }
    }

    fun onAccountSelected(id: String) = _uiState.update { it.copy(selectedAccountId = id) }

    fun onCategorySelected(id: String) = _uiState.update { it.copy(selectedCategoryId = id) }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onTypeChange(type: LoanType) = _uiState.update {
        // A 0% plan cannot carry a rate; the engine would reject the loan outright.
        if (type == LoanType.INSTALLMENT) it.copy(type = type, rateText = "") else it.copy(type = type)
    }

    fun onPrincipalChange(text: String) = _uiState.update {
        it.copy(principalText = sanitizeAmountInput(text))
    }

    fun onFixedPaymentChange(text: String) =
        _uiState.update { it.copy(fixedPaymentText = sanitizeAmountInput(text)) }

    fun onAllowedPrepaymentModeChange(mode: PrepaymentMode?) =
        _uiState.update { it.copy(allowedPrepaymentMode = mode) }

    fun onRateChange(text: String) = _uiState.update { it.copy(rateText = sanitizeRateInput(text)) }

    fun onStartDateChange(epochDay: Long) = _uiState.update { it.copy(startDateEpochDay = epochDay) }

    fun onTermChange(text: String) = _uiState.update {
        it.copy(termText = text.filter(Char::isDigit).take(3))
    }

    fun onPaymentDayChange(text: String) = _uiState.update {
        it.copy(paymentDayText = text.filter(Char::isDigit).take(2))
    }

    fun onUpfrontFeeChange(text: String) = _uiState.update {
        it.copy(upfrontFeeText = sanitizeAmountInput(text))
    }

    fun onMonthlyFeeChange(text: String) = _uiState.update {
        it.copy(monthlyFeeText = sanitizeAmountInput(text))
    }

    /** Toggles one lead time; the rest stay as they were. */
    fun onReminderDayToggle(days: Int) = _uiState.update { state ->
        val next = if (days in state.reminderDays) {
            state.reminderDays - days
        } else {
            state.reminderDays + days
        }
        state.copy(reminderDays = next.sortedDescending())
    }

    fun onReminderEnabledChange(enabled: Boolean) = _uiState.update {
        it.copy(reminderEnabled = enabled)
    }

    fun onDelete() {
        val id = editedLoanId ?: return
        viewModelScope.launch {
            // Soft delete, so the rates and prepayments hanging off it stay recoverable.
            loanRepository.delete(id)
            savedChannel.send(Unit)
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val rateMilliPercent = if (state.showsRate) state.rateMilliPercent else 0
            if (editedLoanId == null) {
                loanRepository.create(
                    name = state.name.trim(),
                    type = state.type,
                    principalMinor = state.principalMinor,
                    rateMilliPercent = rateMilliPercent,
                    startDateEpochDay = state.startDateEpochDay,
                    termMonths = state.termMonths,
                    paymentDay = state.paymentDay,
                    upfrontFeeMinor = state.upfrontFeeMinor,
                    monthlyFeeMinor = state.monthlyFeeMinor,
                    accountId = state.selectedAccountId,
                    categoryId = state.selectedCategoryId,
                    reminderDays = state.savedReminderDays,
                    fixedPaymentMinor = state.fixedPaymentMinor.takeIf { state.showsFixedPayment },
                    allowedPrepaymentMode = state.allowedPrepaymentMode,
                )
            } else {
                loanRepository.update(
                    id = editedLoanId,
                    name = state.name.trim(),
                    type = state.type,
                    principalMinor = state.principalMinor,
                    rateMilliPercent = rateMilliPercent,
                    startDateEpochDay = state.startDateEpochDay,
                    termMonths = state.termMonths,
                    paymentDay = state.paymentDay,
                    upfrontFeeMinor = state.upfrontFeeMinor,
                    monthlyFeeMinor = state.monthlyFeeMinor,
                    accountId = state.selectedAccountId,
                    categoryId = state.selectedCategoryId,
                    reminderDays = state.savedReminderDays,
                    fixedPaymentMinor = state.fixedPaymentMinor.takeIf { state.showsFixedPayment },
                    allowedPrepaymentMode = state.allowedPrepaymentMode,
                )
            }
            savedChannel.send(Unit)
        }
    }
}
