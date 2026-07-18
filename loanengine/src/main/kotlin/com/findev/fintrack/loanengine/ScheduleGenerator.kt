package com.findev.fintrack.loanengine

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Builds the payment schedule from the loan and its rate history.
 *
 * Pure by design: the schedule is never stored, it is recomputed from the loan
 * (plus, later, its prepayments). Same inputs, same kopecks.
 *
 * [Loan.upfrontFeeMinor] deliberately never appears here: it is paid at origination,
 * not on a payment date. It belongs to the overpayment, not to the schedule.
 */
fun generateSchedule(
    loan: Loan,
    rateChanges: List<RateChange> = emptyList(),
    prepayments: List<Prepayment> = emptyList(),
): List<ScheduleEntry> {
    require(loan.type != LoanType.INSTALLMENT || rateChanges.isEmpty()) {
        "An instalment plan carries no interest, so it cannot have rate changes"
    }
    require(prepayments.all { it.date > loan.startDate }) {
        "A prepayment cannot predate the loan itself"
    }
    val sortedChanges = rateChanges.sortedBy { it.effectiveFrom }
    val sortedPrepayments = prepayments.sortedBy { it.date }

    return when (loan.type) {
        LoanType.ANNUITY -> annuitySchedule(loan, sortedChanges, sortedPrepayments)
        // An instalment plan is equal principal slices at 0% - the lender's cut is the fee.
        LoanType.DIFFERENTIATED, LoanType.INSTALLMENT ->
            equalPrincipalSchedule(loan, sortedChanges, sortedPrepayments)
        LoanType.INTEREST_ONLY -> interestOnlySchedule(loan, sortedChanges, sortedPrepayments)
    }
}

/**
 * Interest every period, the principal all at the end.
 *
 * There is no payment formula here at all: the payment *is* the interest the period
 * earned, so it rises and falls with the length of the month. Nothing amortises, which
 * is the whole point of the product - the balance the interest is charged on stays at
 * the full principal for the entire term.
 *
 * A prepayment is the one thing that does move the balance, and it needs no mode: with
 * no scheduled principal to re-slice and no fixed payment to re-derive, REDUCE_TERM and
 * REDUCE_PAYMENT do the same thing - less debt, so less interest, and a smaller final
 * payment.
 */
private fun interestOnlySchedule(
    loan: Loan,
    sortedChanges: List<RateChange>,
    sortedPrepayments: List<Prepayment>,
): List<ScheduleEntry> {
    val entries = ArrayList<ScheduleEntry>(loan.termMonths)

    var balance = loan.principalMinor
    var periodStart = loan.startDate

    for (number in 1..loan.termMonths) {
        val date = paymentDate(loan.startDate, loan.paymentDay, number)
        val insidePeriod = sortedPrepayments.filter { it.date > periodStart && it.date < date }
        val onPaymentDay = sortedPrepayments.filter { it.date == date }

        val accrual = accruePeriod(balance, loan, sortedChanges, insidePeriod, periodStart, date)
        balance = accrual.balanceMinor

        // The last payment carries whatever principal is still owed - everything, unless
        // prepayments already took some of it.
        val principalPart = if (number == loan.termMonths) balance else 0L
        balance -= principalPart

        val applied = applyPrepayments(onPaymentDay, balance)
        balance -= applied.amountMinor

        entries += ScheduleEntry(
            number = number,
            date = date,
            paymentMinor = principalPart + accrual.interestMinor + loan.monthlyFeeMinor,
            interestMinor = accrual.interestMinor,
            principalMinor = principalPart,
            feeMinor = loan.monthlyFeeMinor,
            prepaymentMinor = accrual.prepaidMinor + applied.amountMinor,
            balanceAfterMinor = balance,
        )

        periodStart = date
        if (balance == 0L) break
    }

    return entries
}

/** What one payment period did to the debt before its regular payment lands. */
private class Accrual(
    val interestMinor: Long,
    val balanceMinor: Long,
    val prepaidMinor: Long,
)

