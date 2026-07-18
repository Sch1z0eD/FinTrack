package com.findev.fintrack.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.LoanRepository
import com.findev.fintrack.data.LoanWithSchedule
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.entity.LoanEntity
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.dao.SettlementRow
import com.findev.fintrack.data.local.entity.PrepaymentMode
import com.findev.fintrack.data.toEngineLoan
import com.findev.fintrack.data.toPrepayment
import com.findev.fintrack.data.toRateChange
import com.findev.fintrack.loanengine.LoanSummary
import com.findev.fintrack.loanengine.PrepaymentEffect
import com.findev.fintrack.loanengine.PrepaymentSimulation
import com.findev.fintrack.loanengine.ScheduleEntry
import com.findev.fintrack.loanengine.balanceOn
import com.findev.fintrack.loanengine.simulatePrepayment
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.navigation.LOAN_DETAIL_ARG_LOAN_ID
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput
import com.findev.fintrack.ui.screens.payments.PayDialogState
import com.findev.fintrack.ui.screens.payments.settle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Why the simulation has nothing to say about the date the user picked. */
enum class PrepaymentDateError { BEFORE_START, AFTER_CLOSING }

/**
 * The prepayment dialog: an amount, a date, and what the engine says both modes would do
 * with them. The comparison is the form - picking a mode here is picking the answer.
 */
data class PrepaymentDialogState(
    val amountText: String = "",
    val dateEpochDay: Long,
    val mode: PrepaymentMode = PrepaymentMode.REDUCE_TERM,
    /**
     * The only mode this contract allows, or null when the bank permits both.
     *
     * When it is set the dialog shows that one alone: modelling the other would promise a
     * schedule the bank will not produce.
     */
    val allowedMode: PrepaymentMode? = null,
    /** Null until the amount and date are good enough to simulate. */
    val simulation: PrepaymentSimulation? = null,
    val dateError: PrepaymentDateError? = null,
) {
    val amountMinor: Long get() = parseAmountToMinor(amountText)
    val canSave: Boolean get() = simulation != null

    /** The effect of the mode currently selected. */
    val selectedEffect: PrepaymentEffect?
        get() = when (mode) {
            PrepaymentMode.REDUCE_TERM -> simulation?.reduceTerm
            PrepaymentMode.REDUCE_PAYMENT -> simulation?.reducePayment
        }
}

