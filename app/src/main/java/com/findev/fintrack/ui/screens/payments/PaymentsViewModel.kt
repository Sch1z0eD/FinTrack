package com.findev.fintrack.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.LoanRepository
import com.findev.fintrack.data.LoanWithSchedule
import com.findev.fintrack.data.RecurringPaymentRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.dao.SettlementRow
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.RecurrencePeriod
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.data.nextDueRecurrence
import com.findev.fintrack.data.nextLoanDue
import com.findev.fintrack.data.nthRecurrence
import com.findev.fintrack.data.settledRecurrences
import com.findev.fintrack.data.toEngineLoan
import com.findev.fintrack.data.toPrepayment
import com.findev.fintrack.data.totalRecurrences
import com.findev.fintrack.loanengine.balanceOn
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

/**
 * One row of the "Платежи" list. Loans and recurring payments are different enough to
 * keep apart in storage but answer the same two questions here: when, and how much.
 */
sealed interface PaymentItem {
    val id: String
    val name: String

    /** Null when nothing is due any more: the loan is repaid, or the payment has ended. */
    val dueDate: LocalDate?
    val dueAmountMinor: Long

    /** True once a due date has passed with nothing marked paid for it. */
    val isOverdue: Boolean

    /** Something is payable right now: the due date is today or already behind. */
    val isDue: Boolean

    /**
     * Known paid, with the next date still ahead. Not the same as [isDue] being false: a
     * payment that has never been marked owes nothing yet either, but there is nothing to
     * congratulate the user about.
     */
    val isSettled: Boolean

    data class Loan(
        override val id: String,
        override val name: String,
        override val dueDate: LocalDate?,
        override val dueAmountMinor: Long,
        override val isOverdue: Boolean,
        override val isDue: Boolean,
        override val isSettled: Boolean,
        val type: LoanType,
        val principalMinor: Long,
        /** Debt still outstanding today. */
        val balanceMinor: Long,
        val overpaymentMinor: Long,
        val accountId: String?,
        val categoryId: String?,
        /** Paid so far against the instalment now due. Zero once it closes and the next one starts. */
        val paidTowardsDueMinor: Long = 0,
        /** Lead times this loan reminds at, furthest out first; empty when off. */
        val reminderDays: List<Int> = emptyList(),
    ) : PaymentItem {
        val isClosed: Boolean get() = balanceMinor == 0L

        /** What is still needed to close the instalment now due. */
        val dueRemainingMinor: Long
            get() = (dueAmountMinor - paidTowardsDueMinor).coerceAtLeast(0)

        /** 0..1 of the instalment now due, for the part-payment bar. */
        val duePaidFraction: Float
            get() = if (dueAmountMinor <= 0) 0f
            else (paidTowardsDueMinor.toFloat() / dueAmountMinor.toFloat()).coerceIn(0f, 1f)

        /**
         * An interest-only loan repays nothing until its balloon, so a progress bar would
         * read zero for years and then jump to full - a countdown drawn as progress.
         */
        val showsProgress: Boolean get() = type != LoanType.INTEREST_ONLY

        /**
         * How much of the debt is gone, 0..1. Float is fine here - it drives a progress
         * bar, not money; the kopecks behind it stay Long.
         */
        val progress: Float
            get() = if (principalMinor == 0L) {
                1f
            } else {
                (principalMinor - balanceMinor).toFloat() / principalMinor.toFloat()
            }
    }

    data class Recurring(
        override val id: String,
        override val name: String,
        override val dueDate: LocalDate?,
        override val dueAmountMinor: Long,
        override val isOverdue: Boolean,
        override val isDue: Boolean,
        override val isSettled: Boolean,
        val period: RecurrencePeriod,
        val reminderEnabled: Boolean,
        val accountId: String,
        val categoryId: String,
        /** The anchor every occurrence is counted from. */
        val startDateEpochDay: Long,
        /** Null when open-ended: a subscription is not progressing towards anything. */
        val totalCount: Int?,
        val settledCount: Int,
        /** Paid so far against the occurrence now due. Zero once it closes and the next one starts. */
        val paidTowardsDueMinor: Long = 0,
    ) : PaymentItem {
        val hasEnded: Boolean get() = dueDate == null

        /** What is still needed to close the occurrence now due. */
        val dueRemainingMinor: Long
            get() = (dueAmountMinor - paidTowardsDueMinor).coerceAtLeast(0)

        /** 0..1 of the occurrence now due, for the part-payment bar. */
        val duePaidFraction: Float
            get() = if (dueAmountMinor <= 0) 0f
            else (paidTowardsDueMinor.toFloat() / dueAmountMinor.toFloat()).coerceIn(0f, 1f)

        /** Only a payment with an end has a finish line to draw. */
        val showsProgress: Boolean get() = totalCount != null

        val remainingCount: Int get() = ((totalCount ?: 0) - settledCount).coerceAtLeast(0)

        /** What is still owed over the whole term, at today's amount. */
        val remainingMinor: Long get() = remainingCount * dueAmountMinor

        val progress: Float
            get() {
                val total = totalCount ?: return 0f
                if (total == 0) return 1f
                return settledCount.toFloat() / total.toFloat()
            }
    }
}

