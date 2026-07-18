package com.findev.fintrack.data

import com.findev.fintrack.loanengine.ScheduleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NextLoanDueTest {

    private fun entry(date: String, payment: Long = 1000) =
        ScheduleEntry(
            number = 0,
            date = LocalDate.parse(date),
            paymentMinor = payment,
            interestMinor = 0,
            // paymentMinor must split into interest + principal + fee; put it all in principal.
            principalMinor = payment,
            feeMinor = 0,
            prepaymentMinor = 0,
            balanceAfterMinor = 0,
        )

    private val schedule = listOf(
        entry("2026-05-05"),
        entry("2026-06-05"),
        entry("2026-07-05"),
        entry("2026-08-05"),
    )

    /** Nothing paid: the first instalment not before today, so a past-dated loan is not "overdue". */
    @Test
    fun withNothingPaidTheCalendarPicksTheNextNotBeforeToday() {
        val next = nextLoanDue(schedule, paidThrough = null, today = LocalDate.of(2026, 7, 1))
        assertEquals(LocalDate.of(2026, 7, 5), next?.date)
    }

    /** Once marked paid, the next is the first strictly after the paid-through date. */
    @Test
    fun afterPaidThroughItIsTheFollowingInstalment() {
        val next = nextLoanDue(
            schedule,
            paidThrough = LocalDate.of(2026, 6, 5),
            today = LocalDate.of(2026, 7, 1),
        )
        assertEquals(LocalDate.of(2026, 7, 5), next?.date)
    }

    /** A due date passed with nothing marked stays as the answer - that is what overdue means. */
    @Test
    fun anUnmarkedPastInstalmentIsStillTheNext() {
        val next = nextLoanDue(
            schedule,
            paidThrough = LocalDate.of(2026, 5, 5),
            today = LocalDate.of(2026, 8, 1),
        )
        assertEquals(LocalDate.of(2026, 6, 5), next?.date)
    }

    @Test
    fun aFullyRepaidLoanHasNoNext() {
        val next = nextLoanDue(
            schedule,
            paidThrough = LocalDate.of(2026, 8, 5),
            today = LocalDate.of(2026, 8, 6),
        )
        assertNull(next)
    }
}
