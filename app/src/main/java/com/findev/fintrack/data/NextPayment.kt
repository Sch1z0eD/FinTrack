package com.findev.fintrack.data

import com.findev.fintrack.loanengine.ScheduleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * The soonest obligation still to pay - a loan instalment or a recurring payment, whichever
 * comes first. This is what the "nearest payment" widget shows.
 */
data class NextPayment(
    val name: String,
    val dueEpochDay: Long,
    val amountMinor: Long,
    /** The due date is already behind us and nothing has been marked paid for it. */
    val isOverdue: Boolean,
)

/**
 * The next unpaid instalment of a loan, by the same rule the Payments list uses: with nothing
 * marked paid the calendar decides (first entry not before today, so a loan entered today is
 * not retroactively overdue); once something is marked, it is the first entry after that.
 */
fun nextLoanDue(
    schedule: List<ScheduleEntry>,
    paidThrough: LocalDate?,
    today: LocalDate,
): ScheduleEntry? = if (paidThrough == null) {
    schedule.firstOrNull { !it.date.isBefore(today) }
} else {
    schedule.firstOrNull { it.date.isAfter(paidThrough) }
}

/**
 * Reads the single soonest payment across loans and recurring payments. Lives in the data
 * layer so the widget can use it without the Payments ViewModel (which builds far richer
 * per-row UI state it does not need).
 */
class NextPaymentRepository @Inject constructor(
    private val loanRepository: LoanRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
) {
    /** The soonest [limit] obligations, earliest first. Overdue ones sort to the front. */
    fun observeUpcoming(today: LocalDate, limit: Int): Flow<List<NextPayment>> =
        observeAll(today).map { all -> all.sortedBy { it.dueEpochDay }.take(limit) }

    fun observeNext(today: LocalDate): Flow<NextPayment?> =
        observeAll(today).map { all -> all.minByOrNull { it.dueEpochDay } }

    private fun observeAll(today: LocalDate): Flow<List<NextPayment>> = combine(
        loanRepository.observeAllWithSchedules(),
        recurringPaymentRepository.observeAll(),
        transactionRepository.observePaidThrough(),
    ) { loans, recurring, paidThrough ->
        val fromLoans = loans.mapNotNull { lws ->
            val paid = paidThrough[lws.loan.id]?.let(LocalDate::ofEpochDay)
            nextLoanDue(lws.schedule, paid, today)?.let { entry ->
                NextPayment(
                    name = lws.loan.name,
                    dueEpochDay = entry.date.toEpochDay(),
                    amountMinor = entry.paymentMinor,
                    isOverdue = entry.date.isBefore(today),
                )
            }
        }
        val fromRecurring = recurring.mapNotNull { payment ->
            val paid = paidThrough[payment.id]?.let(LocalDate::ofEpochDay)
            val due = nextDueRecurrence(
                start = LocalDate.ofEpochDay(payment.startDateEpochDay),
                period = payment.period,
                end = payment.endDateEpochDay?.let(LocalDate::ofEpochDay),
                paidThrough = paid,
                today = today,
            )
            due?.let {
                NextPayment(
                    name = payment.name,
                    dueEpochDay = it.toEpochDay(),
                    amountMinor = payment.amountMinor,
                    isOverdue = it.isBefore(today),
                )
            }
        }
        fromLoans + fromRecurring
    }
}