data class LoanDetailUiState(
    val loan: LoanEntity? = null,
    val schedule: List<ScheduleEntry> = emptyList(),
    val summary: LoanSummary? = null,
    val prepayments: List<LoanPrepaymentEntity> = emptyList(),
    /** Debt outstanding today. */
    val balanceMinor: Long = 0,
    val nextPayment: ScheduleEntry? = null,
    /** Every payment ever booked against this loan, newest first. */
    val settlements: List<SettlementRow> = emptyList(),
    /** So the card can tell a prepayment that happened from one that is merely planned. */
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    /** True while the next payment is due and there is somewhere to charge it. */
    val canMarkPaid: Boolean = false,
    /** Known paid, next one still ahead. Same rule as the payments list. */
    val isSettled: Boolean = false,
    /** Null while the dialog is closed. */
    val dialog: PrepaymentDialogState? = null,
    val isLoaded: Boolean = false,
)

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _payDialog = MutableStateFlow<PayDialogState?>(null)
    val payDialog: StateFlow<PayDialogState?> = _payDialog.asStateFlow()

    private val loanId: String = requireNotNull(savedStateHandle[LOAN_DETAIL_ARG_LOAN_ID]) {
        "The loan card cannot open without a loan id"
    }

    /** What the user has typed. The simulation is derived from it, never stored. */
    private data class DialogInput(
        val amountText: String,
        val dateEpochDay: Long,
        val mode: PrepaymentMode,
    )

    private val today = MutableStateFlow(LocalDate.now())
    private val dialogInput = MutableStateFlow<DialogInput?>(null)

    val uiState: StateFlow<LoanDetailUiState> = combine(
        loanRepository.observeWithSchedule(loanId),
        transactionRepository.observePaidThrough(),
        transactionRepository.observeSettlements(loanId),
        today,
        dialogInput,
    ) { withSchedule, paidThroughAll, settlements, date, input ->
        if (withSchedule == null) return@combine LoanDetailUiState(isLoaded = true)

        val loan = withSchedule.loan
        // Same rule as the payments list: nothing marked means the calendar decides and
        // the past is left alone; once something is marked, the next one may be overdue.
        val paidThrough = paidThroughAll[loanId]?.let(LocalDate::ofEpochDay)
        val next = if (paidThrough == null) {
            withSchedule.schedule.firstOrNull { !it.date.isBefore(date) }
        } else {
            withSchedule.schedule.firstOrNull { it.date.isAfter(paidThrough) }
        }

        LoanDetailUiState(
            loan = loan,
            schedule = withSchedule.schedule,
            summary = withSchedule.summary,
            prepayments = withSchedule.prepayments,
            balanceMinor = balanceOn(
                loan = loan.toEngineLoan(),
                schedule = withSchedule.schedule,
                prepayments = withSchedule.prepayments.map { it.toPrepayment() },
                date = date,
            ),
            nextPayment = next,
            settlements = settlements,
            todayEpochDay = date.toEpochDay(),
            canMarkPaid = next != null && !next.date.isAfter(date) &&
                loan.accountId != null && loan.categoryId != null,
            isSettled = paidThrough != null && next != null && next.date.isAfter(date),
            dialog = input?.let { simulate(withSchedule, it) },
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LoanDetailUiState(),
    )

    /**
     * Runs the typed amount through the engine, or explains why it cannot.
     *
     * The engine rejects a date outside the loan's life outright, so the bounds are
     * checked here first: an out-of-range date is something to tell the user about,
     * not something to crash the card with.
     */
    private fun simulate(data: LoanWithSchedule, input: DialogInput): PrepaymentDialogState {
        val date = LocalDate.ofEpochDay(input.dateEpochDay)
        val loan = data.loan.toEngineLoan()

        val dateError = when {
            !date.isAfter(loan.startDate) -> PrepaymentDateError.BEFORE_START
            date.isAfter(data.summary.closingDate) -> PrepaymentDateError.AFTER_CLOSING
            else -> null
        }
        val amountMinor = parseAmountToMinor(input.amountText)

        val simulation = if (dateError == null && amountMinor > 0) {
            simulatePrepayment(
                loan = loan,
                rateChanges = data.rates.map { it.toRateChange() },
                existingPrepayments = data.prepayments.map { it.toPrepayment() },
                date = date,
                amountMinor = amountMinor,
            )
        } else {
            null
        }

        return PrepaymentDialogState(
            amountText = input.amountText,
            dateEpochDay = input.dateEpochDay,
            mode = input.mode,
            allowedMode = data.loan?.allowedPrepaymentMode,
            simulation = simulation,
            dateError = dateError,
        )
    }

    fun onRefresh() {
        today.value = LocalDate.now()
    }

    fun onAddPrepaymentClick() {
        // The next payment date is where a prepayment usually goes, and it is always
        // inside the loan's life - unlike today, once the loan is paid off.
        val default = uiState.value.nextPayment?.date ?: LocalDate.now()
        // Start on the mode the contract allows, so the dialog never opens on a choice
        // that is about to be hidden.
        val allowed = uiState.value.loan?.allowedPrepaymentMode
        dialogInput.value = DialogInput(
            amountText = "",
            dateEpochDay = default.toEpochDay(),
            mode = allowed ?: PrepaymentMode.REDUCE_TERM,
        )
    }

    fun onDialogDismiss() {
        dialogInput.value = null
    }

    fun onDialogAmountChange(text: String) = dialogInput.update {
        it?.copy(amountText = sanitizeAmountInput(text))
    }

    fun onDialogDateChange(epochDay: Long) = dialogInput.update { it?.copy(dateEpochDay = epochDay) }

    fun onDialogModeChange(mode: PrepaymentMode) = dialogInput.update { it?.copy(mode = mode) }

    fun onDialogConfirm() {
        val dialog = uiState.value.dialog ?: return
        if (!dialog.canSave) return

        viewModelScope.launch {
            loanRepository.addPrepayment(
                loanId = loanId,
                amountMinor = dialog.amountMinor,
                dateEpochDay = dialog.dateEpochDay,
                mode = dialog.mode,
            )
            dialogInput.value = null
        }
    }

    fun onDeletePrepayment(id: String) {
        viewModelScope.launch { loanRepository.deletePrepayment(id) }
    }

    fun onMarkPaidClick() {
        val state = uiState.value
        if (!state.canMarkPaid) return
        val loan = state.loan ?: return
        val next = state.nextPayment ?: return

        _payDialog.value = PayDialogState(
            paymentId = loan.id,
            name = loan.name,
            dueDate = next.date,
            accountId = loan.accountId ?: return,
            categoryId = loan.categoryId ?: return,
            amountText = formatAmountForInput(next.paymentMinor),
            dateEpochDay = today.value.toEpochDay(),
            dueAmountMinor = next.paymentMinor,
        )
    }

    fun onPayAmountChange(text: String) = _payDialog.update {
        it?.copy(amountText = sanitizeAmountInput(text))
    }

    fun onPayPartialChange(partial: Boolean) = _payDialog.update {
        it?.copy(partialOverride = partial)
    }

    fun onPayDateChange(epochDay: Long) = _payDialog.update { it?.copy(dateEpochDay = epochDay) }

    fun onPayDismiss() {
        _payDialog.value = null
    }

    fun onPayConfirm() {
        val dialog = _payDialog.value ?: return
        if (!dialog.canSave) return
        _payDialog.value = null
        viewModelScope.launch { transactionRepository.settle(dialog) }
    }
}
