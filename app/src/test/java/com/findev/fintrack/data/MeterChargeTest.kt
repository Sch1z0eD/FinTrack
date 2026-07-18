package com.findev.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The oracles are real receipts, not numbers this code produced.
 *
 * ОЗК, Омск, июнь 2026: электроэнергия 197 кВт·ч at 6,36 руб. => 1 252,92 руб.
 * ОмскВодоканал, июнь 2026 (по нормативу): 3,900 м3 at 28,24 руб. => 110,14 руб.,
 * водоотведение 6,700 м3 at 32,48 руб. => 217,62 руб.
 */
class MeterChargeTest {

    @Test
    fun electricityMatchesTheRealReceipt() {
        // 197 kWh exactly, tariff 6,36.
        assertEquals(1_252_92L, chargeMinor(consumedMilli = 197_000, tariffMinor = 6_36))
    }

    @Test
    fun waterMatchesTheRealReceipt() {
        assertEquals(110_14L, chargeMinor(consumedMilli = 3_900, tariffMinor = 28_24))
    }

    /**
     * 6,700 x 32,48 = 217,616 - the receipt says 217,61, which is truncation, not HALF_UP.
     * We round, so we say 217,62. One kopeck, and ours is the arithmetic the rest of the
     * app uses; the difference is worth knowing about rather than tuning to one utility.
     */
    @Test
    fun waterDisposalRoundsHalfUpWhereTheReceiptTruncates() {
        assertEquals(217_62L, chargeMinor(consumedMilli = 6_700, tariffMinor = 32_48))
    }

    /** Thousandths really are thousandths: 0,001 m3 at 28,24 is 2,824 kopecks -> 3. */
    @Test
    fun aThousandthOfAUnitStillRoundsToWhatItIsWorth() {
        assertEquals(3L, chargeMinor(consumedMilli = 1, tariffMinor = 28_24))
        // ...and a tenth of that is under half a kopeck, so it really does vanish.
        assertEquals(0L, chargeMinor(consumedMilli = 1, tariffMinor = 1_00))
    }

    @Test
    fun consumptionIsTheDifferenceBetweenReadings() {
        assertEquals(197_000L, consumedMilli(previousMilli = 16_180_000, currentMilli = 16_377_000))
        assertEquals(0L, consumedMilli(previousMilli = 100, currentMilli = 100))
    }

    /**
     * A meter reading below the last one means it was replaced or rolled over. Guessing
     * would either invent a month of consumption or hide a real one.
     */
    @Test
    fun aReadingBelowThePreviousIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            consumedMilli(previousMilli = 16_377_000, currentMilli = 100_000)
        }
    }

    /**
     * The first reading of a meter that has been running for years is where counting
     * starts. Billing its face whole charged 102 904,80 руб. for a month that cost
     * 1 252,92 - this is that bug, kept in a test.
     */
    @Test
    fun theFirstReadingIsABaselineAndCostsNothing() {
        assertEquals(0L, readingChargeMinor(previousMilli = null, currentMilli = 16_180_000, tariffMinor = 6_36))
    }

    @Test
    fun theSecondReadingBillsTheMonthBetweenThem() {
        assertEquals(
            1_252_92L,
            readingChargeMinor(previousMilli = 16_180_000, currentMilli = 16_377_000, tariffMinor = 6_36),
        )
    }

    /** A meter installed today reads 0, and 0 is a reading - not "no previous reading". */
    @Test
    fun aBrandNewMeterBaselinesAtZeroAndStillBillsFromThere() {
        assertEquals(0L, readingChargeMinor(previousMilli = null, currentMilli = 0, tariffMinor = 6_36))
        assertEquals(76_32L, readingChargeMinor(previousMilli = 0, currentMilli = 12_000, tariffMinor = 6_36))
    }

    /**
     * Water is billed supply + drainage on the same volume, each rounded on its own.
     * ОмскВодоканал июнь 2026: cold 3,900 m3 is 110,14 supply + 126,67 drainage = 236,81;
     * hot 2,800 m3 is 79,07 + 90,94 = 170,01. The two meters together are 406,82, the
     * bill's whole water charge (110,14 + 79,07 + 217,61).
     */
    @Test
    fun waterCombinesSupplyAndDrainageRoundedSeparately() {
        assertEquals(236_81L, combinedChargeMinor(volumeMilli = 3_900, tariffMinor = 28_24, drainageTariffMinor = 32_48))
        assertEquals(170_01L, combinedChargeMinor(volumeMilli = 2_800, tariffMinor = 28_24, drainageTariffMinor = 32_48))
        assertEquals(406_82L, 236_81L + 170_01L)
    }

    /** Separate rounding is deliberate: rounding the combined 60,72 tariff would drift. */
    @Test
    fun drainageRoundsApartFromSupply() {
        // 3,900 x 32,48 = 126,672 -> 126,67 on its own.
        assertEquals(126_67L, chargeMinor(consumedMilli = 3_900, tariffMinor = 32_48))
        // Reading form: baseline still bills nothing even with a drainage tariff.
        assertEquals(0L, readingChargeMinor(previousMilli = null, currentMilli = 5_000, tariffMinor = 28_24, drainageTariffMinor = 32_48))
        // Second reading bills supply + drainage on the delta.
        assertEquals(236_81L, readingChargeMinor(previousMilli = 1_000, currentMilli = 4_900, tariffMinor = 28_24, drainageTariffMinor = 32_48))
    }

    /** No drainage tariff means no second charge - electricity and gas are unaffected. */
    @Test
    fun noDrainageLeavesTheChargeAsSupplyOnly() {
        assertEquals(1_252_92L, combinedChargeMinor(volumeMilli = 197_000, tariffMinor = 6_36, drainageTariffMinor = 0))
    }
}
