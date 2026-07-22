package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.MeterEntity
import java.time.LocalDate

/**
 * The day of month [day] actually lands on in [month]'s calendar.
 *
 * Day 31 cannot happen in February. Clamping to the month's length means "the 31st" becomes
 * "the last day" rather than silently skipping short months - so a payment set for the 31st
 * still lands every month, on the 28th, 30th or 31st as the calendar allows.
 */
fun effectiveDayOfMonth(day: Int, month: LocalDate): Int =
    day.coerceAtMost(month.lengthOfMonth())

/**
 * Which meters should be reminded about on [today], for any billing kind.
 *
 * A meter reminds N days before its payment day, for each lead time it lists. Rather than
 * project payment dates forward, this asks the reverse: for each lead time N, is `today + N`
 * that meter's payment day? If so, today is exactly N days before it. Working from `today + N`
 * makes month boundaries fall out for free - a reminder 7 days before the 3rd lands late in the
 * previous month without any special case.
 */
fun metersToRemindToday(meters: List<MeterEntity>, today: LocalDate): List<MeterEntity> =
    meters.filter { meter ->
        meter.paymentDay in 1..31 &&
            meter.reminderDaysList.any { daysBefore ->
                val paymentDate = today.plusDays(daysBefore.toLong())
                paymentDate.dayOfMonth == effectiveDayOfMonth(meter.paymentDay, paymentDate)
            }
    }
