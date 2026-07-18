package com.findev.fintrack.data

import java.time.LocalDate

/**
 * The period a screen is showing. Shared by statistics and the transaction feed so the
 * two never disagree about what "этот месяц" means.
 */
enum class StatPeriod { WEEK, THIS_MONTH, LAST_MONTH, QUARTER, THIS_YEAR, ALL, CUSTOM }

/**
 * A chosen period, with the two dates that only [StatPeriod.CUSTOM] carries.
 *
 * One type rather than a period plus two loose nullable dates: the range and the choice
 * that produced it always travel together, and separating them invites a CUSTOM with no
 * dates set.
 */
data class PeriodSelection(
    val period: StatPeriod = StatPeriod.THIS_MONTH,
    val customFromEpochDay: Long? = null,
    val customToEpochDay: Long? = null,
) {
    /** Inclusive bounds, or null for "no limit" - see [StatPeriod.range]. */
    fun bounds(today: LocalDate): Pair<Long, Long>? =
        if (period == StatPeriod.CUSTOM) {
            val from = customFromEpochDay
            val to = customToEpochDay
            // A half-filled custom range must not silently become "everything": until both
            // ends are picked the selection is not usable, so nothing is filtered out.
            if (from != null && to != null) minOf(from, to) to maxOf(from, to) else null
        } else {
            period.range(today)
        }
}

/**
 * Inclusive epoch-day range for a period, relative to [today], or null for [ALL].
 *
 * Null rather than a sentinel pair: a range of "epoch day 0 to today" would quietly drop
 * anything dated before 1970 or in the future, and a filter that silently hides rows is
 * worse than no filter.
 */
fun StatPeriod.range(today: LocalDate): Pair<Long, Long>? {
    val (start, endExclusive) = when (this) {
        // Rolling windows, not calendar ones: "за неделю" on the 2nd of the month should
        // not mean two days.
        StatPeriod.WEEK -> today.minusDays(6) to today.plusDays(1)
        StatPeriod.QUARTER -> today.minusMonths(3).plusDays(1) to today.plusDays(1)
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
        // Both are handled by PeriodSelection.bounds; neither has a range of its own.
        StatPeriod.ALL, StatPeriod.CUSTOM -> return null
    }
    // The DAO range is inclusive on both ends, so the last day is one before the next period.
    return start.toEpochDay() to endExclusive.minusDays(1).toEpochDay()
}