/**
 * Interest for a single period, walking the prepayments that land strictly inside it.
 *
 * A prepayment changes the balance mid-period, so the days before it accrue on the old
 * balance and the days after on the reduced one. Each slice still goes through the rate
 * and year splitting, and the whole period is rounded exactly once at the end -
 * anything else would drift kopecks.
 *
 * Prepayments dated on the payment day itself are not handled here: the bank takes the
 * scheduled payment first and only then puts the surplus against the principal.
 */
private fun accruePeriod(
    balanceMinor: Long,
    loan: Loan,
    sortedChanges: List<RateChange>,
    periodPrepayments: List<Prepayment>,
    from: LocalDate,
    to: LocalDate,
): Accrual {
    var accrued = BigDecimal.ZERO
    var balance = balanceMinor
    var prepaid = 0L
    var cursor = from

    for (prepayment in periodPrepayments) {
        accrued = accrued.add(
            exactInterestWithRateChanges(balance, loan.annualRateBp, sortedChanges, cursor, prepayment.date),
        )
        // Never overpay: the debt is the ceiling.
        val applied = minOf(prepayment.amountMinor, balance)
        balance -= applied
        prepaid += applied
        cursor = prepayment.date
    }

    accrued = accrued.add(
        exactInterestWithRateChanges(balance, loan.annualRateBp, sortedChanges, cursor, to),
    )
    return Accrual(accrued.toKopecksHalfUp(), balance, prepaid)
}

/**
 * Equal slices of principal; the payment falls as the balance shrinks.
 *
 * Note it does not fall strictly month to month: interest runs on actual days, so a
 * 31-day month after a 28-day one can cost more even on a smaller balance.
 *
 * A rate change only moves the interest here - the principal slices are fixed by the
 * term, so there is no schedule to recompute.
 */
private fun equalPrincipalSchedule(
    loan: Loan,
    sortedChanges: List<RateChange>,
    sortedPrepayments: List<Prepayment>,
): List<ScheduleEntry> {
    val entries = ArrayList<ScheduleEntry>(loan.termMonths)

    var balance = loan.principalMinor
    var periodStart = loan.startDate
    var slice = evenSlice(loan.principalMinor, loan.termMonths)

    for (number in 1..loan.termMonths) {
        val date = paymentDate(loan.startDate, loan.paymentDay, number)
        val insidePeriod = sortedPrepayments.filter { it.date > periodStart && it.date < date }
        val onPaymentDay = sortedPrepayments.filter { it.date == date }

        val accrual = accruePeriod(balance, loan, sortedChanges, insidePeriod, periodStart, date)
        balance = accrual.balanceMinor

        // The last payment takes whatever is left, so the rounded slices always add
        // back up to the principal exactly.
        val isLast = number == loan.termMonths
        var principalPart = if (balance == 0L) 0L else if (isLast) balance else slice
        if (principalPart > balance) principalPart = balance
        balance -= principalPart

        val applied = applyPrepayments(onPaymentDay, balance)
        balance -= applied.amountMinor

        entries += ScheduleEntry(
            number = number,
            date = date,
            paymentMinor = principalPart + accrual.interestMinor + loan.monthlyFeeMinor,
            interestMinor = accrual.interestMinor,
            principalMinor = principalPart,
            feeMinor = loan.monthlyFeeMinor,
            prepaymentMinor = accrual.prepaidMinor + applied.amountMinor,
            balanceAfterMinor = balance,
        )

        periodStart = date
        if (balance == 0L) break

        // REDUCE_PAYMENT keeps the end date, so the remaining debt is re-sliced over the
        // payments that are left. REDUCE_TERM changes nothing here - the slice stays and
        // the balance simply runs out early.
        if ((insidePeriod + onPaymentDay).any { it.mode == PrepaymentMode.REDUCE_PAYMENT }) {
            slice = evenSlice(balance, loan.termMonths - number)
        }
    }

    return entries
}

