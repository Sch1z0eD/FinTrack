package com.findev.fintrack.loanengine

import java.time.LocalDate

/**
 * What the loan costs in total, once its schedule is known.
 *
 * [overpaymentMinor] is everything paid beyond the borrowed money: interest plus all
 * fees, including the up-front one that never appears in the schedule. That is the
 * number a borrower actually cares about.
 */
data class LoanSummary(
    val principalMinor: Long,
    val totalInterestMinor: Long,
    /** Monthly servicing plus the one-off fee charged at origination. */
    val totalFeesMinor: Long,
    /** Principal + interest + fees: every kopeck that leaves the borrower's pocket. */
    val totalPaidMinor: Long,
    val overpaymentMinor: Long,
    /** Date of the final payment - earlier than planned if prepayments shortened the term. */
    val closingDate: LocalDate,
    val paymentCount: Int,
) {
    init {
        require(overpaymentMinor == totalInterestMinor + totalFeesMinor) {
            "Overpayment $overpaymentMinor must be interest $totalInterestMinor + fees $totalFeesMinor"
        }
        require(totalPaidMinor == principalMinor + overpaymentMinor) {
            "Total paid $totalPaidMinor must be principal $principalMinor + overpayment $overpaymentMinor"
        }
    }
}

/**
 * Totals for [schedule], which must have been produced from [loan].
 *
 * The schedule repays the principal through its principal parts and its prepayments,
 * so the two together are exactly the borrowed amount - that is what makes the
 * "total paid = principal + overpayment" check below meaningful rather than circular.
 */
fun summarize(loan: Loan, schedule: List<ScheduleEntry>): LoanSummary {
    require(schedule.isNotEmpty()) { "A loan always has at least one payment" }

    val interest = schedule.sumOf { it.interestMinor }
    // The up-front fee is real money out even though no payment row carries it.
    val fees = schedule.sumOf { it.feeMinor } + loan.upfrontFeeMinor
    val repaid = schedule.sumOf { it.principalMinor } + schedule.sumOf { it.prepaymentMinor }

    return LoanSummary(
        principalMinor = repaid,
        totalInterestMinor = interest,
        totalFeesMinor = fees,
        totalPaidMinor = repaid + interest + fees,
        overpaymentMinor = interest + fees,
        closingDate = schedule.last().date,
        paymentCount = schedule.size,
    )
}

/**
 * Debt outstanding on [date].
 *
 * The schedule only reports a balance on payment dates, so between them it is stale by
 * exactly the prepayments made since the last one - and those are the moments the debt
 * actually moved. Money paid yesterday is gone from the debt today, whatever the next
 * payment date says, so anything landing after the last payment and on or before [date]
 * is taken off here.
 *
 * [prepayments] is the same list the schedule was generated from; passing a different one
 * answers a different question than the schedule does.
 */
fun balanceOn(
    loan: Loan,
    schedule: List<ScheduleEntry>,
    prepayments: List<Prepayment>,
    date: LocalDate,
): Long {
    val lastPayment = schedule.lastOrNull { !it.date.isAfter(date) }
    val balance = lastPayment?.balanceAfterMinor ?: loan.principalMinor

    // Prepayments dated on the last payment day are already inside its balance: the
    // schedule applies them right after the scheduled payment.
    val since = lastPayment?.date ?: loan.startDate
    val paidSince = prepayments
        .filter { it.date > since && !it.date.isAfter(date) }
        .sumOf { it.amountMinor }

    return (balance - paidSince).coerceAtLeast(0)
}
