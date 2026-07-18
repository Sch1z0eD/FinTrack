package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.RecurrencePeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * When a recurring payment falls due. Pure date arithmetic - no storage, no Android.
 *
 * Every occurrence is computed from the start date as `start + n periods`, never by
 * stepping from the previous one. That distinction is the whole problem: a payment
 * starting on the 31st lands on 28.02 in February, and stepping a month on from *that*
 * would give 28.03 - the payment would silently walk backwards through the calendar and
 * never return to the 31st. Anchoring on the start keeps 31.01 -> 28.02 -> 31.03, which
 * is what a bank standing order does.
 */
fun nthRecurrence(start: LocalDate, period: RecurrencePeriod, n: Long): LocalDate = when (period) {
    RecurrencePeriod.DAY -> start.plusDays(n)
    RecurrencePeriod.WEEK -> start.plusWeeks(n)
    // plusMonths/plusYears clamp to the last day of a short month by themselves.
    RecurrencePeriod.MONTH -> start.plusMonths(n)
    RecurrencePeriod.YEAR -> start.plusYears(n)
}

/**
 * The first due date on or after [onOrAfter], or null once [end] has passed.
 *
 * [end] is inclusive: a payment may fall due on its last day.
 */
fun nextRecurrenceOnOrAfter(
    start: LocalDate,
    period: RecurrencePeriod,
    end: LocalDate?,
    onOrAfter: LocalDate,
): LocalDate? {
    // Estimate, then walk forward. The estimate can undershoot when clamping shortens a
    // period, so it is a starting point rather than an answer.
    var n = estimatePeriods(start, period, onOrAfter).coerceAtLeast(0)
    var candidate = nthRecurrence(start, period, n)
    while (candidate.isBefore(onOrAfter)) {
        n++
        candidate = nthRecurrence(start, period, n)
    }

    if (end != null && candidate.isAfter(end)) return null
    return candidate
}

/**
 * The occurrence the list should be pointing at, given what is known to be paid.
 *
 * [paidThrough] null does not mean "nothing is paid" - it means nothing is known. A
 * payment entered today may have been running for years, and calling its first occurrence
 * overdue would be an accusation the data does not support. So until the user marks
 * something paid, the calendar alone decides and the past is left alone.
 *
 * Once something is marked, the answer is the next occurrence after it, and that one is
 * allowed to be in the past: an occurrence that came due and was never marked is exactly
 * what "overdue" means, and hiding it would be the whole point missed.
 */
fun nextDueRecurrence(
    start: LocalDate,
    period: RecurrencePeriod,
    end: LocalDate?,
    paidThrough: LocalDate?,
    today: LocalDate,
): LocalDate? = if (paidThrough == null) {
    nextRecurrenceOnOrAfter(start, period, end, today)
} else {
    nextRecurrenceOnOrAfter(start, period, end, paidThrough.plusDays(1))
}

/**
 * How many occurrences the payment has in total, or null when it is open-ended.
 *
 * An open-ended payment has no total, and inventing one - "until 2099" - would put a
 * progress bar on a subscription that is not progressing towards anything.
 */
fun totalRecurrences(start: LocalDate, period: RecurrencePeriod, end: LocalDate?): Int? {
    if (end == null) return null
    if (end.isBefore(start)) return 0

    // The estimate can overshoot when clamping shortens a period, so it is walked back to
    // the last occurrence that actually lands on or before the end.
    var n = estimatePeriods(start, period, end)
    while (n > 0 && nthRecurrence(start, period, n).isAfter(end)) n--
    return (n + 1).toInt()
}

/** Occurrences already settled, given what is paid through. */
fun settledRecurrences(
    start: LocalDate,
    period: RecurrencePeriod,
    paidThrough: LocalDate?,
): Int {
    if (paidThrough == null || paidThrough.isBefore(start)) return 0

    var n = estimatePeriods(start, period, paidThrough)
    while (n > 0 && nthRecurrence(start, period, n).isAfter(paidThrough)) n--
    return (n + 1).toInt()
}

private fun estimatePeriods(start: LocalDate, period: RecurrencePeriod, target: LocalDate): Long {
    val unit = when (period) {
        RecurrencePeriod.DAY -> ChronoUnit.DAYS
        RecurrencePeriod.WEEK -> ChronoUnit.WEEKS
        RecurrencePeriod.MONTH -> ChronoUnit.MONTHS
        RecurrencePeriod.YEAR -> ChronoUnit.YEARS
    }
    return unit.between(start, target)
}