/**
 * The "Оплачено" confirmation. The amount is editable and prefilled, never assumed: a
 * utility bill is a different number every month, and the balance has to follow what was
 * actually paid rather than what was expected.
 *
 * Plain fields rather than a [PaymentItem], so the loan card can raise the same dialog
 * without owning a list item it has no other use for.
 */
data class PayDialogState(
    val paymentId: String,
    val name: String,
    /** The occurrence being settled - not the day the money moves. */
    val dueDate: LocalDate,
    val accountId: String,
    val categoryId: String,
    val amountText: String,
    val dateEpochDay: Long,
    /** What the occurrence was expected to cost, for the full/partial default. */
    val dueAmountMinor: Long = 0,
    /** Set once the user touches the switch; before that the amount decides. */
    val partialOverride: Boolean? = null,
) {
    val amountMinor: Long get() = parseAmountToMinor(amountText)
    val canSave: Boolean get() = amountMinor > 0

    /**
     * Whether this closes the occurrence.
     *
     * Defaults from the amount - paying less than expected is almost always a part payment
     * - but stays a choice, because a smaller amount is sometimes genuinely the whole thing:
     * a final instalment is short by design, and a bank can agree to less.
     */
    val isPartial: Boolean
        get() = partialOverride ?: (dueAmountMinor > 0 && amountMinor in 1 until dueAmountMinor)
}

/**
 * Books the confirmed payment as the expense that settles it.
 *
 * An extension here rather than a method on the repository: the dialog is a UI shape and
 * the data layer has no business knowing it exists. Both the list and the loan card raise
 * the same dialog, so they get the same one-liner instead of two copies of the mapping.
 */
suspend fun TransactionRepository.settle(dialog: PayDialogState): String = addIncomeOrExpense(
    type = TransactionType.EXPENSE,
    amountMinor = dialog.amountMinor,
    accountId = dialog.accountId,
    categoryId = dialog.categoryId,
    dateEpochDay = dialog.dateEpochDay,
    note = dialog.name,
    settlesPaymentId = dialog.paymentId,
    settlesDueEpochDay = dialog.dueDate.toEpochDay(),
    settlesPartial = dialog.isPartial,
)

/**
 * Paying several future occurrences in one go.
 *
 * A recurring payment carries no interest, so "досрочно" here is not the loan's question
 * of what the debt costs - it is simply "how many of these am I settling now?". The money
 * moves once, so it is one expense, dated today and settling the last occurrence covered;
 * everything before that is settled by implication, which is exactly what "paid through"
 * already means.
 */
data class PayAheadDialogState(
    val paymentId: String,
    val name: String,
    val periodAmountMinor: Long,
    val countText: String,
    /** Null when open-ended - then there is no ceiling to check against. */
    val maxCount: Int?,
    /** The occurrence the chosen count reaches, or null while the count is unusable. */
    val coversUpTo: LocalDate?,
    val accountId: String,
    val categoryId: String,
) {
    val count: Int get() = countText.toIntOrNull() ?: 0
    val totalMinor: Long get() = count * periodAmountMinor
    val canSave: Boolean
        get() = count > 0 && coversUpTo != null && (maxCount == null || count <= maxCount)
}

/**
 * Books the confirmed payment as the expense that settles it.
 *
 * An extension here rather than a method on the repository: the dialog is a UI shape and
 * the data layer has no business knowing it exists. Both the list and the loan card raise
 * the same dialog, so they get the same one-liner instead of two copies of the mapping.
 */
suspend fun TransactionRepository.settle(dialog: PayAheadDialogState): String {
    val covers = requireNotNull(dialog.coversUpTo) { "Nothing to cover" }
    return addIncomeOrExpense(
        type = TransactionType.EXPENSE,
        amountMinor = dialog.totalMinor,
        accountId = dialog.accountId,
        categoryId = dialog.categoryId,
        dateEpochDay = LocalDate.now().toEpochDay(),
        note = dialog.name,
        settlesPaymentId = dialog.paymentId,
        settlesDueEpochDay = covers.toEpochDay(),
    )
}

/** The expense just booked, kept only long enough to offer taking it back. */
data class PaidUndo(val transactionId: String)

