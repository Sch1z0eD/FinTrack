package com.findev.fintrack.data

import com.findev.fintrack.data.local.MonthlyTotal
import java.time.LocalDate
import java.time.YearMonth

/** How many months the monthly trend chart shows, ending at the current month. */
const val MONTHLY_BARS_COUNT = 6

/** One month's income and expense, ready for the trend chart. */
data class MonthlyBar(
    val month: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/**
 * The epoch day of the first day of the window the chart covers: the first day of the
 * month [count] - 1 months before [today]'s month. Used as the DAO's lower bound so the
 * query only scans months the chart will actually show.
 */
fun monthlyBarsFromEpochDay(today: LocalDate, count: Int = MONTHLY_BARS_COUNT): Long =
    today.withDayOfMonth(1).minusMonths((count - 1).toLong()).toEpochDay()

/**
 * Turns the sparse per-month totals from the DAO into a continuous run of [count] months
 * ending at [today]'s month, oldest first. Months the DAO did not report (no transactions)
 * become zero bars so the axis has no holes.
 */
fun buildMonthlyBars(
    totals: List<MonthlyTotal>,
    today: LocalDate,
    count: Int = MONTHLY_BARS_COUNT,
): List<MonthlyBar> {
    val byMonth = totals.associateBy { it.yearMonth }
    val thisMonth = YearMonth.from(today)
    return (count - 1 downTo 0).map { back ->
        val month = thisMonth.minusMonths(back.toLong())
        // strftime pads to "YYYY-MM"; YearMonth.toString() matches that exactly.
        val total = byMonth[month.toString()]
        MonthlyBar(
            month = month,
            incomeMinor = total?.incomeMinor ?: 0L,
            expenseMinor = total?.expenseMinor ?: 0L,
        )
    }
}
