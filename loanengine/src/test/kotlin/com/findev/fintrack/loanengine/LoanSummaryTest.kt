package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoanSummaryTest {

    private val start = LocalDate.of(2027, 1, 15)

    private fun instalment(upfrontFeeMinor: Long = 0, monthlyFeeMinor: Long = 0) = Loan(
        type = LoanType.INSTALLMENT,
        principalMinor = 60_000_00,
        annualRateMilliPercent = 0,
        startDate = start,
        termMonths = 6,
        paymentDay = 15,
        upfrontFeeMinor = upfrontFeeMinor,
        monthlyFeeMinor = monthlyFeeMinor,
    )

    private fun annuity() = Loan(LoanType.ANNUITY, 1_000_000_00, 12000, start, 12, paymentDay = 15)

    @Test
    fun anHonestInstalmentCostsNothingExtra() {
        val loan = instalment()
        val summary = summarize(loan, generateSchedule(loan))

        assertEquals(60_000_00L, summary.principalMinor)
        assertEquals(0L, summary.totalInterestMinor)
        assertEquals(0L, summary.totalFeesMinor)
        assertEquals(0L, summary.overpaymentMinor)
        assertEquals(60_000_00L, summary.totalPaidMinor)
        assertEquals(6, summary.paymentCount)
        assertEquals(LocalDate.of(2027, 7, 15), summary.closingDate)
    }

    @Test
    fun upfrontFeeCountsAsOverpaymentEvenThoughNoPaymentCarriesIt() {
        // This is the whole reason the up-front fee exists in the model: a "0%" plan
        // that charges 2 000 руб. at signing is not free, and the summary must say so.
        val loan = instalment(upfrontFeeMinor = 2_000_00)
        val schedule = generateSchedule(loan)
        val summary = summarize(loan, schedule)

        assertTrue("must not leak into the schedule", schedule.all { it.feeMinor == 0L })
        assertEquals(2_000_00L, summary.totalFeesMinor)
        assertEquals(2_000_00L, summary.overpaymentMinor)
        assertEquals(62_000_00L, summary.totalPaidMinor)
    }

    @Test
    fun bothFeesAddUpInTheOverpayment() {
        // 6 x 500 руб. ежемесячно + 2 000 руб. разово = 5 000 руб.
        val loan = instalment(upfrontFeeMinor = 2_000_00, monthlyFeeMinor = 500_00)
        val summary = summarize(loan, generateSchedule(loan))

        assertEquals(5_000_00L, summary.totalFeesMinor)
        assertEquals(5_000_00L, summary.overpaymentMinor)
        assertEquals(65_000_00L, summary.totalPaidMinor)
    }

    @Test
    fun totalPaidIsAlwaysPrincipalPlusOverpayment() {
        // Enforced by LoanSummary itself, but assert it over real loans: this is the
        // check that would catch a kopeck going missing anywhere in the engine.
        listOf(
            annuity() to emptyList<Prepayment>(),
            annuity() to listOf(Prepayment(LocalDate.of(2027, 5, 20), 200_000_00, PrepaymentMode.REDUCE_TERM)),
            instalment(2_000_00, 500_00) to emptyList(),
        ).forEach { (loan, prepayments) ->
            val summary = summarize(loan, generateSchedule(loan, prepayments = prepayments))

            assertEquals(
                summary.totalPaidMinor,
                summary.principalMinor + summary.totalInterestMinor + summary.totalFeesMinor,
            )
            assertEquals(1_000_000_00L.coerceAtMost(summary.principalMinor), summary.principalMinor)
        }
    }

    @Test
    fun theSummaryAlwaysAccountsForTheWholeLoan() {
        val loan = annuity()
        val summary = summarize(loan, generateSchedule(loan))

        assertEquals(loan.principalMinor, summary.principalMinor)
    }

    @Test
    fun prepayingShortensTheClosingDateAndCutsTheOverpayment() {
        val loan = annuity()
        val plain = summarize(loan, generateSchedule(loan))
        val prepaid = summarize(
            loan,
            generateSchedule(
                loan,
                prepayments = listOf(
                    Prepayment(LocalDate.of(2027, 4, 15), 400_000_00, PrepaymentMode.REDUCE_TERM),
                ),
            ),
        )

        assertTrue(prepaid.closingDate < plain.closingDate)
        assertTrue(prepaid.paymentCount < plain.paymentCount)
        assertTrue(prepaid.overpaymentMinor < plain.overpaymentMinor)
        // The debt itself does not change - only what it costs.
        assertEquals(plain.principalMinor, prepaid.principalMinor)
    }

    @Test
    fun reducePaymentKeepsTheClosingDate() {
        val loan = annuity()
        val plain = summarize(loan, generateSchedule(loan))
        val prepaid = summarize(
            loan,
            generateSchedule(
                loan,
                prepayments = listOf(
                    Prepayment(LocalDate.of(2027, 4, 15), 400_000_00, PrepaymentMode.REDUCE_PAYMENT),
                ),
            ),
        )

        assertEquals(plain.closingDate, prepaid.closingDate)
        assertEquals(plain.paymentCount, prepaid.paymentCount)
        assertTrue(prepaid.overpaymentMinor < plain.overpaymentMinor)
    }

    @Test
    fun balanceOnWalksTheScheduleByPaymentDate() {
        val loan = annuity()
        val schedule = generateSchedule(loan)
        fun on(y: Int, m: Int, d: Int) = balanceOn(loan, schedule, emptyList(), LocalDate.of(y, m, d))

        // Before anything is paid the whole loan is owed.
        assertEquals(1_000_000_00L, on(2027, 1, 15))
        assertEquals(1_000_000_00L, on(2027, 2, 14))
        // On a payment date the balance is the one that payment left behind.
        assertEquals(schedule[0].balanceAfterMinor, on(2027, 2, 15))
        // Between payments it simply holds.
        assertEquals(schedule[0].balanceAfterMinor, on(2027, 3, 14))
        assertEquals(schedule[1].balanceAfterMinor, on(2027, 3, 15))
        // Once it is over, nothing is owed - forever.
        assertEquals(0L, on(2028, 1, 15))
        assertEquals(0L, on(2030, 1, 1))
    }

    /**
     * Money paid is money gone, the same day it leaves - not once the next payment date
     * happens to come round. The schedule cannot say this on its own: it only reports
     * balances on payment dates, so between them it is stale by exactly the prepayments
     * made since.
     */
    @Test
    fun balanceOnDropsTheDayAPrepaymentIsMade() {
        val loan = annuity()
        val prepayments = listOf(
            Prepayment(LocalDate.of(2027, 3, 1), 500_000_00, PrepaymentMode.REDUCE_TERM),
        )
        val schedule = generateSchedule(loan, prepayments = prepayments)
        fun on(y: Int, m: Int, d: Int) = balanceOn(loan, schedule, prepayments, LocalDate.of(y, m, d))

        val dayBefore = on(2027, 2, 28)
        val dayOf = on(2027, 3, 1)
        val dayAfter = on(2027, 3, 10)

        assertEquals(schedule[0].balanceAfterMinor, dayBefore)
        assertEquals(dayBefore - 500_000_00, dayOf)
        assertEquals(dayOf, dayAfter)
        // And once the period closes the schedule agrees, having charged its interest.
        assertEquals(schedule[1].balanceAfterMinor, on(2027, 3, 15))
    }

    /** A prepayment on the payment day is already inside that entry - never twice. */
    @Test
    fun aPrepaymentOnThePaymentDayIsNotSubtractedAgain() {
        val loan = annuity()
        val prepayments = listOf(
            Prepayment(LocalDate.of(2027, 3, 15), 500_000_00, PrepaymentMode.REDUCE_TERM),
        )
        val schedule = generateSchedule(loan, prepayments = prepayments)

        assertEquals(
            schedule[1].balanceAfterMinor,
            balanceOn(loan, schedule, prepayments, LocalDate.of(2027, 3, 15)),
        )
    }

    /** Overpaying cannot push the debt below zero. */
    @Test
    fun balanceOnNeverGoesNegative() {
        val loan = annuity()
        val prepayments = listOf(
            Prepayment(LocalDate.of(2027, 3, 1), 5_000_000_00, PrepaymentMode.REDUCE_TERM),
        )
        val schedule = generateSchedule(loan, prepayments = prepayments)

        assertEquals(0L, balanceOn(loan, schedule, prepayments, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun summaryRejectsAnEmptySchedule() {
        assertThrows(IllegalArgumentException::class.java) {
            summarize(annuity(), emptyList())
        }
    }

    @Test
    fun overpaymentIsNotAllowedToDisagreeWithItsParts() {
        // The model refuses an inconsistent summary outright.
        assertThrows(IllegalArgumentException::class.java) {
            LoanSummary(
                principalMinor = 100_000,
                totalInterestMinor = 5_000,
                totalFeesMinor = 1_000,
                totalPaidMinor = 106_000,
                overpaymentMinor = 5_999, // should be 6 000
                closingDate = start,
                paymentCount = 1,
            )
        }
    }
}