data class PaymentsUiState(
    val payments: List<PaymentItem> = emptyList(),
    val isLoaded: Boolean = false,
) {
    val isEmpty: Boolean get() = isLoaded && payments.isEmpty()
}

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    /** Re-read on resume: every due date and balance here is relative to the date. */
    private val today = MutableStateFlow(LocalDate.now())

    private val _paidUndo = MutableStateFlow<PaidUndo?>(null)
    val paidUndo: StateFlow<PaidUndo?> = _paidUndo.asStateFlow()

    private val _payDialog = MutableStateFlow<PayDialogState?>(null)
    val payDialog: StateFlow<PayDialogState?> = _payDialog.asStateFlow()

    private val _payAheadDialog = MutableStateFlow<PayAheadDialogState?>(null)
    val payAheadDialog: StateFlow<PayAheadDialogState?> = _payAheadDialog.asStateFlow()

    val uiState: StateFlow<PaymentsUiState> = combine(
        loanRepository.observeAllWithSchedules(),
        recurringPaymentRepository.observeAll(),
        // "Paid" is not stored anywhere - it is whatever the live transactions say, so
        // deleting an expense walks the payment back to unpaid on its own.
        transactionRepository.observePaidThrough(),
        // Part payments live separately: they are real money against the occurrence but
        // they deliberately do not close it, so they cannot come from paid-through.
        transactionRepository.observeSettledAmounts(),
        today,
    ) { loans, recurring, paidThrough, paidAmounts, date ->
        val items = loans.map { it.toItem(paidThrough[it.loan.id], paidAmounts, date) } +
            recurring.map { it.toItem(paidThrough[it.id], paidAmounts, date) }

        PaymentsUiState(
            // Soonest first; whatever has no next date has nothing to chase and sinks.
            payments = items.sortedWith(compareBy(nullsLast()) { it.dueDate }),
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaymentsUiState(),
    )

    fun onRefresh() {
        today.value = LocalDate.now()
    }

    fun onMarkPaidClick(item: PaymentItem) {
        if (!item.isDue) return
        val due = item.dueDate ?: return
        val accountId = accountOf(item) ?: return
        val categoryId = categoryOf(item) ?: return

        _payDialog.value = PayDialogState(
            paymentId = item.id,
            name = item.name,
            dueDate = due,
            accountId = accountId,
            categoryId = categoryId,
            // Prefilled with what is owed, because that is usually right - and editable,
            // because "usually" is not "always".
            amountText = formatAmountForInput(item.dueAmountMinor),
            dateEpochDay = today.value.toEpochDay(),
            dueAmountMinor = item.dueAmountMinor,
        )
    }

    fun onPayAmountChange(text: String) = _payDialog.update {
        it?.copy(amountText = sanitizeAmountInput(text))
    }

    fun onPayDateChange(epochDay: Long) = _payDialog.update { it?.copy(dateEpochDay = epochDay) }

    fun onPayPartialChange(partial: Boolean) =
        _payDialog.update { it?.copy(partialOverride = partial) }

    fun onPayDismiss() {
        _payDialog.value = null
    }

    /**
     * Books the payment as an expense that knows what it settles.
     *
     * One write, not two: the transaction carries both halves of the answer - money left
     * the account on [PayDialogState.dateEpochDay], and it closed the obligation due on
     * [PaymentItem.dueDate]. A payment made late is both, and the amount on it is what was
     * really paid rather than what was expected.
     */
    fun onPayConfirm() {
        val dialog = _payDialog.value ?: return
        if (!dialog.canSave) return
        _payDialog.value = null

        viewModelScope.launch {
            val transactionId = transactionRepository.settle(dialog)
            _paidUndo.value = PaidUndo(transactionId = transactionId)
        }
    }

    /** Deleting the expense is all undo needs to be: the mark was never separate from it. */
    fun onUndoPaid() {
        val undo = _paidUndo.value ?: return
        _paidUndo.value = null
        viewModelScope.launch { transactionRepository.softDelete(undo.transactionId) }
    }

    fun onUndoDismissed() {
        _paidUndo.value = null
    }

    fun onPayAheadClick(item: PaymentItem.Recurring) {
        _payAheadDialog.value = PayAheadDialogState(
            paymentId = item.id,
            name = item.name,
            periodAmountMinor = item.dueAmountMinor,
            countText = "",
            maxCount = item.totalCount?.let { it - item.settledCount },
            coversUpTo = null,
            accountId = item.accountId,
            categoryId = item.categoryId,
        ).let { withCoverage(it) }
    }

    fun onPayAheadCountChange(text: String) = _payAheadDialog.update { dialog ->
        dialog ?: return@update null
        withCoverage(dialog.copy(countText = text.filter(Char::isDigit).take(3)))
    }

    fun onPayAheadDismiss() {
        _payAheadDialog.value = null
    }

    fun onPayAheadConfirm() {
        val dialog = _payAheadDialog.value ?: return
        if (!dialog.canSave) return
        _payAheadDialog.value = null

        viewModelScope.launch {
            val transactionId = transactionRepository.settle(dialog)
            _paidUndo.value = PaidUndo(transactionId = transactionId)
        }
    }

    /**
     * Works out which occurrence the chosen count reaches: the next unpaid one plus
     * count - 1 more, so "3 вперёд" means the next three.
     *
     * Counted from the payment's own start rather than stepped from the next due date -
     * stepping would re-anchor the series and let a payment on the 31st drift down the
     * calendar, which is the trap [nthRecurrence] exists to avoid.
     */
    private fun withCoverage(dialog: PayAheadDialogState): PayAheadDialogState {
        val payment = uiState.value.payments
            .filterIsInstance<PaymentItem.Recurring>()
            .firstOrNull { it.id == dialog.paymentId }
        val next = payment?.dueDate
        if (next == null || dialog.count <= 0) return dialog.copy(coversUpTo = null)

        val start = LocalDate.ofEpochDay(payment.startDateEpochDay)
        // settledRecurrences counts occurrences up to and including a date, so the next
        // one's index is that count minus one.
        val nextIndex = settledRecurrences(start, payment.period, next) - 1L
        val covers = nthRecurrence(start, payment.period, nextIndex + dialog.count - 1)
        return dialog.copy(coversUpTo = covers)
    }

    private fun accountOf(item: PaymentItem): String? = when (item) {
        is PaymentItem.Loan -> item.accountId
        is PaymentItem.Recurring -> item.accountId
    }

    private fun categoryOf(item: PaymentItem): String? = when (item) {
        is PaymentItem.Loan -> item.categoryId
        is PaymentItem.Recurring -> item.categoryId
    }
}

