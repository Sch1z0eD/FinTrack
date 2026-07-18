package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A payment too small to cover its own period's interest.
 *
 * Checked against a real Т-Банк contract: 136 000 ₽ at 39.9%, 36 payments of 6 740,00 ₽,
 * signed 18.04.2026 with the first payment on 17.06.2026. That opening period is two months
 * long and earns more than one instalment, and the bank does not treat it as an error - the
 * whole payment goes to interest, the balance does not move, and the shortfall waits for
 * the next payment. The engine used to refuse the schedule outright, which made this
 * contract impossible to enter.
 *
 * The convention was established from that contract's own printed schedule, where six
 * consecutive rows reproduce to the kopeck under ACT/365 with interest running from the
 * previous payment date. The figures below are computed by hand from that rule, not taken
 * from this engine's output.
 */
class UnpaidInterestCarryTest {

    private val loan = Loan(
        type = LoanType.ANNUITY,
        principalMinor = 136_000_00,
        annualRateMilliPercent = 39_900,
        startDate = LocalDate.of(2026, 4, 18),
        termMonths = 36,
        paymentDay = 17,
        fixedPaymentMinor = 6_740_00,
        firstPaymentDate = LocalDate.of(2026, 6, 17),
    )

    /**
     * 18.04 -> 17.06 is 60 days: 136 000 × 39.9% × 60 / 365 = 8 920,11, against a payment
     * of 6 740,00. The balance must not move, and nothing may be added to it either - the
     * shortfall is carried, not capitalised.
     */
    @Test
    fun `an opening payment smaller than its interest pays no principal`() {
        val first = generateSchedule(loan).first()

        assertEquals(6_740_00L, first.paymentMinor)
        assertEquals(6_740_00L, first.interestMinor)
        assertEquals(0L, first.principalMinor)
        assertEquals(136_000_00L, first.balanceAfterMinor)
    }

    /**
     * 17.06 -> 17.07 is 30 days on an unchanged 136 000: 4 460,05. Plus the 2 180,11 left
     * over from the first payment, that is 6 640,16 owed, so 99,84 of the 6 740,00 finally
     * reaches the principal.
     */
    @Test
    fun `the shortfall is cleared by the next payment before any principal`() {
        val second = generateSchedule(loan)[1]

        assertEquals(6_640_16L, second.interestMinor)
        assertEquals(99_84L, second.principalMinor)
        assertEquals(135_900_16L, second.balanceAfterMinor)
    }

    /** Carried, not capitalised: interest never earns interest of its own. */
    @Test
    fun `the carried interest does not grow the debt`() {
        val schedule = generateSchedule(loan)

        assertTrue(
            "the balance must never rise above the principal",
            schedule.all { it.balanceAfterMinor <= 136_000_00L },
        )
    }

    @Test
    fun `the loan still amortises to zero over its term`() {
        val schedule = generateSchedule(loan)

        assertEquals(36, schedule.size)
        assertEquals(0L, schedule.last().balanceAfterMinor)
        assertEquals(136_000_00L, schedule.sumOf { it.principalMinor })
    }

    /**
     * Every kopeck accrued is eventually charged - a carried shortfall must not be lost
     * along the way, which is the quiet failure this arrangement invites.
     */
    @Test
    fun `nothing is dropped between the carry and the final payment`() {
        val schedule = generateSchedule(loan)
        val charged = schedule.sumOf { it.interestMinor }

        // Total paid must equal principal plus every kopeck of interest charged.
        assertEquals(136_000_00L + charged, schedule.sumOf { it.paymentMinor })
    }
}
