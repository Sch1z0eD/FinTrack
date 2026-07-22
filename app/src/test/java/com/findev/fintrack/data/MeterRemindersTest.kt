package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterType
import com.findev.fintrack.data.local.entity.reminderDaysToStored
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MeterRemindersTest {

    private fun meter(
        id: String,
        billing: BillingKind = BillingKind.METERED,
        paymentDay: Int,
        reminderDays: List<Int>,
    ) = MeterEntity(
        id = id,
        name = id,
        type = MeterType.ELECTRICITY,
        billing = billing,
        tariffMinor = 6_36,
        normMilli = 0,
        paymentDay = paymentDay,
        reminderDays = reminderDaysToStored(reminderDays),
        updatedAt = 0,
    )

    @Test
    fun remindsOnThePaymentDayWithLeadZero() {
        val meters = listOf(
            meter("a", paymentDay = 23, reminderDays = listOf(0)),
            meter("b", paymentDay = 10, reminderDays = listOf(0)),
        )
        val due = metersToRemindToday(meters, LocalDate.of(2026, 7, 23))
        assertEquals(listOf("a"), due.map { it.id })
    }

    @Test
    fun remindsTheChosenNumberOfDaysAhead() {
        // Pays on the 25th, wants a warning a week and a day before: fires on the 18th and 24th.
        val meters = listOf(meter("a", paymentDay = 25, reminderDays = listOf(7, 1)))
        assertEquals(listOf("a"), metersToRemindToday(meters, LocalDate.of(2026, 7, 18)).map { it.id })
        assertEquals(listOf("a"), metersToRemindToday(meters, LocalDate.of(2026, 7, 24)).map { it.id })
        assertEquals(emptyList<String>(), metersToRemindToday(meters, LocalDate.of(2026, 7, 20)).map { it.id })
        // On the day itself, without a 0 lead time, nothing fires.
        assertEquals(emptyList<String>(), metersToRemindToday(meters, LocalDate.of(2026, 7, 25)).map { it.id })
    }

    /** A lead time can reach back into the previous month; the arithmetic must still land. */
    @Test
    fun aLeadTimeCrossesTheMonthBoundary() {
        // Pays on the 3rd of August, warns a week before: fires on 27 July.
        val meters = listOf(meter("a", paymentDay = 3, reminderDays = listOf(7)))
        assertEquals(listOf("a"), metersToRemindToday(meters, LocalDate.of(2026, 7, 27)).map { it.id })
    }

    /** A payment day of 31 clamps to a short month's last day, so a "0" lead fires there. */
    @Test
    fun the31stClampsToTheLastDayOfAShortMonth() {
        val meters = listOf(meter("a", paymentDay = 31, reminderDays = listOf(0)))
        assertEquals(listOf("a"), metersToRemindToday(meters, LocalDate.of(2026, 2, 28)).map { it.id })
        assertEquals(emptyList<String>(), metersToRemindToday(meters, LocalDate.of(2026, 2, 27)).map { it.id })
    }

    @Test
    fun normativeAndFixedServicesCanRemindToo() {
        // No longer metered-only: a normative service with a reminder is reminded.
        val meters = listOf(meter("water", billing = BillingKind.NORM, paymentDay = 10, reminderDays = listOf(0)))
        assertEquals(listOf("water"), metersToRemindToday(meters, LocalDate.of(2026, 7, 10)).map { it.id })
    }

    @Test
    fun aServiceWithNoLeadTimesIsNeverReminded() {
        val meters = listOf(meter("a", paymentDay = 10, reminderDays = emptyList()))
        assertEquals(emptyList<String>(), metersToRemindToday(meters, LocalDate.of(2026, 7, 10)).map { it.id })
    }
}
