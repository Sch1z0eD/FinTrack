package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A first payment that does not fall one month after the loan.
 *
 * Banks routinely set it 30-60 days out, which is how a contract can read "срок 37
 * месяцев" while listing 36 payments. Without modelling it the whole schedule sits a month
 * early and the opening period is short by a month of interest.
 */
class FirstPaymentDateTest {

    private val start = LocalDate.of(2026, 4, 18)

    private fun loan(firstPayment: LocalDate? = null, rate: Int = 12_000) = Loan(
        type = LoanType.ANNUITY,
        principalMinor = 136_000_00,
        annualRateMilliPercent = rate,
        startDate = start,
        termMonths = 36,
        paymentDay = 17,
        firstPaymentDate = firstPayment,
    )

    @Test
    fun `without it the first payment lands a month after the loan`() {
        assertEquals(LocalDate.of(2026, 5, 17), generateSchedule(loan()).first().date)
    }

    @Test
    fun `with it the schedule starts on the given date and stays monthly`() {
        val schedule = generateSchedule(loan(LocalDate.of(2026, 6, 17)))

        assertEquals(LocalDate.of(2026, 6, 17), schedule[0].date)
        assertEquals(LocalDate.of(2026, 7, 17), schedule[1].date)
        assertEquals(36, schedule.size)
        // 36 payments from June 2026 run out in May 2029 rather than April.
        assertEquals(LocalDate.of(2029, 5, 17), schedule.last().date)
    }

    /**
     * The point of the feature: the opening period runs from the loan date, so a two-month
     * gap earns about two months of interest. Asserted as a relationship rather than a
     * figure - a hand-computed number here would only restate what the code does.
     */
    @Test
    fun `a longer opening period earns more interest and costs more overall`() {
        val normal = generateSchedule(loan())
        val shifted = generateSchedule(loan(LocalDate.of(2026, 6, 17)))

        assertTrue(
            "opening interest should roughly double over a two-month period",
            shifted.first().interestMinor > normal.first().interestMinor * 3 / 2,
        )
        assertTrue(
            "a longer opening period must cost more, not less",
            shifted.sumOf { it.interestMinor } > normal.sumOf { it.interestMinor },
        )
    }

    @Test
    fun `a first payment on or before the loan date is refused`() {
        assertThrows(IllegalArgumentException::class.java) { loan(start) }
        assertThrows(IllegalArgumentException::class.java) { loan(start.minusDays(1)) }
    }

    /** The day still comes from paymentDay, so short months clamp as they always did. */
    @Test
    fun `a 31st payment day still clamps in short months`() {
        val schedule = generateSchedule(
            Loan(
                type = LoanType.ANNUITY,
                principalMinor = 100_000_00,
                annualRateMilliPercent = 12_000,
                startDate = LocalDate.of(2026, 1, 15),
                termMonths = 3,
                paymentDay = 31,
                firstPaymentDate = LocalDate.of(2026, 3, 31),
            ),
        )
        assertEquals(LocalDate.of(2026, 3, 31), schedule[0].date)
        assertEquals(LocalDate.of(2026, 4, 30), schedule[1].date)
        assertEquals(LocalDate.of(2026, 5, 31), schedule[2].date)
    }
}
