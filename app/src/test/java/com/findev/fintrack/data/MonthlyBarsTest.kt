package com.findev.fintrack.data

import com.findev.fintrack.data.local.MonthlyTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyBarsTest {

    @Test
    fun buildsExactlyCountMonthsEndingAtToday() {
        val bars = buildMonthlyBars(emptyList(), LocalDate.of(2026, 7, 18), count = 6)
        assertEquals(6, bars.size)
        assertEquals(YearMonth.of(2026, 2), bars.first().month)
        assertEquals(YearMonth.of(2026, 7), bars.last().month)
    }

    @Test
    fun missingMonthsBecomeZeroBars() {
        val totals = listOf(MonthlyTotal("2026-07", incomeMinor = 5000, expenseMinor = 3000))
        val bars = buildMonthlyBars(totals, LocalDate.of(2026, 7, 18), count = 3)
        assertEquals(listOf(0L, 0L, 5000L), bars.map { it.incomeMinor })
        assertEquals(listOf(0L, 0L, 3000L), bars.map { it.expenseMinor })
    }

    @Test
    fun totalsAreMatchedToTheirMonth() {
        val totals = listOf(
            MonthlyTotal("2026-05", incomeMinor = 100, expenseMinor = 200),
            MonthlyTotal("2026-07", incomeMinor = 700, expenseMinor = 800),
        )
        val bars = buildMonthlyBars(totals, LocalDate.of(2026, 7, 18), count = 3)
        assertEquals(YearMonth.of(2026, 5), bars[0].month)
        assertEquals(100L, bars[0].incomeMinor)
        assertEquals(200L, bars[0].expenseMinor)
        assertEquals(0L, bars[1].incomeMinor) // June, no data
        assertEquals(700L, bars[2].incomeMinor)
    }

    /** The window crosses the year boundary and the "YYYY-MM" keys must still match. */
    @Test
    fun windowCrossesTheYearBoundary() {
        val totals = listOf(MonthlyTotal("2025-12", incomeMinor = 9, expenseMinor = 1))
        val bars = buildMonthlyBars(totals, LocalDate.of(2026, 2, 10), count = 4)
        assertEquals(YearMonth.of(2025, 11), bars.first().month)
        assertEquals(YearMonth.of(2026, 2), bars.last().month)
        assertEquals(9L, bars[1].incomeMinor) // 2025-12
    }

    @Test
    fun fromEpochDayIsTheFirstDayOfTheOldestMonth() {
        val from = monthlyBarsFromEpochDay(LocalDate.of(2026, 7, 18), count = 6)
        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), from)
    }
}
