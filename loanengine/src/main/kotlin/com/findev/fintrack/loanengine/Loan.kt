package com.findev.fintrack.loanengine

import java.time.LocalDate

enum class LoanType {
    /** Equal total payments; the interest/principal split shifts over time. */
    ANNUITY,

    /** Equal principal parts; the total payment falls as the balance shrinks. */
    DIFFERENTIATED,

    /** Retail instalment plan: no interest, the cost sits in fees. */
    INSTALLMENT,

    /**
     * Interest every month, the whole principal in the final payment.
     *
     * Common in microfinance: "Платеж включает проценты, начисленные за прошедший
     * отчетный период, за исключением последнего платежа, который включает 100%
     * ссудной задолженности". The balance therefore does not move until the end, which
     * makes the total cost far higher than an annuity of the same rate and term - and
     * makes calling it an annuity a lie worth avoiding.
     */
    INTEREST_ONLY,
}

/**
 * A loan as the user entered it. Everything derived - the schedule, the interest,
 * the closing date - is computed from this plus its rate changes and prepayments,
 * and is never stored.
 *
 * Money is in kopecks. Rates are in basis points so no floating point is involved:
 * 1 bp = 0.01%, so 16.9% per year is 1690.
 */
data class Loan(
    val type: LoanType,
    val principalMinor: Long,
    /** Rate at [startDate]; later changes live in the rate history. */
    val annualRateBp: Int,
    val startDate: LocalDate,
    val termMonths: Int,
    /**
     * Day of month the payment falls on (1..31). Months that are too short clamp to
     * their last day: a 31st payment day means 28 February (29 in a leap year).
     */
    val paymentDay: Int,
    /**
     * One-off fee charged at origination. Not a scheduled payment - it is paid up
     * front - but it is real money out, so it counts towards the overpayment.
     */
    val upfrontFeeMinor: Long = 0,
    /**
     * Fee added on top of every scheduled payment. This is how a "0%" instalment plan
     * usually earns: no interest, but a monthly servicing charge.
     */
    val monthlyFeeMinor: Long = 0,
) {
    init {
        require(principalMinor > 0) { "Principal must be positive, was $principalMinor" }
        require(annualRateBp >= 0) { "Rate cannot be negative, was $annualRateBp" }
        require(termMonths > 0) { "Term must be positive, was $termMonths" }
        require(paymentDay in 1..31) { "Payment day must be 1..31, was $paymentDay" }
        require(upfrontFeeMinor >= 0) { "Upfront fee cannot be negative, was $upfrontFeeMinor" }
        require(monthlyFeeMinor >= 0) { "Monthly fee cannot be negative, was $monthlyFeeMinor" }
        require(type != LoanType.INSTALLMENT || annualRateBp == 0) {
            "An instalment plan carries no interest, rate was $annualRateBp"
        }
    }
}
