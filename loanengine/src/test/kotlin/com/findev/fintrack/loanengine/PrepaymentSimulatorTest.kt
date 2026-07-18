package com.findev.fintrack.loanengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The oracles here are computed by hand, not read back off the engine.
 *
 * The worked loan is 12 000 руб. at 12% over 6 months from 15.01.2027, differentiated:
 * the principal slice is exactly 2 000 руб. a month, so every period is
 * balance x 12000 x days / (100000 x 365) rounded HALF_UP and can be checked on paper.
 * 2027 is not a leap year, so the divisor is 365 throughout.
 *
 * Left alone, its interest per period is:
 *   1) 15.01-15.02, 31d on 12 000,00 -> 122,30   balance 10 000,00
 *   2) 15.02-15.03, 28d on 10 000,00 ->  92,05   balance  8 000,00
 *   3) 15.03-15.04, 31d on  8 000,00 ->  81,53   balance  6 000,00
 *   4) 15.04-15.05, 30d on  6 000,00 ->  59,18   balance  4 000,00
 *   5) 15.05-15.06, 31d on  4 000,00 ->  40,77   balance  2 000,00
 *   6) 15.06-15.07, 30d on  2 000,00 ->  19,73   balance      0
 * Total interest 415,56.
 */
class PrepaymentSimulatorTest {

    private val start = LocalDate.of(2027, 1, 15)

    /** The loan worked through in the class comment. */
    private fun differentiated() =
        Loan(LoanType.DIFFERENTIATED, 12_000_00, 12000, start, 6, paymentDay = 15)

    private fun annuity() =
        Loan(LoanType.ANNUITY, 1_000_000_00, 12000, start, 12, paymentDay = 15)

    /** The first payment date, so the prepayment lands after the scheduled payment. */
    private val prepayDate = LocalDate.of(2027, 2, 15)

    @Test
    fun theBaselineIsTheLoanLeftAlone() {
        val simulation = simulatePrepayment(
            loan = differentiated(),
            date = prepayDate,
            amountMinor = 2_000_00,
        )

        assertEquals(415_56L, simulation.baseline.totalInterestMinor)
        assertEquals(6, simulation.baseline.paymentCount)
        assertEquals(LocalDate.of(2027, 7, 15), simulation.baseline.closingDate)
    }

    /**
     * 2 000 руб. extra on 15.02, term reduced. The bank takes payment 1 first (2 000,00 of
     * principal), so the balance drops 12 000 -> 10 000 -> 8 000. The 2 000 slice never
     * moves, so the debt simply runs out one payment early:
     *   2) 28d on 8 000,00 ->  73,64   balance 6 000,00
     *   3) 31d on 6 000,00 ->  61,15   balance 4 000,00
     *   4) 30d on 4 000,00 ->  39,45   balance 2 000,00
     *   5) 31d on 2 000,00 ->  20,38   balance     0
     * Interest 122,30 + 73,64 + 61,15 + 39,45 + 20,38 = 316,92, so 415,56 - 316,92 = 98,64 saved.
     */
    @Test
    fun reduceTermMatchesTheHandComputedSchedule() {
        val effect = simulatePrepayment(
            loan = differentiated(),
            date = prepayDate,
            amountMinor = 2_000_00,
        ).reduceTerm

        assertEquals(98_64L, effect.savedMinor)
        assertEquals(5, effect.paymentCount)
        assertEquals(1, effect.paymentsSaved)
        assertEquals(LocalDate.of(2027, 6, 15), effect.closingDate)
        // Payment 2: the 2 000,00 slice plus its 73,64 of interest.
        assertEquals(2_073_64L, effect.paymentAfterMinor)
    }

    /**
     * The same 2 000 руб., payment reduced. The balance after payment 1 is the same 8 000,00,
     * but the tail is re-sliced over the 5 payments that remain: 1 600,00 each.
     *   2) 28d on 8 000,00 ->  73,64   balance 6 400,00
     *   3) 31d on 6 400,00 ->  65,23   balance 4 800,00
     *   4) 30d on 4 800,00 ->  47,34   balance 3 200,00
     *   5) 31d on 3 200,00 ->  32,61   balance 1 600,00
     *   6) 30d on 1 600,00 ->  15,78   balance     0
     * Interest 122,30 + 73,64 + 65,23 + 47,34 + 32,61 + 15,78 = 356,90, so 58,66 saved.
     */
    @Test
    fun reducePaymentMatchesTheHandComputedSchedule() {
        val effect = simulatePrepayment(
            loan = differentiated(),
            date = prepayDate,
            amountMinor = 2_000_00,
        ).reducePayment

        assertEquals(58_66L, effect.savedMinor)
        assertEquals(6, effect.paymentCount)
        assertEquals(0, effect.paymentsSaved)
        // The whole point of the mode: the end date does not move.
        assertEquals(LocalDate.of(2027, 7, 15), effect.closingDate)
        // Payment 2: the re-sliced 1 600,00 plus the same 73,64 of interest.
        assertEquals(1_673_64L, effect.paymentAfterMinor)
    }

