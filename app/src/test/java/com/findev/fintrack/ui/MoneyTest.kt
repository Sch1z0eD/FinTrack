package com.findev.fintrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun formatsZero() {
        assertEquals("0,00", formatMinor(0))
    }

    @Test
    fun padsKopecks() {
        assertEquals("0,05", formatMinor(5))
        assertEquals("0,50", formatMinor(50))
        assertEquals("1,00", formatMinor(100))
        assertEquals("1,01", formatMinor(101))
    }

    @Test
    fun groupsThousands() {
        assertEquals("999,99", formatMinor(99_999))
        assertEquals("1 000,00", formatMinor(100_000))
        assertEquals("12 345,67", formatMinor(1_234_567))
        assertEquals("1 234 567,89", formatMinor(123_456_789))
    }

    @Test
    fun formatsNegative() {
        assertEquals("-1 234,56", formatMinor(-123_456))
    }
}
