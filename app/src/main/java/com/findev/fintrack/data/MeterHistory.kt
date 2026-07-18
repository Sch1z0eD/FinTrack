package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.MeterReadingEntity
import java.time.LocalDate
import java.time.YearMonth

/** What a meter counted in one calendar month, and what it cost. */
data class MonthlyConsumption(
    val month: YearMonth,
    val consumedMilli: Long,
    val amountMinor: Long,
)

/**
 * Consumption per calendar month, oldest first.
 *
 * Consumption is never stored, because it does not belong to a reading - it only exists
 * *between* two of them. So the oldest reading contributes nothing (it is where counting
 * starts) and every later one contributes the gap from the reading before it, credited to
 * the month it was taken in.
 *
 * The money comes from the reading's own [MeterReadingEntity.amountMinor] rather than being
 * recomputed: that is the snapshot of what was actually charged, at the tariff in force
 * then, which is the whole reason it is stored.
 *
 * Two readings in the same month - a correction, or catching up after a missed month - add
 * together instead of drawing two bars for one month.
 */
fun monthlyConsumption(readings: List<MeterReadingEntity>): List<MonthlyConsumption> =
    readings
        .sortedBy { it.dateEpochDay }
        .zipWithNext { previous, current ->
            MonthlyConsumption(
                month = YearMonth.from(LocalDate.ofEpochDay(current.dateEpochDay)),
                consumedMilli = current.valueMilli - previous.valueMilli,
                amountMinor = current.amountMinor,
            )
        }
        .groupBy { it.month }
        .map { (month, entries) ->
            MonthlyConsumption(
                month = month,
                consumedMilli = entries.sumOf { it.consumedMilli },
                amountMinor = entries.sumOf { it.amountMinor },
            )
        }
        .sortedBy { it.month }