    @Test
    fun reduceTermSavesMoreThanReducePayment() {
        val simulation = simulatePrepayment(
            loan = annuity(),
            date = prepayDate,
            amountMinor = 100_000_00,
        )

        assertTrue(
            "term ${simulation.reduceTerm.savedMinor} should beat payment " +
                "${simulation.reducePayment.savedMinor}",
            simulation.reduceTerm.savedMinor > simulation.reducePayment.savedMinor,
        )
        assertTrue(simulation.reduceTerm.paymentsSaved > 0)
        assertEquals(0, simulation.reducePayment.paymentsSaved)
    }

    /** On an annuity the payment is fixed, so only REDUCE_PAYMENT may touch it. */
    @Test
    fun reduceTermLeavesTheAnnuityPaymentAlone() {
        val simulation = simulatePrepayment(
            loan = annuity(),
            date = prepayDate,
            amountMinor = 100_000_00,
        )

        val untouched = annuityPaymentMinor(1_000_000_00, 12000, 12)
        assertEquals(untouched, simulation.reduceTerm.paymentAfterMinor)
        assertTrue(
            "the payment should shrink, was ${simulation.reducePayment.paymentAfterMinor}",
            simulation.reducePayment.paymentAfterMinor!! < untouched,
        )
    }

    /**
     * Paying more than is owed closes the loan on the spot: payment 1 takes its 2 000,00,
     * the prepayment covers the remaining 10 000,00 and there is no payment 2 to report.
     * Only the 122,30 already earned stays due, so 415,56 - 122,30 = 293,26 is saved.
     */
    @Test
    fun aPrepaymentBiggerThanTheDebtClosesTheLoan() {
        val effect = simulatePrepayment(
            loan = differentiated(),
            date = prepayDate,
            amountMinor = 50_000_00,
        ).reduceTerm

        assertEquals(293_26L, effect.savedMinor)
        assertEquals(1, effect.paymentCount)
        assertEquals(5, effect.paymentsSaved)
        assertEquals(prepayDate, effect.closingDate)
        assertNull(effect.paymentAfterMinor)
    }

    /** Prepayments already made are part of the picture the simulation compares against. */
    @Test
    fun existingPrepaymentsCountTowardsTheBaseline() {
        val existing = listOf(
            Prepayment(LocalDate.of(2027, 3, 15), 2_000_00, PrepaymentMode.REDUCE_TERM),
        )
        val simulation = simulatePrepayment(
            loan = differentiated(),
            existingPrepayments = existing,
            date = prepayDate,
            amountMinor = 2_000_00,
        )

        assertTrue(simulation.baseline.totalInterestMinor < 415_56L)
        assertEquals(
            summarize(differentiated(), generateSchedule(differentiated(), prepayments = existing)),
            simulation.baseline,
        )
    }

    /**
     * A token prepayment can make REDUCE_PAYMENT cost more, and that is not a bug.
     *
     * This loan starts on 17.07 but pays on the 15th, so its first period is 29 days and
     * accrues 9 534,25 rather than the 10 000,00 the /12 annuity formula assumed. The real
     * balance afterwards is 920 685,46 against the model's 921 151,21. Recomputing the
     * annuity from the real balance therefore hands back a payment of 88 803,86 instead of
     * 88 848,79 - 44,93 lower off a one-kopeck prepayment - and the smaller payment repays
     * the debt more slowly than the money saved.
     *
     * Banks recompute the same way, so the schedule is faithful; the simulator's job is to
     * report the number, not to hide it.
     */
    @Test
    fun aTokenReducePaymentCanCostMoreThanItSaves() {
        val skewed = Loan(
            LoanType.ANNUITY,
            1_000_000_00,
            12000,
            LocalDate.of(2026, 7, 17),
            12,
            paymentDay = 15,
        )
        val simulation = simulatePrepayment(
            loan = skewed,
            date = LocalDate.of(2026, 8, 15),
            amountMinor = 1,
        )

        assertEquals(-25_33L, simulation.reducePayment.savedMinor)
        assertEquals(88_803_86L, simulation.reducePayment.paymentAfterMinor)
        // Reducing the term recomputes nothing, so a kopeck is simply a kopeck.
        assertEquals(0L, simulation.reduceTerm.savedMinor)
        assertEquals(88_848_79L, simulation.reduceTerm.paymentAfterMinor)
    }

    /** Past a few hundred roubles the prepayment dominates and both modes save again. */
    @Test
    fun aRealReducePaymentStillSaves() {
        val skewed = Loan(
            LoanType.ANNUITY,
            1_000_000_00,
            12000,
            LocalDate.of(2026, 7, 17),
            12,
            paymentDay = 15,
        )
        val simulation = simulatePrepayment(
            loan = skewed,
            date = LocalDate.of(2026, 8, 15),
            amountMinor = 100_000_00,
        )

        assertEquals(6_079_54L, simulation.reducePayment.savedMinor)
        assertTrue(simulation.reduceTerm.savedMinor > simulation.reducePayment.savedMinor)
    }

    @Test
    fun aPrepaymentAfterTheLoanClosesIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            simulatePrepayment(
                loan = differentiated(),
                date = LocalDate.of(2028, 1, 15),
                amountMinor = 2_000_00,
            )
        }
    }

    @Test
    fun aPrepaymentBeforeTheLoanIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            simulatePrepayment(
                loan = differentiated(),
                date = start,
                amountMinor = 2_000_00,
            )
        }
    }

    @Test
    fun aNonPositivePrepaymentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            simulatePrepayment(loan = differentiated(), date = prepayDate, amountMinor = 0)
        }
    }
}
