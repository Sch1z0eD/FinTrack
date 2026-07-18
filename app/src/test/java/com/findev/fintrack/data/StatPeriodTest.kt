package com.findev.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatPeriodTest {

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    @Test
    fun thisMonthSpansTheWholeCalendarMonth() {
        val (from, to) = StatPeriod.THIS_MONTH.range(LocalDate.of(2026, 7, 18))
        assertEquals(day("2026-07-01"), from)
        assertEquals(day("2026-07-31"), to)
    }

    /** February is short: this month must end on the 28th, not roll into March. */
    @Test
    fun thisMonthEndsOnAShortMonthsLastDay() {
        val (from, to) = StatPeriod.THIS_MONTH.range(LocalDate.of(2026, 2, 10))
        assertEquals(day("2026-02-01"), from)
        assertEquals(day("2026-02-28"), to)
    }

    @Test
    fun lastMonthIsThePrecedingCalendarMonth() {
        val (from, to) = StatPeriod.LAST_MONTH.range(LocalDate.of(2026, 3, 15))
        assertEquals(day("2026-02-01"), from)
        assertEquals(day("2026-02-28"), to)
    }

    /** Last month from January is December of the previous year. */
    @Test
    fun lastMonthCrossesTheYearBoundary() {
        val (from, to) = StatPeriod.LAST_MONTH.range(LocalDate.of(2026, 1, 5))
        assertEquals(day("2025-12-01"), from)
        assertEquals(day("2025-12-31"), to)
    }

    @Test
    fun thisYearSpansJanuaryToDecember() {
        val (from, to) = StatPeriod.THIS_YEAR.range(LocalDate.of(2026, 7, 18))
        assertEquals(day("2026-01-01"), from)
        assertEquals(day("2026-12-31"), to)
    }
}
