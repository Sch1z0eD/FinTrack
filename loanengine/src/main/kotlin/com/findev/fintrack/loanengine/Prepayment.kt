package com.findev.fintrack.loanengine

import java.time.LocalDate

enum class PrepaymentMode {
    /** Keep paying the same amount; the loan simply ends sooner. */
    REDUCE_TERM,

    /** Keep the original end date; each remaining payment gets smaller. */
    REDUCE_PAYMENT,
}

/**
 * Money paid on top of the schedule on [date]. It goes entirely against the balance,
 * so everything after it is recomputed from the reduced balance.
 */
data class Prepayment(
    val date: LocalDate,
    val amountMinor: Long,
    val mode: PrepaymentMode,
) {
    init {
        require(amountMinor > 0) { "Prepayment must be positive, was $amountMinor" }
    }
}
