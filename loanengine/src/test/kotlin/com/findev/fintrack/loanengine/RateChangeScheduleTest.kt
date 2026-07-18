package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RateChangeScheduleTest {

    private val start = LocalDate.of(2027, 1, 15)

    private fun loan(
        type: LoanType = LoanType.ANNUITY,
        principalMinor: Long = 1_000_000_00,
        annualRateBp: Int = 1200,
        termMonths: Int = 12,
    ) = Loan(type, principalMinor, annualRateBp, start, termMonths, paymentDay = 15)

    @Test
    fun rateChangeMidPeriodSplitsThatPeriodsInterest() {
        // 15.01.2027 -> 15.02.2027 is 31 days, base 365. Rate rises to 15% on 01.02:
        //   17 days at 12%: 100000000 * 1200 * 17 / (10000 * 365) =   558 904,109...
        //   14 days at 15%: 100000000 * 1500 * 14 / (10000 * 365) =   575 342,465...
        //                                              total       = 1 134 246,575 -> 1 134 247
        val interest = exactInterestWithRateChanges(
            balanceMinor = 1_000_000_00,
            baseRateBp = 1200,
            sortedChanges = listOf(RateChange(LocalDate.of(2027, 2, 1), 1500)),
            from = LocalDate.of(2027, 1, 15),
            to = LocalDate.of(2027, 2, 15),
        ).toKopecksHalfUp()

        assertEquals(1_134_247L, interest)
    }

    @Test
    fun splitInterestSitsBetweenBothFlatRates() {
        val from = LocalDate.of(2027, 1, 15)
        val to = LocalDate.of(2027, 2, 15)
        val changes = listOf(RateChange(LocalDate.of(2027, 2, 1), 1500))

        val mixed = exactInterestWithRateChanges(1_000_000_00, 1200, changes, from, to).toKopecksHalfUp()
        val allOld = exactInterest(1_000_000_00, 1200, from, to).toKopecksHalfUp()
        val allNew = exactInterest(1_000_000_00, 1500, from, to).toKopecksHalfUp()

        // Proves the period really was split rather than one rate winning outright.
        assertTrue(mixed > allOld)
        assertTrue(mixed < allNew)
    }

    @Test
    fun noChangesBehavesExactlyLikeAFlatRate() {
        assertEquals(
            generateSchedule(loan()),
            generateSchedule(loan(), rateChanges = emptyList()),
        )
    }

    @Test
    fun annuityPaymentIsResizedFromTheNextPaymentAfterTheChange() {
        // Change lands mid-period (01.02), between payment 1 (15.02) and its period start.
        val schedule = generateSchedule(loan(), listOf(RateChange(LocalDate.of(2027, 2, 1), 1500)))

        // Payment 1's period began on 15.01 at the old rate, so its size is unchanged...
        assertEquals(88_848_79L, schedule[0].paymentMinor)
        // ...but its interest already feels the new rate for part of the period.
        assertEquals(1_134_247L, schedule[0].interestMinor)

        // From payment 2 the tail is recomputed at 15% over the 11 remaining months.
        assertNotEquals(schedule[0].paymentMinor, schedule[1].paymentMinor)
        assertTrue(schedule[1].paymentMinor > schedule[0].paymentMinor)
        // ...and then it is flat again to the end (bar the adjusting final one).
        assertEquals(1, schedule.drop(1).dropLast(1).map { it.paymentMinor }.distinct().size)
    }

    @Test
    fun rateRiseCostsMoreInterestOverall() {
        val flat = generateSchedule(loan()).sumOf { it.interestMinor }
        val rising = generateSchedule(loan(), listOf(RateChange(LocalDate.of(2027, 7, 15), 2000)))
            .sumOf { it.interestMinor }

        assertTrue("rising=$rising should cost more than flat=$flat", rising > flat)
    }

    @Test
    fun rateCutSavesInterest() {
        val flat = generateSchedule(loan()).sumOf { it.interestMinor }
        val falling = generateSchedule(loan(), listOf(RateChange(LocalDate.of(2027, 7, 15), 600)))
            .sumOf { it.interestMinor }

        assertTrue("falling=$falling should cost less than flat=$flat", falling < flat)
    }

    @Test
    fun theLoanStillReconcilesAfterAChange() {
        val schedule = generateSchedule(loan(), listOf(RateChange(LocalDate.of(2027, 7, 15), 2000)))

        assertEquals(1_000_000_00L, schedule.sumOf { it.principalMinor })
        assertEquals(0L, schedule.last().balanceAfterMinor)
        schedule.forEach {
            assertEquals(it.paymentMinor, it.interestMinor + it.principalMinor + it.feeMinor)
        }
    }

    @Test
    fun severalChangesEachResizeTheTail() {
        val schedule = generateSchedule(
            loan(),
            listOf(
                RateChange(LocalDate.of(2027, 4, 15), 1800),
                RateChange(LocalDate.of(2027, 8, 15), 900),
            ),
        )

        val sizes = schedule.dropLast(1).map { it.paymentMinor }.distinct()
        // Three flat stretches: 12% -> 18% -> 9%.
        assertEquals(3, sizes.size)
        assertEquals(1_000_000_00L, schedule.sumOf { it.principalMinor })
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun changesOutsideTheTermDoNothing() {
        val afterTheEnd = generateSchedule(loan(), listOf(RateChange(LocalDate.of(2030, 1, 1), 3000)))

        assertEquals(generateSchedule(loan()), afterTheEnd)
    }

    @Test
    fun aChangeDatedBeforeTheStartActsAsTheOpeningRate() {
        val schedule = generateSchedule(loan(), listOf(RateChange(LocalDate.of(2026, 1, 1), 600)))

        // The whole loan runs at 6%: same as if it had been written that way.
        assertEquals(generateSchedule(loan(annualRateBp = 600)), schedule)
    }

    @Test
    fun differentiatedKeepsItsSlicesAndOnlyTheInterestMoves() {
        val flat = generateSchedule(loan(type = LoanType.DIFFERENTIATED))
        val changed = generateSchedule(
            loan(type = LoanType.DIFFERENTIATED),
            listOf(RateChange(LocalDate.of(2027, 7, 15), 2000)),
        )

        // Principal slices are fixed by the term, so nothing about them may move...
        assertEquals(flat.map { it.principalMinor }, changed.map { it.principalMinor })
        assertEquals(flat.map { it.balanceAfterMinor }, changed.map { it.balanceAfterMinor })
        // ...only the interest.
        assertTrue(changed.sumOf { it.interestMinor } > flat.sumOf { it.interestMinor })
    }

    @Test
    fun instalmentPlanRejectsRateChanges() {
        val instalment = Loan(LoanType.INSTALLMENT, 60_000_00, 0, start, 6, 15)

        try {
            generateSchedule(instalment, listOf(RateChange(LocalDate.of(2027, 3, 1), 1000)))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // A 0% plan with a rate change is a contradiction, not a schedule.
        }
    }

    @Test
    fun rateOnPicksTheLatestChangeInForce() {
        val changes = listOf(
            RateChange(LocalDate.of(2027, 4, 15), 1800),
            RateChange(LocalDate.of(2027, 8, 15), 900),
        )

        assertEquals(1200, rateOn(1200, changes, LocalDate.of(2027, 4, 14)))
        // Effective from is inclusive: the change bites on its own date.
        assertEquals(1800, rateOn(1200, changes, LocalDate.of(2027, 4, 15)))
        assertEquals(1800, rateOn(1200, changes, LocalDate.of(2027, 8, 14)))
        assertEquals(900, rateOn(1200, changes, LocalDate.of(2027, 8, 15)))
    }
}
