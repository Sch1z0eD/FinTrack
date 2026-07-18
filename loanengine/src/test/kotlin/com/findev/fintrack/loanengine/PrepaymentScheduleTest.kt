package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrepaymentScheduleTest {

    private val start = LocalDate.of(2027, 1, 15)

    /** 120 000 руб. на 12 месяцев под 0%: ровно 10 000 руб. тела в месяц, считается в уме. */
    private fun interestFree(type: LoanType = LoanType.INSTALLMENT) =
        Loan(type, 120_000_00, 0, start, 12, paymentDay = 15)

    private fun annuity(rateMilliPercent: Int = 12000) =
        Loan(LoanType.ANNUITY, 1_000_000_00, rateMilliPercent, start, 12, paymentDay = 15)

    @Test
    fun reduceTermKeepsThePaymentAndEndsTheLoanEarlier() {
        // 10 000 руб. досрочно 15.03 (конец 2-го периода). Долг гасится на месяц раньше:
        // период 1: 10 000, период 2: 10 000 + 10 000 досрочно, далее 9 x 10 000 = 90 000.
        // Итого 12 платежей тела за 11 периодов.
        val schedule = generateSchedule(
            interestFree(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 3, 15), 10_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        assertEquals("term should shrink by one month", 11, schedule.size)
        assertEquals(LocalDate.of(2027, 12, 15), schedule.last().date)
        // The payment itself never moved.
        assertTrue(schedule.all { it.principalMinor == 10_000_00L })
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun reducePaymentKeepsTheEndDateAndShrinksThePayment() {
        // Same 10 000 руб. on 15.03, but now the tail is re-sliced: after period 2 the
        // debt is 90 000 over the 10 payments left = 9 000 руб. each.
        val schedule = generateSchedule(
            interestFree(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 3, 15), 10_000_00, PrepaymentMode.REDUCE_PAYMENT),
            ),
        )

        assertEquals("end date must not move", 12, schedule.size)
        assertEquals(LocalDate.of(2028, 1, 15), schedule.last().date)
        assertEquals(10_000_00L, schedule[0].principalMinor)
        assertEquals(10_000_00L, schedule[1].principalMinor)
        assertTrue(schedule.drop(2).all { it.principalMinor == 9_000_00L })
        assertEquals(0L, schedule.last().balanceAfterMinor)
    }

    @Test
    fun principalAndPrepaymentsTogetherRepayExactlyTheLoan() {
        // The invariant that catches any leak: nothing paid, nothing lost.
        listOf(PrepaymentMode.REDUCE_TERM, PrepaymentMode.REDUCE_PAYMENT).forEach { mode ->
            val schedule = generateSchedule(
                annuity(),
                prepayments = listOf(
                    Prepayment(LocalDate.of(2027, 4, 20), 200_000_00, mode),
                    Prepayment(LocalDate.of(2027, 8, 3), 150_000_00, mode),
                ),
            )

            val repaid = schedule.sumOf { it.principalMinor } + schedule.sumOf { it.prepaymentMinor }
            assertEquals("$mode", 1_000_000_00L, repaid)
            assertEquals("$mode", 0L, schedule.last().balanceAfterMinor)
        }
    }

    @Test
    fun bothModesSaveInterestAndReduceTermSavesMore() {
        val plain = generateSchedule(annuity()).sumOf { it.interestMinor }
        val reduceTerm = generateSchedule(
            annuity(),
            prepayments = listOf(Prepayment(LocalDate.of(2027, 4, 15), 300_000_00, PrepaymentMode.REDUCE_TERM)),
        ).sumOf { it.interestMinor }
        val reducePayment = generateSchedule(
            annuity(),
            prepayments = listOf(Prepayment(LocalDate.of(2027, 4, 15), 300_000_00, PrepaymentMode.REDUCE_PAYMENT)),
        ).sumOf { it.interestMinor }

        assertTrue("prepaying must save interest", reduceTerm < plain)
        assertTrue("prepaying must save interest", reducePayment < plain)
        // The textbook result, and the whole point of the simulator in Этап 3:
        // shortening the term beats shrinking the payment.
        assertTrue(
            "reduceTerm=$reduceTerm should beat reducePayment=$reducePayment",
            reduceTerm < reducePayment,
        )
    }

    @Test
    fun prepaymentMidPeriodSplitsThatPeriodsInterest() {
        // 15.01 -> 15.02, 1 000 000 руб. под 12%, 500 000 руб. досрочно 01.02:
        //   17 дней на 1 000 000: 100000000 * 12000 * 17 / (100000 * 365) = 558 904,109...
        //   14 дней на   500 000:  50000000 * 12000 * 14 / (100000 * 365) = 230 136,986...
        //                                                 итого 789 041,095 -> 789 041
        val schedule = generateSchedule(
            annuity(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 2, 1), 500_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        assertEquals(789_041L, schedule[0].interestMinor)
        // Against 1 019 178 with no prepayment: the days after it accrue on less debt.
        assertTrue(schedule[0].interestMinor < 1_019_178L)
        assertEquals(500_000_00L, schedule[0].prepaymentMinor)
    }

    @Test
    fun prepaymentIsCappedAtTheOutstandingDebt() {
        // Throwing money at a nearly-repaid loan must not create a negative balance.
        // 500 000 руб. досрочно 15.03, в дату платежа: сперва плановые 10 000 руб.
        // (остаток 100 000), и только излишек гасит долг - значит применится 100 000.
        val schedule = generateSchedule(
            interestFree(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 3, 15), 500_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        assertEquals(0L, schedule.last().balanceAfterMinor)
        assertEquals(100_000_00L, schedule.sumOf { it.prepaymentMinor })
        assertEquals(20_000_00L, schedule.sumOf { it.principalMinor })
        assertEquals(120_000_00L, schedule.sumOf { it.principalMinor + it.prepaymentMinor })
    }

    @Test
    fun aPrepaymentOnThePaymentDayLandsAfterTheScheduledPayment() {
        // This is how a bank processes it: the planned payment is taken first, the
        // surplus goes against the principal. Applying it the other way round would
        // swallow that month's scheduled payment.
        val schedule = generateSchedule(
            interestFree(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 2, 15), 30_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        val first = schedule.first()
        assertEquals(10_000_00L, first.principalMinor)
        assertEquals(30_000_00L, first.prepaymentMinor)
        // 120 000 - 10 000 planned - 30 000 early = 80 000 left.
        assertEquals(80_000_00L, first.balanceAfterMinor)
    }

    @Test
    fun aMidPeriodPrepaymentThatClearsTheDebtLeavesOnlyInterestDue() {
        // Cleared on 01.02, before the first payment date: there is no principal left
        // to pay on 15.02, but the 17 days the money was owed still earned interest.
        //   17 дней на 1 000 000: 100000000 * 12000 * 17 / (100000 * 365) = 558 904,109 -> 558 904
        val schedule = generateSchedule(
            annuity(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 2, 1), 2_000_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        assertEquals(1, schedule.size)
        val only = schedule.single()
        assertEquals(0L, only.balanceAfterMinor)
        assertEquals(0L, only.principalMinor)
        assertEquals(558_904L, only.interestMinor)
        assertEquals(558_904L, only.paymentMinor)
        assertEquals(1_000_000_00L, only.prepaymentMinor)
    }

    @Test
    fun prepaymentIsReportedOnItsOwnRowAndNotInThePayment() {
        val schedule = generateSchedule(
            interestFree(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 3, 15), 10_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        val withPrepayment = schedule.single { it.prepaymentMinor > 0 }
        assertEquals(LocalDate.of(2027, 3, 15), withPrepayment.date)
        // The extra money is not part of the scheduled payment - it is on top of it.
        assertEquals(10_000_00L, withPrepayment.paymentMinor)
        assertEquals(10_000_00L, withPrepayment.prepaymentMinor)
    }

    @Test
    fun differentiatedReduceTermAlsoEndsEarly() {
        val schedule = generateSchedule(
            interestFree(LoanType.DIFFERENTIATED),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 3, 15), 20_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        assertEquals(10, schedule.size)
        assertEquals(120_000_00L, schedule.sumOf { it.principalMinor + it.prepaymentMinor })
    }

    @Test
    fun severalPrepaymentsInOnePeriodAllApply() {
        val schedule = generateSchedule(
            interestFree(),
            prepayments = listOf(
                Prepayment(LocalDate.of(2027, 2, 20), 5_000_00, PrepaymentMode.REDUCE_TERM),
                Prepayment(LocalDate.of(2027, 3, 1), 5_000_00, PrepaymentMode.REDUCE_TERM),
            ),
        )

        assertEquals(10_000_00L, schedule.single { it.prepaymentMinor > 0 }.prepaymentMinor)
        assertEquals(11, schedule.size)
        assertEquals(120_000_00L, schedule.sumOf { it.principalMinor + it.prepaymentMinor })
    }

    @Test
    fun prepaymentBeforeTheLoanStartsIsRejected() {
        try {
            generateSchedule(
                annuity(),
                prepayments = listOf(
                    Prepayment(LocalDate.of(2026, 12, 1), 1_000_00, PrepaymentMode.REDUCE_TERM),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Money cannot be repaid on a loan that does not exist yet.
        }
    }

    @Test
    fun prepaymentsAndRateChangesWorkTogether() {
        val schedule = generateSchedule(
            annuity(),
            rateChanges = listOf(RateChange(LocalDate.of(2027, 5, 1), 18000)),
            prepayments = listOf(Prepayment(LocalDate.of(2027, 5, 20), 200_000_00, PrepaymentMode.REDUCE_PAYMENT)),
        )

        assertEquals(1_000_000_00L, schedule.sumOf { it.principalMinor + it.prepaymentMinor })
        assertEquals(0L, schedule.last().balanceAfterMinor)
        schedule.forEach {
            assertEquals(it.paymentMinor, it.interestMinor + it.principalMinor + it.feeMinor)
        }
    }
}
