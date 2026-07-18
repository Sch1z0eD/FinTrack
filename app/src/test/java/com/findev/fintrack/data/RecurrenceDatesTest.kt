package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.RecurrencePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceDatesTest {

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    /**
     * The one that matters: a payment on the 31st must come back to the 31st.
     *
     * February can only take the 28th, but that is February's problem, not the payment's.
     * Stepping a month on from 28.02 would give 28.03 and the payment would drift down the
     * calendar for good.
     */
    @Test
    fun aMonthlyPaymentOnThe31stReturnsToThe31stAfterAShortMonth() {
        val start = date(2027, 1, 31)
        val expected = listOf(
            date(2027, 1, 31),
            date(2027, 2, 28),
            date(2027, 3, 31),
            date(2027, 4, 30),
            date(2027, 5, 31),
            date(2027, 6, 30),
            date(2027, 7, 31),
        )

        val actual = (0L..6L).map { nthRecurrence(start, RecurrencePeriod.MONTH, it) }

        assertEquals(expected, actual)
    }

    @Test
    fun aMonthlyPaymentOnThe31stLandsOnThe29thInALeapFebruary() {
        // 2028 is a leap year, so February can take one more day than in 2027.
        assertEquals(
            date(2028, 2, 29),
            nthRecurrence(date(2028, 1, 31), RecurrencePeriod.MONTH, 1),
        )
    }

    @Test
    fun aYearlyPaymentOnLeapDayFallsBackToThe28thInCommonYears() {
        val start = date(2028, 2, 29)

        assertEquals(date(2029, 2, 28), nthRecurrence(start, RecurrencePeriod.YEAR, 1))
        assertEquals(date(2030, 2, 28), nthRecurrence(start, RecurrencePeriod.YEAR, 2))
        // ...and returns to the 29th at the next leap year rather than sticking on the 28th.
        assertEquals(date(2032, 2, 29), nthRecurrence(start, RecurrencePeriod.YEAR, 4))
    }

    @Test
    fun theStartDateIsItselfTheFirstDueDate() {
        val start = date(2027, 3, 10)

        assertEquals(
            start,
            nextRecurrenceOnOrAfter(start, RecurrencePeriod.MONTH, end = null, onOrAfter = start),
        )
    }

    @Test
    fun aDateBeforeTheStartGetsTheStart() {
        val start = date(2027, 3, 10)

        assertEquals(
            start,
            nextRecurrenceOnOrAfter(
                start,
                RecurrencePeriod.MONTH,
                end = null,
                onOrAfter = date(2026, 1, 1),
            ),
        )
    }

    @Test
    fun theNextMonthlyDateSkipsThePeriodsAlreadyPast() {
        assertEquals(
            date(2027, 9, 5),
            nextRecurrenceOnOrAfter(
                start = date(2027, 1, 5),
                period = RecurrencePeriod.MONTH,
                end = null,
                onOrAfter = date(2027, 8, 6),
            ),
        )
    }

    /** The estimate undershoots here - 31.01 + 1 month is 28.02, so it must walk forward. */
    @Test
    fun aClampedMonthDoesNotStallTheSearch() {
        assertEquals(
            date(2027, 2, 28),
            nextRecurrenceOnOrAfter(
                start = date(2027, 1, 31),
                period = RecurrencePeriod.MONTH,
                end = null,
                onOrAfter = date(2027, 2, 1),
            ),
        )
    }

    @Test
    fun weeklyAndDailyStepEvenly() {
        val start = date(2027, 3, 1)

        assertEquals(
            date(2027, 3, 22),
            nextRecurrenceOnOrAfter(start, RecurrencePeriod.WEEK, null, date(2027, 3, 16)),
        )
        assertEquals(
            date(2027, 3, 16),
            nextRecurrenceOnOrAfter(start, RecurrencePeriod.DAY, null, date(2027, 3, 16)),
        )
    }

    @Test
    fun anEndedPaymentHasNoNextDate() {
        assertNull(
            nextRecurrenceOnOrAfter(
                start = date(2027, 1, 10),
                period = RecurrencePeriod.MONTH,
                end = date(2027, 6, 10),
                onOrAfter = date(2027, 6, 11),
            ),
        )
    }

    /**
     * A payment entered today may have been running for years, so an unmarked one is not
     * overdue - it is unknown. The calendar decides until the user says otherwise.
     */
    @Test
    fun withNothingMarkedPaidTheCalendarDecidesAndThePastIsLeftAlone() {
        assertEquals(
            date(2027, 8, 5),
            nextDueRecurrence(
                start = date(2027, 1, 5),
                period = RecurrencePeriod.MONTH,
                end = null,
                paidThrough = null,
                today = date(2027, 7, 20),
            ),
        )
    }

    @Test
    fun markingOnePaidPointsAtTheNextOne() {
        assertEquals(
            date(2027, 8, 5),
            nextDueRecurrence(
                start = date(2027, 1, 5),
                period = RecurrencePeriod.MONTH,
                end = null,
                paidThrough = date(2027, 7, 5),
                today = date(2027, 7, 5),
            ),
        )
    }

    /** The whole point of tracking: a due date that came and went shows up as overdue. */
    @Test
    fun anOccurrenceThatCameDueAndWasNeverMarkedStaysInThePast() {
        val due = nextDueRecurrence(
            start = date(2027, 1, 5),
            period = RecurrencePeriod.MONTH,
            end = null,
            paidThrough = date(2027, 6, 5),
            today = date(2027, 7, 20),
        )

        assertEquals(date(2027, 7, 5), due)
        assertTrue("should read as overdue", due!!.isBefore(date(2027, 7, 20)))
    }

    @Test
    fun markingTheLastOccurrencePaidEndsThePayment() {
        assertNull(
            nextDueRecurrence(
                start = date(2027, 1, 10),
                period = RecurrencePeriod.MONTH,
                end = date(2027, 6, 10),
                paidThrough = date(2027, 6, 10),
                today = date(2027, 6, 10),
            ),
        )
    }

    /** The end date is inclusive: the last payment is due on it, not before it. */
    @Test
    fun anOpenEndedPaymentHasNoTotal() {
        assertNull(totalRecurrences(date(2027, 1, 10), RecurrencePeriod.MONTH, end = null))
    }

    /** 10.01 to 10.12 inclusive is twelve payments, not eleven. */
    @Test
    fun theTotalCountsBothEnds() {
        assertEquals(
            12,
            totalRecurrences(date(2027, 1, 10), RecurrencePeriod.MONTH, date(2027, 12, 10)),
        )
    }

    /** An end a day before the twelfth is due leaves eleven. */
    @Test
    fun theTotalDropsAnOccurrenceTheEndDoesNotReach() {
        assertEquals(
            11,
            totalRecurrences(date(2027, 1, 10), RecurrencePeriod.MONTH, date(2027, 12, 9)),
        )
    }

    /** The 31st clamps to short months, and the count must not drift with it. */
    @Test
    fun theTotalSurvivesClampedMonths() {
        assertEquals(
            7,
            totalRecurrences(date(2027, 1, 31), RecurrencePeriod.MONTH, date(2027, 7, 31)),
        )
    }

    @Test
    fun settledCountsWhatIsPaidThrough() {
        val start = date(2027, 1, 10)

        assertEquals(0, settledRecurrences(start, RecurrencePeriod.MONTH, paidThrough = null))
        assertEquals(1, settledRecurrences(start, RecurrencePeriod.MONTH, start))
        assertEquals(3, settledRecurrences(start, RecurrencePeriod.MONTH, date(2027, 3, 10)))
        // Paid through a day the payment does not fall on still settles the last one before it.
        assertEquals(3, settledRecurrences(start, RecurrencePeriod.MONTH, date(2027, 3, 20)))
    }

    @Test
    fun aPaymentIsStillDueOnItsLastDay() {
        assertEquals(
            date(2027, 6, 10),
            nextRecurrenceOnOrAfter(
                start = date(2027, 1, 10),
                period = RecurrencePeriod.MONTH,
                end = date(2027, 6, 10),
                onOrAfter = date(2027, 6, 10),
            ),
        )
    }
}
