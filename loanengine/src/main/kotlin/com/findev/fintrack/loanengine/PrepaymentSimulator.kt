package com.findev.fintrack.loanengine

import java.time.LocalDate

/**
 * What one extra payment does to the loan, measured against leaving it alone.
 */
data class PrepaymentEffect(
    val mode: PrepaymentMode,
    val closingDate: LocalDate,
    val paymentCount: Int,
    /** Payments the loan no longer needs. The schedule is monthly, so these are months. */
    val paymentsSaved: Int,
    /**
     * Overpayment avoided. Negative when the prepayment costs more than it saves, which
     * REDUCE_PAYMENT can genuinely do for a small amount: recomputing the annuity resets
     * the payment to what the /12 formula makes of the balance today, and that balance is
     * not what the /12 model predicted, because interest accrued on actual days. A short
     * first period leaves the real balance below the model's, so the recomputed payment
     * comes out lower - and a lower payment repays more slowly. Below roughly a few
     * hundred roubles that effect outweighs the prepayment itself.
     *
     * Banks recompute exactly this way, so the artefact is theirs too, not an error here.
     */
    val savedMinor: Long,
    /**
     * The scheduled payment that follows the prepayment, or null if the prepayment closed
     * the loan. This is the number that separates the two modes: REDUCE_TERM leaves it
     * alone, REDUCE_PAYMENT shrinks it.
     */
    val paymentAfterMinor: Long?,
) {
    init {
        // Nothing constrains savedMinor's sign - see above. The term, though, can only
        // shrink: the schedule runs to the agreed number of payments and stops early when
        // the debt is gone.
        require(paymentsSaved >= 0) {
            "Prepaying $mode cannot add payments, but saved $paymentsSaved"
        }
    }
}

/** The same prepayment answered both ways, against the loan as it stands today. */
data class PrepaymentSimulation(
    val baseline: LoanSummary,
    val reduceTerm: PrepaymentEffect,
    val reducePayment: PrepaymentEffect,
)

/**
 * Answers "what if I paid [amountMinor] extra on [date]?" for both modes at once.
 *
 * Nothing is stored and nothing is special-cased: each answer is the ordinary schedule
 * regenerated with one more prepayment in it, so the simulation and the real thing can
 * never disagree.
 */
fun simulatePrepayment(
    loan: Loan,
    rateChanges: List<RateChange> = emptyList(),
    existingPrepayments: List<Prepayment> = emptyList(),
    date: LocalDate,
    amountMinor: Long,
): PrepaymentSimulation {
    require(amountMinor > 0) { "A prepayment to simulate must be positive, was $amountMinor" }
    require(date > loan.startDate) { "A prepayment cannot predate the loan itself" }

    val baselineSchedule = generateSchedule(loan, rateChanges, existingPrepayments)
    val baseline = summarize(loan, baselineSchedule)
    require(!date.isAfter(baseline.closingDate)) {
        "The loan closes on ${baseline.closingDate}, so there is nothing left to prepay on $date"
    }

    fun effectOf(mode: PrepaymentMode): PrepaymentEffect =
        effectOf(loan, rateChanges, existingPrepayments, date, amountMinor, baseline, mode)

    return PrepaymentSimulation(
        baseline = baseline,
        reduceTerm = effectOf(PrepaymentMode.REDUCE_TERM),
        reducePayment = effectOf(PrepaymentMode.REDUCE_PAYMENT),
    )
}

private fun effectOf(
    loan: Loan,
    rateChanges: List<RateChange>,
    existingPrepayments: List<Prepayment>,
    date: LocalDate,
    amountMinor: Long,
    baseline: LoanSummary,
    mode: PrepaymentMode,
): PrepaymentEffect {
    val schedule = generateSchedule(
        loan = loan,
        rateChanges = rateChanges,
        prepayments = existingPrepayments + Prepayment(date, amountMinor, mode),
    )
    val summary = summarize(loan, schedule)

    // The payment covering the prepayment's own period is already sized, so the mode shows
    // up only in the one after it - that is the payment worth comparing. The period always
    // exists: the caller has checked the loan is still open on this date.
    val periodIndex = schedule.indexOfFirst { !it.date.isBefore(date) }
    check(periodIndex >= 0) { "No payment period covers $date" }
    val paymentAfter = schedule.getOrNull(periodIndex + 1)?.paymentMinor

    return PrepaymentEffect(
        mode = mode,
        closingDate = summary.closingDate,
        paymentCount = summary.paymentCount,
        paymentsSaved = baseline.paymentCount - summary.paymentCount,
        savedMinor = baseline.overpaymentMinor - summary.overpaymentMinor,
        paymentAfterMinor = paymentAfter,
    )
}
