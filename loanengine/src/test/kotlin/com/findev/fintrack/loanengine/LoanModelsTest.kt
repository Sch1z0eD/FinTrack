package com.findev.fintrack.loanengine

import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class LoanModelsTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 15)

    private fun loan(
        type: LoanType = LoanType.ANNUITY,
        principalMinor: Long = 1_000_000_00,
        annualRateBp: Int = 1690,
        termMonths: Int = 60,
        paymentDay: Int = 15,
    ) = Loan(type, principalMinor, annualRateBp, start, termMonths, paymentDay)

    @Test
    fun loanRejectsNonsenseInput() {
        assertThrows(IllegalArgumentException::class.java) { loan(principalMinor = 0) }
        assertThrows(IllegalArgumentException::class.java) { loan(principalMinor = -1) }
        assertThrows(IllegalArgumentException::class.java) { loan(annualRateBp = -1) }
        assertThrows(IllegalArgumentException::class.java) { loan(termMonths = 0) }
        assertThrows(IllegalArgumentException::class.java) { loan(paymentDay = 0) }
        assertThrows(IllegalArgumentException::class.java) { loan(paymentDay = 32) }
    }

    @Test
    fun instalmentPlanCannotCarryInterest() {
        // The whole point of рассрочка is 0%: the cost sits in fees, not a rate.
        assertThrows(IllegalArgumentException::class.java) {
            loan(type = LoanType.INSTALLMENT, annualRateBp = 1)
        }
        loan(type = LoanType.INSTALLMENT, annualRateBp = 0)
    }

    @Test
    fun zeroRateIsValidForARealLoan() {
        loan(annualRateBp = 0)
    }

    @Test
    fun scheduleEntryPaymentMustSplitExactly() {
        // Guards the core money rule: rounding must never lose a kopeck between the parts.
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleEntry(
                number = 1,
                date = start,
                paymentMinor = 10_000,
                interestMinor = 3_000,
                principalMinor = 6_999,
                feeMinor = 0,
                prepaymentMinor = 0,
                balanceAfterMinor = 100,
            )
        }

        ScheduleEntry(
            number = 1,
            date = start,
            paymentMinor = 10_000,
            interestMinor = 3_000,
            principalMinor = 7_000,
            feeMinor = 0,
            prepaymentMinor = 0,
            balanceAfterMinor = 100,
        )
    }

    @Test
    fun scheduleEntryRejectsNegativeBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleEntry(
                number = 1,
                date = start,
                paymentMinor = 10_000,
                interestMinor = 3_000,
                principalMinor = 7_000,
                feeMinor = 0,
                prepaymentMinor = 0,
                balanceAfterMinor = -1,
            )
        }
    }

    @Test
    fun prepaymentMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            Prepayment(start, 0, PrepaymentMode.REDUCE_TERM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Prepayment(start, -100, PrepaymentMode.REDUCE_PAYMENT)
        }
    }

    @Test
    fun feesCannotBeNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            Loan(LoanType.ANNUITY, 1_000_00, 1000, start, 12, 15, upfrontFeeMinor = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Loan(LoanType.ANNUITY, 1_000_00, 1000, start, 12, 15, monthlyFeeMinor = -1)
        }
    }

    @Test
    fun scheduleEntryPaymentMustCoverTheFeeToo() {
        // The fee is neither interest nor principal, so the split is a three-way sum.
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleEntry(
                number = 1,
                date = start,
                paymentMinor = 10_000,
                interestMinor = 3_000,
                principalMinor = 7_000,
                feeMinor = 500, // payment should have been 10 500
                prepaymentMinor = 0,
                balanceAfterMinor = 100,
            )
        }

        ScheduleEntry(
            number = 1,
            date = start,
            paymentMinor = 10_500,
            interestMinor = 3_000,
            principalMinor = 7_000,
            feeMinor = 500,
            prepaymentMinor = 0,
            balanceAfterMinor = 100,
        )
    }

    @Test
    fun rateChangeRejectsNegativeRate() {
        assertThrows(IllegalArgumentException::class.java) { RateChange(start, -1) }
        RateChange(start, 0)
    }
}
