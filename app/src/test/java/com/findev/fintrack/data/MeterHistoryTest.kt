package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.MeterReadingEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MeterHistoryTest {

    private fun reading(date: String, valueMilli: Long, amountMinor: Long = 0) = MeterReadingEntity(
        id = date + valueMilli,
        meterId = "m",
        valueMilli = valueMilli,
        dateEpochDay = LocalDate.parse(date).toEpochDay(),
        tariffMinor = 6_36,
        amountMinor = amountMinor,
        updatedAt = 0,
    )

    /** The real meter: 16180 in May, 16377 in June - one month of history, not two. */
    @Test
    fun theBaselineMonthIsNotAMonthOfConsumption() {
        val months = monthlyConsumption(
            listOf(
                reading("2026-05-25", 16_180_000),
                reading("2026-06-25", 16_377_000, amountMinor = 1_252_92),
            ),
        )

        assertEquals(1, months.size)
        assertEquals(YearMonth.of(2026, 6), months[0].month)
        assertEquals(197_000L, months[0].consumedMilli)
        assertEquals(1_252_92L, months[0].amountMinor)
    }

    @Test
    fun aSingleReadingChartsNothing() {
        assertEquals(emptyList<MonthlyConsumption>(), monthlyConsumption(listOf(reading("2026-05-25", 16_180_000))))
        assertEquals(emptyList<MonthlyConsumption>(), monthlyConsumption(emptyList()))
    }

    /** Each month is measured from the reading before it, whatever order they arrive in. */
    @Test
    fun monthsComeOutOldestFirstFromUnorderedReadings() {
        val months = monthlyConsumption(
            listOf(
                reading("2026-07-25", 16_600_000, amountMinor = 1_418_28),
                reading("2026-05-25", 16_180_000),
                reading("2026-06-25", 16_377_000, amountMinor = 1_252_92),
            ),
        )

        assertEquals(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7)), months.map { it.month })
        assertEquals(listOf(197_000L, 223_000L), months.map { it.consumedMilli })
    }

    /** Two readings in one month are one bar: a correction is not a second month. */
    @Test
    fun readingsInTheSameMonthAddUp() {
        val months = monthlyConsumption(
            listOf(
                reading("2026-05-25", 16_180_000),
                reading("2026-06-10", 16_280_000, amountMinor = 636_00),
                reading("2026-06-25", 16_377_000, amountMinor = 616_92),
            ),
        )

        assertEquals(1, months.size)
        assertEquals(197_000L, months[0].consumedMilli)
        assertEquals(1_252_92L, months[0].amountMinor)
    }
}
