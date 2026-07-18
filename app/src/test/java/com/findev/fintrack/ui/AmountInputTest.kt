package com.findev.fintrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun parsesWholeRubles() {
        assertEquals(0L, parseAmountToMinor(""))
        assertEquals(500L, parseAmountToMinor("5"))
        assertEquals(123_400L, parseAmountToMinor("1234"))
    }

    @Test
    fun treatsSingleDecimalAsTenthsOfRuble() {
        // "5,3" is 5 roubles 30 kopecks - not 5 roubles 3 kopecks.
        assertEquals(530L, parseAmountToMinor("5,3"))
        assertEquals(505L, parseAmountToMinor("5,05"))
        assertEquals(500L, parseAmountToMinor("5,"))
    }

    @Test
    fun acceptsBothSeparatorsBecauseVendorKeyboardsDiffer() {
        assertEquals(1_234L, parseAmountToMinor("12.34"))
        assertEquals(1_234L, parseAmountToMinor("12,34"))
    }

    @Test
    fun ignoresGarbageTheSystemKeyboardCanProduce() {
        // A second separator, letters and spaces must not corrupt the amount.
        assertEquals(1_234L, parseAmountToMinor("12.34.56"))
        assertEquals(1_234L, parseAmountToMinor("12,34,56"))
        assertEquals(1_234L, parseAmountToMinor("1 2 , 3 4"))
        assertEquals(1_234L, parseAmountToMinor("12abc,34"))
        assertEquals(0L, parseAmountToMinor("..,,"))
        assertEquals(0L, parseAmountToMinor("abc"))
    }

    @Test
    fun dropsExtraDecimalsInsteadOfRounding() {
        assertEquals(1_239L, parseAmountToMinor("12,399"))
    }

    @Test
    fun leadingSeparatorIsIgnored() {
        // ",5" has no roubles part; the separator is dropped until a digit exists.
        assertEquals(500L, parseAmountToMinor(",5"))
    }

    @Test
    fun sanitizeKeepsOnlyValidAmountText() {
        assertEquals("12,34", sanitizeAmountInput("12.34"))
        assertEquals("12,34", sanitizeAmountInput("12,34,56"))
        assertEquals("12,34", sanitizeAmountInput("a1b2,3c4d"))
        assertEquals("", sanitizeAmountInput(","))
        assertEquals("999999999", sanitizeAmountInput("99999999999999"))
    }

    @Test
    fun formatRoundTripsThroughTheField() {
        assertEquals("", formatAmountForInput(0))
        assertEquals("12", formatAmountForInput(1_200))
        assertEquals("12,34", formatAmountForInput(1_234))
        assertEquals("12,05", formatAmountForInput(1_205))

        listOf(1L, 500L, 1_234L, 999_999_999L).forEach { minor ->
            assertEquals(minor, parseAmountToMinor(formatAmountForInput(minor)))
        }
    }
}
