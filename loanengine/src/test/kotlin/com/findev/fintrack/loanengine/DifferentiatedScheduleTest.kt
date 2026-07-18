package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DifferentiatedScheduleTest {

    private fun loan(
        principalMinor: Long = 120_000_00,
        annualRateMilliPercent: Int = 12000,
        termMonths: Int = 12,
        startDate: LocalDate = LocalDate.of(2026, 1, 15),
        paymentDay: Int = 15,
    ) = Loan(LoanType.DIFFERENTIATED, principalMinor, annualRateMilliPercent, startDate, termMonths, paymentDay)

    @Test
    fun firstPaymentMatchesTheHandCalculation() {
        // 120 000 руб. под 12% на 12 мес: тело = 120000/12 = 10 000 руб. каждый месяц.
        // Проценты за 15.01 -> 15.02 (31 день, база 365) на остаток 120 000:
        //   12000000 * 12000 * 31 / (100000 * 365) = 122 301,369... -> 122 301 коп.
        // Платёж = 1 000 000 + 122 301 = 1 122 301 коп. = 11 223,01 руб.
        val first = generateSchedule(loan()).first()

        assertEquals(1_000_000L, first.principalMinor)
        assertEquals(122_301L, first.interestMinor)
        assertEquals(1_122_301L, first.paymentMinor)
        assertEquals(LocalDate.of(2026, 2, 15), first.date)
    }

    @Test
    fun principalSlicesAreEqual() {
        val schedule = generateSchedule(loan())

        // That is the whole definition of a differentiated schedule.
        assertEquals(1, schedule.map { it.principalMinor }.distinct().size)
        assertEquals(1_000_000L, schedule.first().principalMinor)
    }

    @Test
    fun principalPartsAddUpToTheLoanExactly() {
        assertEquals(120_000_00L, generateSchedule(loan()).sumOf { it.principalMinor })
    }

    @Test
    fun roundingRemainderLandsOnTheLastPayment() {
        // 1000 руб. / 3 = 333,33 руб. -> slices of 33 333 коп. leave 1 копейка over.
        val schedule = generateSchedule(loan(principalMinor = 100_000, termMonths = 3))

        assertEquals(listOf(33_333L, 33_333L, 33_334L), schedule.map { it.principalMinor })
        // Nothing invented, nothing lost.
        assertEquals(100_000L, schedule.sumOf { it.principalMinor })
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun everyRowSplitsExactlyAndBalanceReachesZero() {
        val schedule = generateSchedule(loan())

        assertEquals(12, schedule.size)
        schedule.forEach { assertEquals(it.paymentMinor, it.interestMinor + it.principalMinor) }
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun paymentsFallOverTheTermAsTheBalanceShrinks() {
        val schedule = generateSchedule(loan())

        assertTrue(schedule.first().paymentMinor > schedule.last().paymentMinor)
        assertTrue(schedule.first().interestMinor > schedule.last().interestMinor)
    }

    @Test
    fun aLongMonthCanCostMoreThanTheShortOneBeforeIt() {
        // Deliberate: interest runs on actual days, so a 31-day March on a smaller
        // balance can still out-cost a 28-day February. "Payments always decrease"
        // is a myth this engine must not encode.
        val schedule = generateSchedule(
            loan(
                principalMinor = 120_000_00,
                annualRateMilliPercent = 12000,
                termMonths = 12,
                startDate = LocalDate.of(2027, 1, 1),
                paymentDay = 1,
            ),
        )

        val february = schedule.single { it.date == LocalDate.of(2027, 2, 1) } // 31 days of January
        val march = schedule.single { it.date == LocalDate.of(2027, 3, 1) } // 28 days of February
        val april = schedule.single { it.date == LocalDate.of(2027, 4, 1) } // 31 days of March

        assertTrue("28-day February must cost less", march.interestMinor < february.interestMinor)
        assertTrue("31-day March outweighs the smaller balance", april.interestMinor > march.interestMinor)
    }

    @Test
    fun zeroRateIsPurePrincipal() {
        val schedule = generateSchedule(loan(annualRateMilliPercent = 0))

        assertTrue(schedule.all { it.interestMinor == 0L })
        assertTrue(schedule.all { it.paymentMinor == 1_000_000L })
        assertEquals(120_000_00L, schedule.sumOf { it.principalMinor })
    }

    @Test
    fun shortMonthPaymentDayStillClamps() {
        val schedule = generateSchedule(
            loan(startDate = LocalDate.of(2026, 1, 31), paymentDay = 31, termMonths = 3),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            schedule.map { it.date },
        )
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun differentiatedCostsLessInterestThanAnnuity() {
        // Known property: paying principal down faster means less interest overall.
        val common = { type: LoanType ->
            Loan(type, 1_000_000_00, 1690, LocalDate.of(2026, 1, 15), 60, 15)
        }

        val differentiated = generateSchedule(common(LoanType.DIFFERENTIATED)).sumOf { it.interestMinor }
        val annuity = generateSchedule(common(LoanType.ANNUITY)).sumOf { it.interestMinor }

        assertTrue(
            "differentiated=$differentiated should be cheaper than annuity=$annuity",
            differentiated < annuity,
        )
    }
}
