package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InstallmentScheduleTest {

    private fun instalment(
        principalMinor: Long = 60_000_00,
        termMonths: Int = 6,
        upfrontFeeMinor: Long = 0,
        monthlyFeeMinor: Long = 0,
        startDate: LocalDate = LocalDate.of(2026, 1, 15),
        paymentDay: Int = 15,
    ) = Loan(
        type = LoanType.INSTALLMENT,
        principalMinor = principalMinor,
        annualRateMilliPercent = 0,
        startDate = startDate,
        termMonths = termMonths,
        paymentDay = paymentDay,
        upfrontFeeMinor = upfrontFeeMinor,
        monthlyFeeMinor = monthlyFeeMinor,
    )

    @Test
    fun plainInstalmentIsThePriceSplitEvenly() {
        // 60 000 руб. на 6 месяцев без комиссий = ровно 10 000 руб. в месяц, без процентов.
        val schedule = generateSchedule(instalment())

        assertEquals(6, schedule.size)
        assertTrue(schedule.all { it.interestMinor == 0L })
        assertTrue(schedule.all { it.feeMinor == 0L })
        assertTrue(schedule.all { it.paymentMinor == 10_000_00L })
        assertEquals(60_000_00L, schedule.sumOf { it.principalMinor })
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun monthlyFeeRidesOnTopOfEveryPayment() {
        // 60 000 / 6 = 10 000 руб. тела + 500 руб. комиссии = 10 500 руб. в месяц.
        val schedule = generateSchedule(instalment(monthlyFeeMinor = 500_00))

        assertTrue(schedule.all { it.paymentMinor == 10_500_00L })
        assertTrue(schedule.all { it.principalMinor == 10_000_00L })
        assertTrue(schedule.all { it.feeMinor == 500_00L })
        // The fee must not be mistaken for principal: the debt is still just the price.
        assertEquals(60_000_00L, schedule.sumOf { it.principalMinor })
        assertEquals(3_000_00L, schedule.sumOf { it.feeMinor })
    }

    @Test
    fun feeNeverPaysDownTheDebt() {
        val withFee = generateSchedule(instalment(monthlyFeeMinor = 500_00))
        val withoutFee = generateSchedule(instalment())

        // Same debt, same slices, same closing date - the fee only costs more money.
        assertEquals(
            withoutFee.map { it.balanceAfterMinor },
            withFee.map { it.balanceAfterMinor },
        )
        assertEquals(withoutFee.map { it.date }, withFee.map { it.date })
    }

    @Test
    fun upfrontFeeStaysOutOfTheSchedule() {
        // It is paid at origination, not on a payment date. It belongs to the
        // overpayment aggregate, and must not silently inflate a monthly payment.
        val schedule = generateSchedule(instalment(upfrontFeeMinor = 2_000_00))

        assertTrue(schedule.all { it.feeMinor == 0L })
        assertTrue(schedule.all { it.paymentMinor == 10_000_00L })
        assertEquals(60_000_00L, schedule.sumOf { it.principalMinor })
    }

    @Test
    fun bothFeesTogether() {
        val loan = instalment(upfrontFeeMinor = 2_000_00, monthlyFeeMinor = 500_00)
        val schedule = generateSchedule(loan)

        assertTrue(schedule.all { it.paymentMinor == 10_500_00L })
        // Real cost of the plan: 6 * 500 monthly + 2 000 upfront = 5 000 руб.
        val paidInFees = schedule.sumOf { it.feeMinor } + loan.upfrontFeeMinor
        assertEquals(5_000_00L, paidInFees)
    }

    @Test
    fun everyRowSplitsIntoPrincipalInterestAndFee() {
        generateSchedule(instalment(monthlyFeeMinor = 500_00)).forEach { entry ->
            assertEquals(
                entry.paymentMinor,
                entry.interestMinor + entry.principalMinor + entry.feeMinor,
            )
        }
    }

    @Test
    fun roundingRemainderLandsOnTheLastPayment() {
        // 1000 руб. на 3 месяца: 333,33 / 333,33 / 333,34.
        val schedule = generateSchedule(instalment(principalMinor = 100_000, termMonths = 3))

        assertEquals(listOf(33_333L, 33_333L, 33_334L), schedule.map { it.principalMinor })
        assertEquals(100_000L, schedule.sumOf { it.principalMinor })
    }

    @Test
    fun instalmentPlanRefusesToCarryARate() {
        // Guarded in the model: a "0%" plan with a rate is a contradiction.
        try {
            Loan(LoanType.INSTALLMENT, 60_000_00, 1, LocalDate.of(2026, 1, 15), 6, 15)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun monthlyFeeAlsoWorksOnAnInterestBearingLoan() {
        // Fees are not an instalment-only idea; a normal loan can carry servicing too.
        val schedule = generateSchedule(
            Loan(
                type = LoanType.ANNUITY,
                principalMinor = 1_000_000_00,
                annualRateMilliPercent = 12000,
                startDate = LocalDate.of(2026, 1, 15),
                termMonths = 12,
                paymentDay = 15,
                monthlyFeeMinor = 300_00,
            ),
        )

        // The annuity part is unchanged (88 848,79 руб.), the fee sits on top.
        assertEquals(88_848_79L + 300_00L, schedule.first().paymentMinor)
        assertEquals(300_00L, schedule.first().feeMinor)
        assertEquals(1_000_000_00L, schedule.sumOf { it.principalMinor })
    }
}
