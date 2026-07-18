package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.LoanEntity
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.RecurrencePeriod
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import com.findev.fintrack.loanengine.LoanSummary
import com.findev.fintrack.loanengine.ScheduleEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthlyObligationsTest {

    private val july = LocalDate.of(2026, 7, 1).toEpochDay()..LocalDate.of(2026, 7, 31).toEpochDay()

    @Test
    fun `sums only what falls inside the month`() {
        val loan = loanWithSchedule(
            id = "loan",
            dates = listOf(
                LocalDate.of(2026, 6, 25) to 1_000L,
                LocalDate.of(2026, 7, 25) to 1_000L,
                LocalDate.of(2026, 8, 25) to 1_000L,
            ),
        )

        val result = monthlyObligations(listOf(loan), emptyList(), emptyMap(), july)

        assertEquals(1_000L, result.loansMinor)
        assertEquals(1_000L, result.totalMinor)
    }

    @Test
    fun `a monthly payment contributes one occurrence`() {
        val payment = recurring(
            id = "net",
            amountMinor = 80_000,
            start = LocalDate.of(2026, 1, 20),
            period = RecurrencePeriod.MONTH,
        )

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), july)

        assertEquals(80_000L, result.recurringMinor)
    }

    @Test
    fun `a daily payment contributes every day of the month`() {
        val payment = recurring(
            id = "coffee",
            amountMinor = 100,
            start = LocalDate.of(2026, 7, 1),
            period = RecurrencePeriod.DAY,
        )

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), july)

        assertEquals(31 * 100L, result.recurringMinor)
    }

    @Test
    fun `a yearly payment falling outside the month counts nothing`() {
        val payment = recurring(
            id = "insurance",
            amountMinor = 500_000,
            start = LocalDate.of(2026, 3, 10),
            period = RecurrencePeriod.YEAR,
        )

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), july)

        assertEquals(0L, result.recurringMinor)
    }

    @Test
    fun `occurrences past the end date stop counting`() {
        val payment = recurring(
            id = "ended",
            amountMinor = 80_000,
            start = LocalDate.of(2026, 1, 20),
            period = RecurrencePeriod.MONTH,
            end = LocalDate.of(2026, 6, 20),
        )

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), july)

        assertEquals(0L, result.recurringMinor)
    }

    @Test
    fun `paid through marks the occurrences behind it as paid`() {
        val payment = recurring(
            id = "net",
            amountMinor = 80_000,
            start = LocalDate.of(2026, 7, 5),
            period = RecurrencePeriod.MONTH,
        )
        val loan = loanWithSchedule(
            id = "loan",
            dates = listOf(LocalDate.of(2026, 7, 25) to 1_000L),
        )

        val paidThrough = mapOf("net" to LocalDate.of(2026, 7, 5).toEpochDay())
        val result = monthlyObligations(listOf(loan), listOf(payment), paidThrough, july)

        assertEquals(81_000L, result.totalMinor)
        assertEquals(80_000L, result.paidMinor)
        assertEquals(1_000L, result.remainingMinor)
    }

    @Test
    fun `paying more than owed never makes the remainder negative`() {
        val payment = recurring(
            id = "net",
            amountMinor = 80_000,
            start = LocalDate.of(2026, 7, 5),
            period = RecurrencePeriod.MONTH,
        )
        // Paid through a date beyond this month: the user paid several months ahead.
        val paidThrough = mapOf("net" to LocalDate.of(2026, 12, 5).toEpochDay())

        val result = monthlyObligations(emptyList(), listOf(payment), paidThrough, july)

        assertEquals(0L, result.remainingMinor)
    }

    @Test
    fun `a payment starting on the 31st still lands in a short month`() {
        val june = LocalDate.of(2026, 6, 1).toEpochDay()..LocalDate.of(2026, 6, 30).toEpochDay()
        val payment = recurring(
            id = "rent",
            amountMinor = 100_000,
            start = LocalDate.of(2026, 1, 31),
            period = RecurrencePeriod.MONTH,
        )

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), june)

        // June has 30 days; the occurrence clamps to the 30th rather than skipping the month.
        assertEquals(100_000L, result.recurringMinor)
    }

    private fun recurring(
        id: String,
        amountMinor: Long,
        start: LocalDate,
        period: RecurrencePeriod,
        end: LocalDate? = null,
    ) = RecurringPaymentEntity(
        id = id,
        name = id,
        amountMinor = amountMinor,
        period = period,
        startDateEpochDay = start.toEpochDay(),
        endDateEpochDay = end?.toEpochDay(),
        accountId = "acc",
        categoryId = "cat",
        updatedAt = 0,
    )

    private fun loanWithSchedule(id: String, dates: List<Pair<LocalDate, Long>>) = LoanWithSchedule(
        loan = LoanEntity(
            id = id,
            name = id,
            type = LoanType.ANNUITY,
            principalMinor = 100_000,
            rateBp = 1_990,
            termMonths = 12,
            startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            paymentDay = 25,
            updatedAt = 0,
        ),
        rates = emptyList(),
        prepayments = emptyList(),
        schedule = dates.map { (date, amount) ->
            ScheduleEntry(
                number = 1,
                date = date,
                paymentMinor = amount,
                principalMinor = amount,
                interestMinor = 0,
                feeMinor = 0,
                prepaymentMinor = 0,
                balanceAfterMinor = 0,
            )
        },
        summary = LoanSummary(
            principalMinor = 100_000,
            totalInterestMinor = 0,
            totalFeesMinor = 0,
            totalPaidMinor = 100_000,
            overpaymentMinor = 0,
            closingDate = LocalDate.of(2026, 12, 25),
            paymentCount = 12,
        ),
    )
}
