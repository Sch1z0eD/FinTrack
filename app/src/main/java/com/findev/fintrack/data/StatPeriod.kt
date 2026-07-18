package com.findev.fintrack.data

import java.time.LocalDate

/** The period a statistics screen is showing. */
enum class StatPeriod { THIS_MONTH, LAST_MONTH, THIS_YEAR }

/** Inclusive epoch-day range for a period, relative to [today]. */
fun StatPeriod.range(today: LocalDate): Pair<Long, Long> {
    val (start, endExclusive) = when (this) {
        StatPeriod.THIS_MONTH -> {
            val first = today.withDayOfMonth(1)
            first to first.plusMonths(1)
        }
        StatPeriod.LAST_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            first to first.plusMonths(1)
        }
        StatPeriod.THIS_YEAR -> {
            val first = today.withDayOfYear(1)
            first to first.plusYears(1)
        }
    }
    // The DAO range is inclusive on both ends, so the last day is one before the next period.
    return start.toEpochDay() to endExclusive.minusDays(1).toEpochDay()
}
