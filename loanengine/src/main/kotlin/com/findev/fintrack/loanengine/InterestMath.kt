package com.findev.fintrack.loanengine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Interest arithmetic. Money is Long kopecks; intermediates are BigDecimal.
 * Double never appears - a fraction of a kopeck lost per period becomes roubles
 * over a 20-year mortgage.
 */

/** Wide enough that powers and divisions never lose a kopeck before the final rounding. */
internal val MATH: MathContext = MathContext(30, RoundingMode.HALF_UP)

/** Rates are thousandths of a percent, so 100% is 100 000 units. See [Loan.annualRateMilliPercent]. */
private val PERCENT_UNITS: BigDecimal = BigDecimal(100_000)
private val MONTHS_PER_YEAR: BigDecimal = BigDecimal(12)

internal fun daysInYear(year: Int): Int = if (Year.isLeap(year.toLong())) 366 else 365

/** Rounds to whole kopecks, HALF_UP, exactly as a bank does at every payment step. */
internal fun BigDecimal.toKopecksHalfUp(): Long = setScale(0, RoundingMode.HALF_UP).toLong()

/**
 * Exact, unrounded interest accrued on [balanceMinor] from [from] (inclusive) to
 * [to] (exclusive).
 *
 * ACT/ACT: every day accrues at rate / days-in-its-own-year, so a period crossing
 * New Year is split - 365 for the days in a common year, 366 for a leap one.
 *
 * Deliberately returns an unrounded value: rounding must happen once per payment.
 * Rounding each segment separately would drift a kopeck at every year boundary.
 */
internal fun exactInterest(
    balanceMinor: Long,
    rateMilliPercent: Int,
    from: LocalDate,
    to: LocalDate,
): BigDecimal {
    require(!to.isBefore(from)) { "Period ends $to before it starts $from" }
    if (balanceMinor == 0L || rateMilliPercent == 0) return BigDecimal.ZERO

    var accrued = BigDecimal.ZERO
    var cursor = from
    while (cursor < to) {
        val nextYear = LocalDate.of(cursor.year + 1, 1, 1)
        val segmentEnd = if (nextYear < to) nextYear else to
        val days = ChronoUnit.DAYS.between(cursor, segmentEnd)

        accrued = accrued.add(
            BigDecimal(balanceMinor)
                .multiply(BigDecimal(rateMilliPercent))
                .multiply(BigDecimal(days))
                .divide(PERCENT_UNITS.multiply(BigDecimal(daysInYear(cursor.year))), MATH),
        )
        cursor = segmentEnd
    }
    return accrued
}

/** Rate in force on [date]: the latest change that has taken effect, else [baseRateMilliPercent]. */
internal fun rateOn(baseRateMilliPercent: Int, sortedChanges: List<RateChange>, date: LocalDate): Int =
    sortedChanges.lastOrNull { !it.effectiveFrom.isAfter(date) }?.annualRateMilliPercent ?: baseRateMilliPercent

/**
 * Exact, unrounded interest for [from] until [to], honouring rate changes inside the
 * period: the days before a change accrue at the old rate, the days from it at the new.
 *
 * Each slice still goes through [exactInterest], so the year bases stay correct even
 * when a rate change and New Year fall in the same period. Nothing is rounded here -
 * that happens once, on the whole payment.
 */
internal fun exactInterestWithRateChanges(
    balanceMinor: Long,
    baseRateMilliPercent: Int,
    sortedChanges: List<RateChange>,
    from: LocalDate,
    to: LocalDate,
): BigDecimal {
    require(!to.isBefore(from)) { "Period ends $to before it starts $from" }

    var accrued = BigDecimal.ZERO
    var cursor = from
    while (cursor < to) {
        val nextChange = sortedChanges
            .firstOrNull { it.effectiveFrom > cursor && it.effectiveFrom < to }
            ?.effectiveFrom
        val segmentEnd = nextChange ?: to

        accrued = accrued.add(
            exactInterest(balanceMinor, rateOn(baseRateMilliPercent, sortedChanges, cursor), cursor, segmentEnd),
        )
        cursor = segmentEnd
    }
    return accrued
}

/**
 * Date of payment number [number] (1-based), counted in whole months from [start].
 * Short months clamp: a 31st payment day lands on 28 February, or 29 in a leap year.
 */
internal fun paymentDate(
    start: LocalDate,
    paymentDay: Int,
    number: Int,
    firstPaymentDate: LocalDate? = null,
): LocalDate {
    // With a first payment date given, it decides which month payment 1 lands in and the
    // rest follow monthly from there. The day still comes from paymentDay, so there is one
    // source for "the 17th" rather than two that can disagree.
    val month = if (firstPaymentDate != null) {
        YearMonth.from(firstPaymentDate).plusMonths((number - 1).toLong())
    } else {
        YearMonth.from(start).plusMonths(number.toLong())
    }
    return month.atDay(minOf(paymentDay, month.lengthOfMonth()))
}

/**
 * Size of the equal annuity payment: P*i*(1+i)^n / ((1+i)^n - 1), with i = annual/12.
 *
 * The /12 sets the payment SIZE only. Interest inside each payment still accrues on
 * actual days ([exactInterest]), which is exactly why the final payment comes out
 * different from the rest - banks work the same way.
 */
internal fun annuityPaymentMinor(principalMinor: Long, annualRateMilliPercent: Int, termMonths: Int): Long {
    if (annualRateMilliPercent == 0) {
        return BigDecimal(principalMinor).divide(BigDecimal(termMonths), MATH).toKopecksHalfUp()
    }

    val monthlyRate = BigDecimal(annualRateMilliPercent).divide(PERCENT_UNITS.multiply(MONTHS_PER_YEAR), MATH)
    val growth = BigDecimal.ONE.add(monthlyRate).pow(termMonths, MATH)

    return BigDecimal(principalMinor)
        .multiply(monthlyRate)
        .multiply(growth)
        .divide(growth.subtract(BigDecimal.ONE), MATH)
        .toKopecksHalfUp()
}
