package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

/**
 * What the month costs before a single discretionary rouble is spent: loan instalments plus
 * recurring payments falling due inside it.
 *
 * Split by kind because the two behave differently - a loan instalment is fixed by a contract
 * and a subscription can be cancelled - and split by paid/unpaid because "1 200 ₽ this month"
 * means something very different on the 1st and on the 28th.
 */
data class MonthlyObligations(
    val loansMinor: Long = 0,
    val recurringMinor: Long = 0,
    val paidMinor: Long = 0,
) {
    val totalMinor: Long get() = loansMinor + recurringMinor

    /** Never negative: paying more than the nominal (a bigger utility bill) is not a credit. */
    val remainingMinor: Long get() = (totalMinor - paidMinor).coerceAtLeast(0)
}

/**
 * Reads the month's obligations. Lives in the data layer so the widget can use it without
 * the Overview ViewModel, the same way [NextPaymentRepository] does.
 */
class ObligationsRepository @Inject constructor(
    private val loanRepository: LoanRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
) {
    fun observeForMonth(month: LongRange): Flow<MonthlyObligations> = combine(
        loanRepository.observeAllWithSchedules(),
        recurringPaymentRepository.observeAll(),
        transactionRepository.observePaidThrough(),
    ) { loans, recurring, paidThrough ->
        monthlyObligations(loans, recurring, paidThrough, month)
    }
}

/**
 * Sums the obligations due within [month] (inclusive epoch-day bounds).
 *
 * An occurrence counts as paid when its due date is not after the payment's paid-through
 * mark, which is the same rule the Payments screen uses - so the two can never disagree.
 */
fun monthlyObligations(
    loans: List<LoanWithSchedule>,
    recurring: List<RecurringPaymentEntity>,
    paidThrough: Map<String, Long>,
    month: LongRange,
): MonthlyObligations {
    var loansMinor = 0L
    var recurringMinor = 0L
    var paidMinor = 0L

    loans.forEach { lws ->
        val paidThroughDay = paidThrough[lws.loan.id]
        lws.schedule.forEach { entry ->
            val day = entry.date.toEpochDay()
            if (day in month) {
                loansMinor += entry.paymentMinor
                if (paidThroughDay != null && day <= paidThroughDay) {
                    paidMinor += entry.paymentMinor
                }
            }
        }
    }

    recurring.forEach { payment ->
        val paidThroughDay = paidThrough[payment.id]
        recurrenceDaysIn(payment, month).forEach { day ->
            recurringMinor += payment.amountMinor
            if (paidThroughDay != null && day <= paidThroughDay) {
                paidMinor += payment.amountMinor
            }
        }
    }

    return MonthlyObligations(
        loansMinor = loansMinor,
        recurringMinor = recurringMinor,
        paidMinor = paidMinor,
    )
}

/**
 * Every due date of [payment] inside [month]. Walks occurrence by occurrence rather than
 * dividing the range by the period: a daily payment has ~30 of them in a month, and a yearly
 * one usually none.
 */
private fun recurrenceDaysIn(payment: RecurringPaymentEntity, month: LongRange): List<Long> {
    val start = LocalDate.ofEpochDay(payment.startDateEpochDay)
    val end = payment.endDateEpochDay?.let(LocalDate::ofEpochDay)
    val monthStart = LocalDate.ofEpochDay(month.first)
    val monthEnd = LocalDate.ofEpochDay(month.last)

    val days = mutableListOf<Long>()
    var cursor = nextRecurrenceOnOrAfter(start, payment.period, end, monthStart)
    while (cursor != null && !cursor.isAfter(monthEnd)) {
        days += cursor.toEpochDay()
        cursor = nextRecurrenceOnOrAfter(start, payment.period, end, cursor.plusDays(1))
    }
    return days
}
