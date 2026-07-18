package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MeterRemindersTest {

    private fun meter(
        id: String,
        billing: BillingKind = BillingKind.METERED,
        reminderDay: Int,
    ) = MeterEntity(
        id = id,
        name = id,
        type = MeterType.ELECTRICITY,
        billing = billing,
        tariffMinor = 6_36,
        normMilli = 0,
        reminderDay = reminderDay,
        updatedAt = 0,
    )

    @Test
    fun remindsOnTheReminderDay() {
        val meters = listOf(meter("a", reminderDay = 23), meter("b", reminderDay = 10))
        val due = metersDueToday(meters, LocalDate.of(2026, 7, 23))
        assertEquals(listOf("a"), due.map { it.id })
    }

    @Test
    fun remindsNobodyOnAnOrdinaryDay() {
        val meters = listOf(meter("a", reminderDay = 23), meter("b", reminderDay = 10))
        assertEquals(emptyList<String>(), metersDueToday(meters, LocalDate.of(2026, 7, 15)).map { it.id })
    }

    /** A reminder set for the 31st still fires in February - on the 28th, its last day. */
    @Test
    fun the31stClampsToTheLastDayOfAShortMonth() {
        val meters = listOf(meter("a", reminderDay = 31))
        assertEquals(listOf("a"), metersDueToday(meters, LocalDate.of(2026, 2, 28)).map { it.id })
        // ...and does not also fire earlier in the month.
        assertEquals(emptyList<String>(), metersDueToday(meters, LocalDate.of(2026, 2, 27)).map { it.id })
    }

    /** The 31st is a real day in July, so clamping must not pull it onto the 30th too. */
    @Test
    fun the31stFiresOnceInALongMonth() {
        val meters = listOf(meter("a", reminderDay = 31))
        assertEquals(emptyList<String>(), metersDueToday(meters, LocalDate.of(2026, 7, 30)).map { it.id })
        assertEquals(listOf("a"), metersDueToday(meters, LocalDate.of(2026, 7, 31)).map { it.id })
    }

    @Test
    fun normativeServicesAreNeverReminded() {
        val meters = listOf(meter("water", billing = BillingKind.NORM, reminderDay = 0))
        assertEquals(emptyList<String>(), metersDueToday(meters, LocalDate.of(2026, 7, 1)).map { it.id })
    }
}
