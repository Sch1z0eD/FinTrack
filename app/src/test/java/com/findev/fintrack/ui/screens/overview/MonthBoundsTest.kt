package com.findev.fintrack.ui.screens.overview

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthBoundsTest {

    private fun bounds(date: String) = monthBounds(LocalDate.parse(date))

    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    @Test
    fun coversWholeMonthFromMidMonth() {
        val range = bounds("2026-07-16")

        assertEquals(day("2026-07-01"), range.first)
        assertEquals(day("2026-07-31"), range.last)
    }

    @Test
    fun isInclusiveOnBothEdges() {
        val range = bounds("2026-07-01")

        assertEquals(day("2026-07-01"), range.first)
        assertEquals(day("2026-07-31"), range.last)
        assertEquals(31, range.last - range.first + 1)
    }

    @Test
    fun handlesLeapFebruary() {
        val range = bounds("2024-02-10")

        assertEquals(day("2024-02-29"), range.last)
        assertEquals(29, range.last - range.first + 1)
    }

    @Test
    fun handlesShortFebruary() {
        val range = bounds("2026-02-10")

        assertEquals(day("2026-02-28"), range.last)
        assertEquals(28, range.last - range.first + 1)
    }

    @Test
    fun handlesDecemberWithoutSpillingIntoNextYear() {
        val range = bounds("2026-12-31")

        assertEquals(day("2026-12-01"), range.first)
        assertEquals(day("2026-12-31"), range.last)
    }
}