private fun LoanWithSchedule.toItem(
    paidThroughEpochDay: Long?,
    paidAmounts: Map<Pair<String, Long>, Long>,
    today: LocalDate,
): PaymentItem.Loan {
    val paidThrough = paidThroughEpochDay?.let(LocalDate::ofEpochDay)
    val enginePrepayments = prepayments.map { it.toPrepayment() }
    val next = nextLoanDue(schedule, paidThrough, today)

    return PaymentItem.Loan(
        id = loan.id,
        name = loan.name,
        dueDate = next?.date,
        dueAmountMinor = next?.paymentMinor ?: 0,
        isOverdue = next != null && next.date.isBefore(today),
        isDue = next != null && !next.date.isAfter(today),
        isSettled = paidThrough != null && next != null && next.date.isAfter(today),
        type = loan.type,
        principalMinor = loan.principalMinor,
        balanceMinor = balanceOn(loan.toEngineLoan(), schedule, enginePrepayments, today),
        overpaymentMinor = summary.overpaymentMinor,
        accountId = loan.accountId,
        categoryId = loan.categoryId,
        paidTowardsDueMinor = next?.let { paidAmounts[loan.id to it.date.toEpochDay()] } ?: 0,
        reminderDays = loan.reminderDaysList,
    )
}

private fun RecurringPaymentEntity.toItem(
    paidThroughEpochDay: Long?,
    paidAmounts: Map<Pair<String, Long>, Long>,
    today: LocalDate,
): PaymentItem.Recurring {
    val start = LocalDate.ofEpochDay(startDateEpochDay)
    val end = endDateEpochDay?.let(LocalDate::ofEpochDay)
    val paidThrough = paidThroughEpochDay?.let(LocalDate::ofEpochDay)
    val due = nextDueRecurrence(
        start = start,
        period = period,
        end = end,
        paidThrough = paidThrough,
        today = today,
    )

    return PaymentItem.Recurring(
        id = id,
        name = name,
        dueDate = due,
        dueAmountMinor = amountMinor,
        isOverdue = due != null && due.isBefore(today),
        isDue = due != null && !due.isAfter(today),
        isSettled = paidThroughEpochDay != null && due != null && due.isAfter(today),
        period = period,
        reminderEnabled = reminderEnabled,
        accountId = accountId,
        categoryId = categoryId,
        startDateEpochDay = startDateEpochDay,
        totalCount = totalRecurrences(start, period, end),
        settledCount = settledRecurrences(start, period, paidThrough),
        paidTowardsDueMinor = due?.let { paidAmounts[id to it.toEpochDay()] } ?: 0,
    )
}
