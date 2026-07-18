package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnnuityScheduleTest {

    private fun loan(
        principalMinor: Long = 1_000_000_00,
        annualRateBp: Int = 1690,
        termMonths: Int = 60,
        startDate: LocalDate = LocalDate.of(2026, 1, 15),
        paymentDay: Int = 15,
    ) = Loan(LoanType.ANNUITY, principalMinor, annualRateBp, startDate, termMonths, paymentDay)

    @Test
    fun everyRowSplitsExactlyIntoInterestAndPrincipal() {
        // Guaranteed by ScheduleEntry's own check, but assert it over a real schedule:
        // this is the rule a rounding mistake would break first.
        generateSchedule(loan()).forEach { entry ->
            assertEquals(entry.paymentMinor, entry.interestMinor + entry.principalMinor)
        }
    }

    @Test
    fun principalPartsAddUpToTheLoanExactly() {
        val schedule = generateSchedule(loan())

        // Not "about" the principal - to the kopeck. Rounding must not invent or lose money.
        assertEquals(1_000_000_00L, schedule.sumOf { it.principalMinor })
    }

    @Test
    fun balanceWalksDownToExactlyZero() {
        val schedule = generateSchedule(loan())

        assertEquals(60, schedule.size)
        assertEquals(0L, schedule.last().balanceAfterMinor)
        // And it never jumps around on the way.
        schedule.zipWithNext { previous, next ->
            assertTrue(next.balanceAfterMinor < previous.balanceAfterMinor)
        }
    }

    @Test
    fun allPaymentsAreEqualExceptTheAdjustingLast() {
        val schedule = generateSchedule(loan())
        val regular = schedule.dropLast(1)

        // "Annuity" means equal payments...
        assertEquals(1, regular.map { it.paymentMinor }.distinct().size)
        // ...except the final one, which absorbs the drift between the /12 payment size
        // and interest accrued on actual days. Banks do exactly this.
        assertNotEquals(regular.last().paymentMinor, schedule.last().paymentMinor)
    }

    @Test
    fun interestShrinksAsPrincipalGrows() {
        val schedule = generateSchedule(loan())

        // The signature shape of an annuity: early payments are mostly interest.
        assertTrue(schedule.first().interestMinor > schedule.first().principalMinor)
        assertTrue(schedule.last().principalMinor > schedule.last().interestMinor)
    }

    @Test
    fun zeroRateLoanIsPurePrincipal() {
        val schedule = generateSchedule(loan(principalMinor = 120_000_00, annualRateBp = 0, termMonths = 12))

        assertEquals(12, schedule.size)
        assertTrue(schedule.all { it.interestMinor == 0L })
        assertTrue(schedule.all { it.paymentMinor == 10_000_00L })
        assertEquals(0L, schedule.last().balanceAfterMinor)
        assertEquals(120_000_00L, schedule.sumOf { it.principalMinor })
    }

    @Test
    fun firstPeriodRunsFromTheStartDateNotFromTheMonthStart() {
        // 15.01 -> 15.02 is 31 days, so the first interest must match those 31 days
        // rather than a "monthly" twelfth.
        val schedule = generateSchedule(
            loan(principalMinor = 1_000_000_00, annualRateBp = 1200, startDate = LocalDate.of(2026, 1, 15)),
        )

        // 100000000 * 1200 * 31 / (10000 * 365) = 1 019 178,08 -> 1 019 178
        assertEquals(1_019_178L, schedule.first().interestMinor)
        assertEquals(LocalDate.of(2026, 2, 15), schedule.first().date)
    }

    @Test
    fun scheduleSurvivesAShortMonthPaymentDay() {
        val schedule = generateSchedule(
            loan(startDate = LocalDate.of(2026, 1, 31), paymentDay = 31, termMonths = 3),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
            ),
            schedule.map { it.date },
        )
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun singlePaymentLoanClearsItselfAtOnce() {
        val schedule = generateSchedule(loan(principalMinor = 100_000_00, termMonths = 1))

        assertEquals(1, schedule.size)
        assertEquals(100_000_00L, schedule.single().principalMinor)
        assertEquals(0L, schedule.single().balanceAfterMinor)
    }

    @Test
    fun longMortgageStillReconcilesToTheKopeck() {
        // 20 years crosses five leap years; drift would show up here if anywhere.
        val schedule = generateSchedule(loan(principalMinor = 5_000_000_00, annualRateBp = 1150, termMonths = 240))

        assertEquals(240, schedule.size)
        assertEquals(5_000_000_00L, schedule.sumOf { it.principalMinor })
        assertEquals(0L, schedule.last().balanceAfterMinor)
        schedule.forEach { assertEquals(it.paymentMinor, it.interestMinor + it.principalMinor) }
    }

    @Test
    fun everyLoanTypeNowProducesASchedule() {
        // Replaces the old "unimplemented types fail loudly" guard: all three are in.
        listOf(LoanType.ANNUITY, LoanType.DIFFERENTIATED, LoanType.INSTALLMENT).forEach { type ->
            val rate = if (type == LoanType.INSTALLMENT) 0 else 1200
            val schedule = generateSchedule(
                Loan(type, 120_000_00, rate, LocalDate.of(2026, 1, 15), 12, 15),
            )

            assertEquals("$type", 12, schedule.size)
            assertEquals("$type", 0L, schedule.last().balanceAfterMinor)
            assertEquals("$type", 120_000_00L, schedule.sumOf { it.principalMinor })
        }
    }
}