private fun evenSlice(balanceMinor: Long, payments: Int): Long =
    BigDecimal(balanceMinor).divide(BigDecimal(payments), MATH).toKopecksHalfUp()

private class Applied(val amountMinor: Long)

/** Puts prepayments against the debt, never paying more than is owed. */
private fun applyPrepayments(prepayments: List<Prepayment>, balanceMinor: Long): Applied {
    var remaining = balanceMinor
    var total = 0L
    for (prepayment in prepayments) {
        val applied = minOf(prepayment.amountMinor, remaining)
        remaining -= applied
        total += applied
    }
    return Applied(total)
}

private fun annuitySchedule(
    loan: Loan,
    sortedChanges: List<RateChange>,
    sortedPrepayments: List<Prepayment>,
): List<ScheduleEntry> {
    val entries = ArrayList<ScheduleEntry>(loan.termMonths)

    var balance = loan.principalMinor
    var periodStart = loan.startDate
    // Size the opening payment from the rate actually in force on the start date, not
    // from the loan's written rate: a change dated on or before the start supersedes it.
    var rateInPayment = rateOn(loan.annualRateBp, sortedChanges, loan.startDate)
    var payment = annuityPaymentMinor(loan.principalMinor, rateInPayment, loan.termMonths)

    for (number in 1..loan.termMonths) {
        val date = paymentDate(loan.startDate, loan.paymentDay, number)

        // A change that took effect by the start of this period resizes the rest of the
        // schedule: the new payment is derived from what is still owed over what is left
        // of the term. A change landing mid-period only splits this period's interest;
        // the resized payment starts from the next one, as banks do it.
        val rateNow = rateOn(loan.annualRateBp, sortedChanges, periodStart)
        if (rateNow != rateInPayment) {
            payment = annuityPaymentMinor(balance, rateNow, loan.termMonths - number + 1)
            rateInPayment = rateNow
        }

        val insidePeriod = sortedPrepayments.filter { it.date > periodStart && it.date < date }
        val onPaymentDay = sortedPrepayments.filter { it.date == date }
        val accrual = accruePeriod(balance, loan, sortedChanges, insidePeriod, periodStart, date)
        val interest = accrual.interestMinor
        balance = accrual.balanceMinor

        // The last payment clears whatever is left: the fixed payment size is derived
        // from /12 while interest runs on actual days, so the two never land exactly.
        val isLast = number == loan.termMonths
        var principalPart: Long
        if (balance == 0L) {
            // A prepayment already cleared the debt; only the interest it earned is due.
            principalPart = 0
        } else if (isLast) {
            principalPart = balance
        } else {
            principalPart = payment - interest
            require(principalPart > 0) {
                "Payment $payment does not cover interest $interest at payment $number: " +
                    "the balance would grow and the loan would never amortise"
            }
            if (principalPart > balance) principalPart = balance
        }

        balance -= principalPart

        val applied = applyPrepayments(onPaymentDay, balance)
        balance -= applied.amountMinor

        entries += ScheduleEntry(
            number = number,
            date = date,
            // The fee rides on top of the annuity: the equal-payment formula sizes
            // principal + interest, servicing is charged separately.
            paymentMinor = principalPart + interest + loan.monthlyFeeMinor,
            interestMinor = interest,
            principalMinor = principalPart,
            feeMinor = loan.monthlyFeeMinor,
            prepaymentMinor = accrual.prepaidMinor + applied.amountMinor,
            balanceAfterMinor = balance,
        )

        periodStart = date
        if (balance == 0L) break

        // REDUCE_PAYMENT keeps the end date, so the payment is re-derived from what is
        // left over the payments that remain. REDUCE_TERM keeps the payment and simply
        // runs out of balance early - nothing to recompute.
        if ((insidePeriod + onPaymentDay).any { it.mode == PrepaymentMode.REDUCE_PAYMENT }) {
            payment = annuityPaymentMinor(balance, rateInPayment, loan.termMonths - number)
        }
    }

    return entries
}
