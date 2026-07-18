package com.findev.fintrack.loanengine

import java.time.LocalDate

/**
 * One line of the payment schedule, as a bank would print it.
 *
 * [paymentMinor] always equals [interestMinor] + [principalMinor] + [feeMinor]: the
 * split is what the borrower is really paying for, and it must add up exactly - hence
 * kopecks as Long and HALF_UP rounding at every step rather than at the end.
 *
 * [prepaymentMinor] is money paid on top of this period's payment; it lowers
 * [balanceAfterMinor] but is not part of [paymentMinor].
 */
data class ScheduleEntry(
    /** 1-based, as in a bank statement. */
    val number: Int,
    val date: LocalDate,
    val paymentMinor: Long,
    val interestMinor: Long,
    val principalMinor: Long,
    /** Servicing fee for this period; neither interest nor principal, but still due. */
    val feeMinor: Long,
    val prepaymentMinor: Long,
    /** Debt remaining once this payment and any prepayment are applied. */
    val balanceAfterMinor: Long,
) {
    init {
        require(paymentMinor == interestMinor + principalMinor + feeMinor) {
            "Payment $paymentMinor must split into interest $interestMinor + " +
                "principal $principalMinor + fee $feeMinor"
        }
        require(balanceAfterMinor >= 0) { "Balance cannot go negative, was $balanceAfterMinor" }
    }
}
