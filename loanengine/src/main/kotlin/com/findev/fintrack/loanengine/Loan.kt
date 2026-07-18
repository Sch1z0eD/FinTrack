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
 * Money is in kopecks. Rates are in thousandths of a percent so no floating point is
 * involved: 1 unit = 0.001%, so 16.9% per year is 16900.
 *
 * Basis points (0.01%) were the original unit and were not enough. Real contracts quote
 * three decimals - a Яндекс Банк loan at 28.572% is 2857.2 bp, which an Int cannot hold.
 * Truncating it to 2857 cost 1.86 ₽ on that contract's final payment.
 */
data class Loan(
    val type: LoanType,
    val principalMinor: Long,
    /** Rate at [startDate]; later changes live in the rate history. */
    val annualRateMilliPercent: Int,
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
    /**
     * Payment size taken from the contract instead of derived from the rate.
     *
     * Some contracts publish a level payment the annuity formula cannot reproduce. The
     * case that forced this: a Яндекс Банк loan whose two rates are both fixed at signing
     * (70% to 01.05.2026, then 28.572%), with one payment of 6696.00 solved across BOTH
     * periods and held constant through the switch. Re-annuitising at the rate change -
     * the right answer for a rate that moves unexpectedly - gives a different payment,
     * and even the opening one is wrong: the formula says 7845.53, the contract says
     * 6696.00.
     *
     * There is no reliable way to reverse-engineer how a given bank solved for its level
     * payment, and the number is printed in the contract anyway. So when this is set the
     * engine takes it as given and derives only the interest/principal split. A rate
     * change then splits interest without resizing the payment.
     *
     * Prepayments still resize it: REDUCE_PAYMENT has to re-derive something, and the
     * annuity formula is the only estimate available. That makes the post-prepayment tail
     * an approximation of what the bank will send back - flagged here rather than hidden.
     */
    val fixedPaymentMinor: Long? = null,
) {
    init {
        require(principalMinor > 0) { "Principal must be positive, was $principalMinor" }
        require(annualRateMilliPercent >= 0) { "Rate cannot be negative, was $annualRateMilliPercent" }
        require(termMonths > 0) { "Term must be positive, was $termMonths" }
        require(paymentDay in 1..31) { "Payment day must be 1..31, was $paymentDay" }
        require(upfrontFeeMinor >= 0) { "Upfront fee cannot be negative, was $upfrontFeeMinor" }
        require(monthlyFeeMinor >= 0) { "Monthly fee cannot be negative, was $monthlyFeeMinor" }
        require(type != LoanType.INSTALLMENT || annualRateMilliPercent == 0) {
            "An instalment plan carries no interest, rate was $annualRateMilliPercent"
        }
        require(fixedPaymentMinor == null || fixedPaymentMinor > 0) {
            "Contract payment must be positive, was $fixedPaymentMinor"
        }
        require(fixedPaymentMinor == null || type == LoanType.ANNUITY) {
            "A contract payment only makes sense for a level-payment loan, type was $type"
        }
    }
}
