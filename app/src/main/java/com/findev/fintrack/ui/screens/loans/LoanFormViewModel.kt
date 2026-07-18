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
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.navigation.LOAN_FORM_ARG_LOAN_ID
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput
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
    val reminderDaysText: String = "3",
    /** Where "Оплачено" charges the payment from, and what it counts as. */
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val isEditing: Boolean = false,
) {
    val principalMinor: Long get() = parseAmountToMinor(principalText)
    val rateBp: Int get() = parseAmountToMinor(rateText).toInt()
    val termMonths: Int get() = termText.toIntOrNull() ?: 0
    val paymentDay: Int get() = paymentDayText.toIntOrNull() ?: 0
    val upfrontFeeMinor: Long get() = parseAmountToMinor(upfrontFeeText)
    val monthlyFeeMinor: Long get() = parseAmountToMinor(monthlyFeeText)

    /** Blank field means "no reminder"; any digits are the lead days (0 = on the day). */
    val reminderDaysBefore: Int? get() = reminderDaysText.toIntOrNull()

    /** An instalment plan is 0% by definition, so its rate field is not asked for. */
    val showsRate: Boolean get() = type != LoanType.INSTALLMENT

    val canSave: Boolean
        get() = name.isNotBlank() &&
            principalMinor > 0 &&
            termMonths > 0 &&
            paymentDay in 1..31 &&
            (!showsRate || rateBp >= 0)
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
                        rateText = formatAmountForInput(loan.rateBp.toLong()),
                        startDateEpochDay = loan.startDateEpochDay,
                        termText = loan.termMonths.toString(),
                        paymentDayText = loan.paymentDay.toString(),
                        upfrontFeeText = formatAmountForInput(loan.upfrontFeeMinor),
                        monthlyFeeText = formatAmountForInput(loan.monthlyFeeMinor),
                        reminderDaysText = loan.reminderDaysBefore?.toString() ?: "",
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

    fun onRateChange(text: String) = _uiState.update { it.copy(rateText = sanitizeAmountInput(text)) }

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

    fun onReminderDaysChange(text: String) = _uiState.update {
        it.copy(reminderDaysText = text.filter(Char::isDigit).take(2))
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
            val rateBp = if (state.showsRate) state.rateBp else 0
            if (editedLoanId == null) {
                loanRepository.create(
                    name = state.name.trim(),
                    type = state.type,
                    principalMinor = state.principalMinor,
                    rateBp = rateBp,
                    startDateEpochDay = state.startDateEpochDay,
                    termMonths = state.termMonths,
                    paymentDay = state.paymentDay,
                    upfrontFeeMinor = state.upfrontFeeMinor,
                    monthlyFeeMinor = state.monthlyFeeMinor,
                    accountId = state.selectedAccountId,
                    categoryId = state.selectedCategoryId,
                    reminderDaysBefore = state.reminderDaysBefore,
                )
            } else {
                loanRepository.update(
                    id = editedLoanId,
                    name = state.name.trim(),
                    type = state.type,
                    principalMinor = state.principalMinor,
                    rateBp = rateBp,
                    startDateEpochDay = state.startDateEpochDay,
                    termMonths = state.termMonths,
                    paymentDay = state.paymentDay,
                    upfrontFeeMinor = state.upfrontFeeMinor,
                    monthlyFeeMinor = state.monthlyFeeMinor,
                    accountId = state.selectedAccountId,
                    categoryId = state.selectedCategoryId,
                    reminderDaysBefore = state.reminderDaysBefore,
                )
            }
            savedChannel.send(Unit)
        }
    }
}
