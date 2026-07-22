package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
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
    /** ЖКХ this month: fixed and normative services plus metered readings entered in it. */
    val utilitiesMinor: Long = 0,
    /** Money that actually left the accounts towards this month's obligations. */
    val paidMinor: Long = 0,
    /**
     * Scheduled amount of the occurrences still open.
     *
     * Stored rather than derived as total minus paid: an occurrence settled for less than
     * its nominal is closed, so subtracting would keep insisting on money nobody owes.
     */
    val remainingMinor: Long = 0,
) {
    val totalMinor: Long get() = loansMinor + recurringMinor + utilitiesMinor
}

/**
 * Reads the month's obligations. Lives in the data layer so the widget can use it without
 * the Overview ViewModel, the same way [NextPaymentRepository] does.
 */
class ObligationsRepository @Inject constructor(
    private val loanRepository: LoanRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val meterRepository: MeterRepository,
    private val transactionRepository: TransactionRepository,
) {
    fun observeForMonth(month: LongRange): Flow<MonthlyObligations> = combine(
        loanRepository.observeAllWithSchedules(),
        recurringPaymentRepository.observeAll(),
        // Meters and their readings arrive as one pair so the five-arg combine still fits.
        combine(
            meterRepository.observeAll(),
            meterRepository.observeAllReadings(),
        ) { meters, readings -> meters to readings },
        transactionRepository.observePaidThrough(),
        transactionRepository.observeSettledAmounts(),
    ) { loans, recurring, metersAndReadings, paidThrough, paidAmounts ->
        val (meters, readings) = metersAndReadings
        monthlyObligations(loans, recurring, paidThrough, paidAmounts, month, meters, readings)
    }
}

/**
 * What an occurrence actually cost this month.
 *
 * Once it is closed, the money that settled it *is* the obligation: agreeing 5 000 ₽ for a
 * 10 000 ₽ bill means the month's burden was 5 000 ₽, not 10 000 ₽ with half of it
 * forgiven and still displayed. While it is open the scheduled amount stands, because that
 * is what is still going to be asked for.
 *
 * A closed occurrence with nothing booked against it fell to a payment made ahead, which
 * is attached to the last occurrence it covered; the scheduled amount is the honest figure
 * for the ones it settled by implication.
 */
private fun effectiveAmount(scheduledMinor: Long, paidMinor: Long, closed: Boolean): Long =
    if (closed && paidMinor > 0) paidMinor else scheduledMinor

/**
 * Sums the obligations due within [month] (inclusive epoch-day bounds).
 *
 * Two different questions, deliberately answered from two different places:
 *
 *  - **How much money went out** ([MonthlyObligations.paidMinor]) is counted from the
 *    transactions themselves. It used to credit the *scheduled* amount whenever an
 *    occurrence was marked closed, so settling a 10 000 ₽ obligation with 3 800 ₽ - which
 *    the dialog allows, and banks do accept - reported 10 000 ₽ paid while only 3 800 ₽
 *    had left the account.
 *  - **How much is still owed** ([MonthlyObligations.remainingMinor]) is the scheduled
 *    amount of the occurrences that are still open. It cannot be total minus paid: an
 *    occurrence settled for less than its nominal is closed, and nothing more is due on it.
 *
 * The total follows the same rule through [effectiveAmount], so a settled month adds up:
 * pay 5 000 ₽ against a 10 000 ₽ bill and call it settled, and the month reads 5 000 ₽ of
 * 5 000 ₽ rather than 5 000 ₽ of 10 000 ₽ with nothing outstanding.
 */
fun monthlyObligations(
    loans: List<LoanWithSchedule>,
    recurring: List<RecurringPaymentEntity>,
    paidThrough: Map<String, Long>,
    paidAmounts: Map<Pair<String, Long>, Long>,
    month: LongRange,
    // Defaulted and last so the many existing loan/recurring call sites stay valid.
    meters: List<MeterEntity> = emptyList(),
    readings: List<MeterReadingEntity> = emptyList(),
): MonthlyObligations {
    var loansMinor = 0L
    var recurringMinor = 0L
    var utilitiesMinor = 0L
    var paidMinor = 0L
    var remainingMinor = 0L

    loans.forEach { lws ->
        val paidThroughDay = paidThrough[lws.loan.id]
        lws.schedule.forEach { entry ->
            val day = entry.date.toEpochDay()
            if (day in month) {
                val paid = paidAmounts[lws.loan.id to day] ?: 0
                val closed = paidThroughDay != null && day <= paidThroughDay
                loansMinor += effectiveAmount(entry.paymentMinor, paid, closed)
                paidMinor += paid
                if (!closed) remainingMinor += entry.paymentMinor
            }
        }
    }

    recurring.forEach { payment ->
        val paidThroughDay = paidThrough[payment.id]
        recurrenceDaysIn(payment, month).forEach { day ->
            val paid = paidAmounts[payment.id to day] ?: 0
            val closed = paidThroughDay != null && day <= paidThroughDay
            recurringMinor += effectiveAmount(payment.amountMinor, paid, closed)
            paidMinor += paid
            if (!closed) remainingMinor += payment.amountMinor
        }
    }

    // The 1st of the shown month, used as the settle key for a monthly (norm/fixed) service -
    // the same key UtilitiesViewModel pays it under.
    val firstOfMonth = LocalDate.ofEpochDay(month.first).withDayOfMonth(1).toEpochDay()
    val latestReadingByMeter = readings.groupBy { it.meterId }
    meters.forEach { meter ->
        val charge = utilityChargeInMonth(meter, latestReadingByMeter[meter.id], month, firstOfMonth)
            ?: return@forEach
        val paid = paidAmounts[charge.settlesId to charge.dueEpochDay] ?: 0
        val closed = when (meter.billing) {
            // A metered reading is settled outright, keyed by the reading itself.
            BillingKind.METERED -> paidThrough.containsKey(charge.settlesId)
            // A monthly service is paid for a month; closed once that month is settled.
            else -> paidThrough[meter.id]?.let { it in month } == true
        }
        utilitiesMinor += effectiveAmount(charge.scheduledMinor, paid, closed)
        paidMinor += paid
        if (!closed) remainingMinor += charge.scheduledMinor
    }

    return MonthlyObligations(
        loansMinor = loansMinor,
        recurringMinor = recurringMinor,
        utilitiesMinor = utilitiesMinor,
        paidMinor = paidMinor,
        remainingMinor = remainingMinor,
    )
}

/** What one service owes in [month], or null when there is nothing to pay for it yet. */
private class UtilityCharge(
    val settlesId: String,
    val dueEpochDay: Long,
    val scheduledMinor: Long,
)

private fun utilityChargeInMonth(
    meter: MeterEntity,
    meterReadings: List<MeterReadingEntity>?,
    month: LongRange,
    firstOfMonth: Long,
): UtilityCharge? = when (meter.billing) {
    // Norm and fixed cost the same every month, keyed by the meter and the 1st.
    BillingKind.NORM, BillingKind.FIXED ->
        meter.monthlyChargeMinor()
            ?.takeIf { it > 0 }
            ?.let { UtilityCharge(meter.id, firstOfMonth, it) }
    // Metered: the latest reading counts only if it was taken in this month - a reading
    // from an earlier month belongs to that month's bill, not this one.
    BillingKind.METERED -> meterReadings
        ?.maxByOrNull { it.dateEpochDay }
        ?.takeIf { it.dateEpochDay in month && it.amountMinor > 0 }
        ?.let { UtilityCharge(it.id, it.dateEpochDay, it.amountMinor) }
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
