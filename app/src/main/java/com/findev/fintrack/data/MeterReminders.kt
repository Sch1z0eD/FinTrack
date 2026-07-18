package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import java.time.LocalDate

/**
 * The day a meter's reminder actually lands in a given month.
 *
 * A reminder day of 31 cannot happen in February. Clamping to the month's length means
 * "the 31st" becomes "the last day" rather than silently skipping short months - so a
 * reminder set for the 31st still fires every month, on the 28th, 30th or 31st as the
 * calendar allows.
 */
fun effectiveReminderDay(reminderDay: Int, month: LocalDate): Int =
    reminderDay.coerceAtMost(month.lengthOfMonth())

/**
 * Which meters should be reminded about on [today].
 *
 * Only metered services: a normative service takes no readings, so there is nothing to be
 * reminded to submit (and its reminder day is 0). A reminder day outside 1..31 is treated
 * as "off" rather than trusted blindly.
 */
fun metersDueToday(meters: List<MeterEntity>, today: LocalDate): List<MeterEntity> =
    meters.filter { meter ->
        meter.billing == BillingKind.METERED &&
            meter.reminderDay in 1..31 &&
            effectiveReminderDay(meter.reminderDay, today) == today.dayOfMonth
    }
