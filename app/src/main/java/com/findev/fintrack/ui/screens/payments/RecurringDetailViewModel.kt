package com.findev.fintrack.ui.screens.payments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.RecurringPaymentRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.dao.SettlementRow
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import com.findev.fintrack.data.settledRecurrences
import com.findev.fintrack.data.totalRecurrences
import com.findev.fintrack.data.nextDueRecurrence
import com.findev.fintrack.ui.navigation.RECURRING_DETAIL_ARG_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class RecurringDetailUiState(
    val payment: RecurringPaymentEntity? = null,
    val dueDate: LocalDate? = null,
    val isOverdue: Boolean = false,
    /** Null when the payment is open-ended: a subscription is not counting towards anything. */
    val totalCount: Int? = null,
    val settledCount: Int = 0,
    /** Every payment ever booked against it, newest first. */
    val settlements: List<SettlementRow> = emptyList(),
    val isLoaded: Boolean = false,
) {
    val paidTotalMinor: Long get() = settlements.sumOf { it.amountMinor }

    val remainingCount: Int get() = ((totalCount ?: 0) - settledCount).coerceAtLeast(0)

    /** What is left over the whole term, at today's amount. Null while it is open-ended. */
    val remainingMinor: Long?
        get() = payment?.let { p -> totalCount?.let { remainingCount * p.amountMinor } }

    val progress: Float
        get() {
            val total = totalCount ?: return 0f
            if (total == 0) return 1f
            return settledCount.toFloat() / total.toFloat()
        }
}

/**
 * The recurring payment's own screen.
 *
 * It began as a bottom sheet over the list, which stopped working as soon as there were
 * more than a handful of payments: a sheet is a glance, and this is the screen where the
 * whole history of an obligation is read. Loans already had one, so this mirrors it.
 */
@HiltViewModel
class RecurringDetailViewModel @Inject constructor(
    recurringPaymentRepository: RecurringPaymentRepository,
    transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val paymentId: String = requireNotNull(savedStateHandle[RECURRING_DETAIL_ARG_ID])

    // Re-read when the screen returns to the foreground: the due date moves with the date.
    private val today = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<RecurringDetailUiState> = combine(
        recurringPaymentRepository.observeAll().map { all -> all.firstOrNull { it.id == paymentId } },
        transactionRepository.observePaidThrough(),
        transactionRepository.observeSettlements(paymentId),
        today,
    ) { payment, paidThrough, settlements, date ->
        if (payment == null) return@combine RecurringDetailUiState(isLoaded = true)

        val start = LocalDate.ofEpochDay(payment.startDateEpochDay)
        val end = payment.endDateEpochDay?.let(LocalDate::ofEpochDay)
        val paidThroughDate = paidThrough[paymentId]?.let(LocalDate::ofEpochDay)
        val due = nextDueRecurrence(
            start = start,
            period = payment.period,
            end = end,
            paidThrough = paidThroughDate,
            today = date,
        )

        RecurringDetailUiState(
            payment = payment,
            dueDate = due,
            isOverdue = due != null && due.isBefore(date),
            totalCount = totalRecurrences(start, payment.period, end),
            settledCount = settledRecurrences(start, payment.period, paidThroughDate),
            settlements = settlements,
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringDetailUiState(),
    )

    fun onRefresh() {
        today.value = LocalDate.now()
    }
}
