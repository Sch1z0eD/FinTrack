package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The oracle here is a real signed contract, not a number this engine produced.
 *
 * ООО МФК «Совком Экспресс», договор от 05.07.2026: 102 300 руб. at 19.9% over 60 months,
 * payment on the 5th. Clause 6 defines the product: "Платеж по Договору включает проценты
 * за пользование займом, начисленные за прошедший отчетный период ..., за исключением
 * последнего платежа, который включает 100% ссудной задолженности".
 *
 * The contract's ПОЛНАЯ СТОИМОСТЬ ЗАЙМА is 101 788,59 руб., and this engine reproduces it
 * to the kopeck. That one number pins down all three money rules at once:
 *   - the basis is ACT/ACT, not ACT/365: over five years the two disagree by 55,68 руб.
 *     (ACT/365 would give 101 844,27), and 2028 being a leap year really is divided by 366;
 *   - rounding really is HALF_UP per period, not once at the end: rounding the whole term
 *     in one go gives 101 788,50, nine kopecks short of the contract;
 *   - the product really is interest-only with a balloon, per clause 6 above.
 * None of this is re-derived from the engine - it is a signed contract disagreeing or not.
 */
class InterestOnlyScheduleTest {

    /** The real contract. */
    private fun realLoan() = Loan(
        type = LoanType.INTEREST_ONLY,
        principalMinor = 102_300_00,
        annualRateMilliPercent = 19900,
        startDate = LocalDate.of(2026, 7, 5),
        termMonths = 60,
        paymentDay = 5,
    )

    /** The whole point of this file: the engine and the contract agree exactly. */
    @Test
    fun theTotalCostMatchesTheContractsFullCostOfCreditToTheKopeck() {
        val schedule = generateSchedule(realLoan())
        val summary = summarize(realLoan(), schedule)

        assertEquals(101_788_59L, summary.totalInterestMinor)
        assertEquals(101_788_59L, summary.overpaymentMinor)
        assertEquals(102_300_00L + 101_788_59L, summary.totalPaidMinor)
    }

    /** Nothing is repaid until the end - that is what makes this product what it is. */
    @Test
    fun theBalanceDoesNotMoveUntilTheFinalPayment() {
        val schedule = generateSchedule(realLoan())

        assertEquals(60, schedule.size)
        assertTrue(
            "every payment but the last is interest only",
            schedule.dropLast(1).all { it.principalMinor == 0L && it.balanceAfterMinor == 102_300_00L },
        )
        assertEquals(102_300_00L, schedule.last().principalMinor)
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    /**
     * 05.07 -> 05.08 is 31 days: 102300 x 19900 x 31 / (100000 x 365) = 1 729,01.
     * Hand-computed, and it is also why the payment is not level - a 28-day February
     * costs less than a 31-day August on the very same balance.
     */
    @Test
    fun eachPaymentIsTheInterestItsOwnPeriodEarned() {
        val schedule = generateSchedule(realLoan())

        assertEquals(1_729_01L, schedule[0].paymentMinor)
        assertEquals(1_729_01L, schedule[0].interestMinor)
        // 05.08 -> 05.09 is also 31 days, so the same again...
        assertEquals(1_729_01L, schedule[1].paymentMinor)
        // ...but 05.09 -> 05.10 is 30, and it costs less: 20357,70 x 30 / 365 = 1673,2356.
        assertEquals(1_673_24L, schedule[2].paymentMinor)
    }

    @Test
    fun theFinalPaymentIsTheWholePrincipalPlusItsOwnInterest() {
        val schedule = generateSchedule(realLoan())
        val last = schedule.last()

        assertEquals(LocalDate.of(2031, 7, 5), last.date)
        assertEquals(last.principalMinor + last.interestMinor, last.paymentMinor)
        assertTrue("the balloon dwarfs the rest", last.paymentMinor > 100_000_00L)
    }

    /**
     * A prepayment is the only thing that moves the balance, so it cuts every remaining
     * period's interest and shrinks the balloon. The mode makes no difference here:
     * there is no scheduled principal to re-slice and no fixed payment to re-derive.
     */
    @Test
    fun aPrepaymentShrinksEveryLaterPaymentAndTheBalloon() {
        val prepayment = Prepayment(LocalDate.of(2026, 8, 5), 50_000_00, PrepaymentMode.REDUCE_TERM)
        val base = generateSchedule(realLoan())
        val withPrepayment = generateSchedule(realLoan(), prepayments = listOf(prepayment))

        assertEquals(52_300_00L, withPrepayment[1].balanceAfterMinor)
        assertTrue(withPrepayment[2].interestMinor < base[2].interestMinor)
        assertEquals(52_300_00L, withPrepayment.last().principalMinor)

        val bothModes = generateSchedule(
            realLoan(),
            prepayments = listOf(prepayment.copy(mode = PrepaymentMode.REDUCE_PAYMENT)),
        )
        assertEquals(withPrepayment, bothModes)
    }
}
