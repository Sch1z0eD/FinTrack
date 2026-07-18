package com.findev.fintrack.loanengine

import java.time.LocalDate

/**
 * The rate becomes [annualRateBp] from [effectiveFrom] onwards.
 *
 * A change mid-period splits that period: days before it accrue at the old rate,
 * days from it at the new one. Only the remaining schedule is affected - payments
 * already made are history.
 */
data class RateChange(
    val effectiveFrom: LocalDate,
    val annualRateBp: Int,
) {
    init {
        require(annualRateBp >= 0) { "Rate cannot be negative, was $annualRateBp" }
    }
}
