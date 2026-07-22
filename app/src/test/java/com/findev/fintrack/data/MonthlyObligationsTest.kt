package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.LoanEntity
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
import com.findev.fintrack.data.local.entity.MeterType
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

        val result = monthlyObligations(listOf(loan), emptyList(), emptyMap(), emptyMap(), july)

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

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), emptyMap(), july)

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

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), emptyMap(), july)

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

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), emptyMap(), july)

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

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), emptyMap(), july)

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

        val due = LocalDate.of(2026, 7, 5).toEpochDay()
        val paidThrough = mapOf("net" to due)
        // Paid counts the money that actually moved, so the transaction has to be there.
        val paidAmounts = mapOf(("net" to due) to 80_000L)
        val result = monthlyObligations(listOf(loan), listOf(payment), paidThrough, paidAmounts, july)

        assertEquals(81_000L, result.totalMinor)
        assertEquals(80_000L, result.paidMinor)
        // Only the loan instalment is still open.
        assertEquals(1_000L, result.remainingMinor)
    }

    /**
     * The bug this guards: "оплачено" was decided purely by a settling row existing, so
     * paying 500 against an 80 000 bill closed the whole month.
     */
    @Test
    fun `a part payment counts towards the month without closing the occurrence`() {
        val payment = recurring(
            id = "net",
            amountMinor = 80_000,
            start = LocalDate.of(2026, 7, 5),
            period = RecurrencePeriod.MONTH,
        )
        val paid = mapOf(("net" to LocalDate.of(2026, 7, 5).toEpochDay()) to 500L)

        // Nothing is paid *through*: the occurrence is still open, so the whole nominal
        // is still owed even though 500 has gone out.
        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), paid, july)

        assertEquals(80_000L, result.totalMinor)
        assertEquals(500L, result.paidMinor)
        assertEquals(80_000L, result.remainingMinor)
    }

    /**
     * Settling for less than the nominal, which the pay dialog allows and banks do accept.
     *
     * All three figures agree on 3 800: that is what left the account, and once the month
     * is closed for less, 3 800 *is* what the month cost. Two earlier versions of this got
     * it wrong in opposite directions - one credited the scheduled 10 000 as paid, the next
     * reported "3 800 of 10 000 paid, 0 remaining", which reads as a contradiction.
     */
    @Test
    fun `an occurrence settled for less becomes the month's actual burden`() {
        val payment = recurring(
            id = "net",
            amountMinor = 10_000,
            start = LocalDate.of(2026, 7, 5),
            period = RecurrencePeriod.MONTH,
        )
        val due = LocalDate.of(2026, 7, 5).toEpochDay()

        val result = monthlyObligations(
            emptyList(),
            listOf(payment),
            mapOf("net" to due),
            mapOf(("net" to due) to 3_800L),
            july,
        )

        assertEquals(3_800L, result.totalMinor)
        assertEquals(3_800L, result.paidMinor)
        assertEquals(0L, result.remainingMinor)
    }

    /**
     * A payment made ahead is booked against the last occurrence it covers, so the ones it
     * settled by implication have nothing of their own. Their scheduled amount has to stand,
     * or they would silently count as costing nothing.
     */
    @Test
    fun `an occurrence settled by implication keeps its scheduled amount`() {
        val payment = recurring(
            id = "net",
            amountMinor = 10_000,
            start = LocalDate.of(2026, 7, 5),
            period = RecurrencePeriod.WEEK,
        )
        val firstDue = LocalDate.of(2026, 7, 5).toEpochDay()
        val secondDue = LocalDate.of(2026, 7, 12).toEpochDay()

        val result = monthlyObligations(
            emptyList(),
            listOf(payment),
            // Paid through the second occurrence; only the second carries the money.
            mapOf("net" to secondDue),
            mapOf(("net" to secondDue) to 20_000L),
            LocalDate.of(2026, 7, 1).toEpochDay()..LocalDate.of(2026, 7, 12).toEpochDay(),
        )

        assertEquals(0L, result.remainingMinor)
        assertEquals(20_000L, result.paidMinor)
        // 10 000 for the implied first occurrence, 20 000 booked on the second.
        assertEquals(30_000L, result.totalMinor)
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

        val result = monthlyObligations(emptyList(), listOf(payment), paidThrough, emptyMap(), july)

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

        val result = monthlyObligations(emptyList(), listOf(payment), emptyMap(), emptyMap(), june)

        // June has 30 days; the occurrence clamps to the 30th rather than skipping the month.
        assertEquals(100_000L, result.recurringMinor)
    }

    @Test
    fun `fixed and metered utilities count towards the month`() {
        val fixed = meter("kap", BillingKind.FIXED, tariffMinor = 84_303)
        val metered = meter("svet", BillingKind.METERED, tariffMinor = 636)
        val reading = reading("r1", "svet", LocalDate.of(2026, 7, 20), amountMinor = 125_292)

        val result = monthlyObligations(
            emptyList(), emptyList(), emptyMap(), emptyMap(), july,
            meters = listOf(fixed, metered),
            readings = listOf(reading),
        )

        assertEquals(84_303L + 125_292L, result.utilitiesMinor)
        assertEquals(84_303L + 125_292L, result.totalMinor)
        assertEquals(84_303L + 125_292L, result.remainingMinor)
        assertEquals(0L, result.paidMinor)
    }

    @Test
    fun `a metered reading from an earlier month does not count in this one`() {
        val metered = meter("svet", BillingKind.METERED, tariffMinor = 636)
        val juneReading = reading("r0", "svet", LocalDate.of(2026, 6, 20), amountMinor = 100_000)

        val result = monthlyObligations(
            emptyList(), emptyList(), emptyMap(), emptyMap(), july,
            meters = listOf(metered),
            readings = listOf(juneReading),
        )

        assertEquals(0L, result.utilitiesMinor)
    }

    @Test
    fun `paid utilities drop out of the remaining figure`() {
        val fixed = meter("kap", BillingKind.FIXED, tariffMinor = 84_303)
        val metered = meter("svet", BillingKind.METERED, tariffMinor = 636)
        val reading = reading("r1", "svet", LocalDate.of(2026, 7, 20), amountMinor = 125_292)

        val firstOfJuly = LocalDate.of(2026, 7, 1).toEpochDay()
        val readingDay = LocalDate.of(2026, 7, 20).toEpochDay()

        val result = monthlyObligations(
            emptyList(), emptyList(),
            // Fixed settled for its month, metered reading settled by its id.
            paidThrough = mapOf("kap" to firstOfJuly, "r1" to readingDay),
            paidAmounts = mapOf(
                ("kap" to firstOfJuly) to 84_303L,
                ("r1" to readingDay) to 125_292L,
            ),
            month = july,
            meters = listOf(fixed, metered),
            readings = listOf(reading),
        )

        assertEquals(84_303L + 125_292L, result.totalMinor)
        assertEquals(84_303L + 125_292L, result.paidMinor)
        assertEquals(0L, result.remainingMinor)
    }

    private fun meter(
        id: String,
        billing: BillingKind,
        tariffMinor: Long,
        normMilli: Long = 0,
    ) = MeterEntity(
        id = id,
        name = id,
        type = MeterType.OTHER,
        billing = billing,
        tariffMinor = tariffMinor,
        normMilli = normMilli,
        paymentDay = 5,
        updatedAt = 0,
    )

    private fun reading(
        id: String,
        meterId: String,
        date: LocalDate,
        amountMinor: Long,
    ) = MeterReadingEntity(
        id = id,
        meterId = meterId,
        valueMilli = 0,
        dateEpochDay = date.toEpochDay(),
        tariffMinor = 0,
        amountMinor = amountMinor,
        updatedAt = 0,
    )

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
            rateMilliPercent = 19_900,
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
