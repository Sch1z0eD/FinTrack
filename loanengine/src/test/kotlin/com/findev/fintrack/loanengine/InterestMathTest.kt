package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Expected values here are worked out by hand, not by re-running the engine's own
 * formula - a test that recomputes the implementation proves nothing.
 */
class InterestMathTest {

    /** 1 000 000 roubles. */
    private val balance = 1_000_000_00L
    private val rate12Percent = 12000

    @Test
    fun annuityPaymentMatchesThePublishedFigure() {
        // 1 000 000 руб. под 12% на 12 месяцев is the textbook case: 88 848,79 руб.
        // i = 0.01, 1.01^12 = 1.12682503..., A = P*i*(1+i)^n / ((1+i)^n - 1).
        val payment = annuityPaymentMinor(
            principalMinor = 1_000_000_00,
            annualRateMilliPercent = rate12Percent,
            termMonths = 12,
        )

        assertEquals(88_848_79L, payment)
    }

    @Test
    fun zeroRatePaymentIsJustThePrincipalSplitEvenly() {
        assertEquals(10_000_00L, annuityPaymentMinor(120_000_00, annualRateMilliPercent = 0, termMonths = 12))
    }

    @Test
    fun interestFollowsActualDaysOfTheMonth() {
        // February 2027: 28 days, common year base 365.
        // 100000000 * 12000 * 28 / (100000 * 365) = 920 547,945... -> 920 548
        val february = exactInterest(
            balance, rate12Percent, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1),
        ).toKopecksHalfUp()
        assertEquals(920_548L, february)

        // March 2027: 31 days. 100000000 * 12000 * 31 / (100000 * 365) = 1 019 178,08 -> 1 019 178
        val march = exactInterest(
            balance, rate12Percent, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1),
        ).toKopecksHalfUp()
        assertEquals(1_019_178L, march)

        // The whole point of "not /12": a longer month really does cost more.
        assertTrue(march > february)
    }

    @Test
    fun leapFebruaryUsesBase366AndItsExtraDay() {
        // February 2028: 29 days, leap year base 366.
        // 100000000 * 12000 * 29 / (100000 * 366) = 950 819,672... -> 950 820
        val leapFebruary = exactInterest(
            balance, rate12Percent, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1),
        ).toKopecksHalfUp()

        assertEquals(950_820L, leapFebruary)
    }

    @Test
    fun periodAcrossNewYearSplitsBetweenBothYearBases() {
        // 15.12.2027 -> 15.01.2028 is 31 days: 17 of them in 2027 (base 365) and
        // 14 in 2028, which is a leap year (base 366).
        // 17 days: 100000000 * 12000 * 17 / (100000 * 365) =   558 904,109...
        // 14 days: 100000000 * 12000 * 14 / (100000 * 366) =   459 016,393...
        //                                          total = 1 017 920,503  -> 1 017 921
        val actual = exactInterest(
            balance, rate12Percent, LocalDate.of(2027, 12, 15), LocalDate.of(2028, 1, 15),
        ).toKopecksHalfUp()

        assertEquals(1_017_921L, actual)

        // And it must sit strictly between "all 31 days at 365" and "all at 366",
        // which is what proves the period was actually split.
        val allAt365 = 1_019_178L // 3.72e12 / 3.65e6
        val allAt366 = 1_016_393L // 3.72e12 / 3.66e6
        assertTrue(actual < allAt365)
        assertTrue(actual > allAt366)
    }

    @Test
    fun noInterestWithoutBalanceOrRate() {
        val from = LocalDate.of(2027, 1, 1)
        val to = LocalDate.of(2027, 2, 1)

        assertEquals(0L, exactInterest(0, rate12Percent, from, to).toKopecksHalfUp())
        assertEquals(0L, exactInterest(balance, 0, from, to).toKopecksHalfUp())
        assertEquals(0L, exactInterest(balance, rate12Percent, from, from).toKopecksHalfUp())
    }

    @Test
    fun daysInYearKnowsLeapYears() {
        assertEquals(365, daysInYear(2027))
        assertEquals(366, daysInYear(2028))
        assertEquals(366, daysInYear(2000)) // divisible by 400
        assertEquals(365, daysInYear(1900)) // divisible by 100 but not 400
    }

    @Test
    fun paymentDayClampsToShortMonths() {
        val start = LocalDate.of(2026, 1, 31)

        // No 31 February: the payment lands on the last day instead.
        assertEquals(LocalDate.of(2026, 2, 28), paymentDate(start, paymentDay = 31, number = 1))
        // ...and springs back to the 31st where the month allows.
        assertEquals(LocalDate.of(2026, 3, 31), paymentDate(start, paymentDay = 31, number = 2))
        assertEquals(LocalDate.of(2026, 4, 30), paymentDate(start, paymentDay = 31, number = 3))
        // Leap February gets its 29th.
        assertEquals(LocalDate.of(2028, 2, 29), paymentDate(LocalDate.of(2028, 1, 31), 31, 1))
    }

    @Test
    fun paymentDatesWalkWholeMonths() {
        val start = LocalDate.of(2026, 1, 15)

        assertEquals(LocalDate.of(2026, 2, 15), paymentDate(start, 15, 1))
        assertEquals(LocalDate.of(2026, 12, 15), paymentDate(start, 15, 11))
        assertEquals(LocalDate.of(2027, 1, 15), paymentDate(start, 15, 12))
    }
}
