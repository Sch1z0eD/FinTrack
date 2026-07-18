package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val MATH: MathContext = MathContext(30, RoundingMode.HALF_UP)

/**
 * What a meter reading costs: consumption times tariff, rounded to the kopeck.
 *
 * [consumedMilli] is thousandths of a unit and [tariffMinor] is kopecks per whole unit, so
 * the product carries six implied decimal places; dividing by 1000 brings it back to
 * kopecks. BigDecimal rather than Long arithmetic because that intermediate overflows for
 * nothing - and never Double, which is the rule the whole app is built on.
 *
 * HALF_UP, like every other rounding here. Checked against a real receipt (ОЗК, июнь 2026):
 * 197 kWh at 6,36 руб. is 1 252,92 руб. exactly.
 */
fun chargeMinor(consumedMilli: Long, tariffMinor: Long): Long {
    require(consumedMilli >= 0) { "Consumption cannot be negative, was $consumedMilli" }
    require(tariffMinor >= 0) { "Tariff cannot be negative, was $tariffMinor" }

    return BigDecimal(consumedMilli)
        .multiply(BigDecimal(tariffMinor), MATH)
        .divide(BigDecimal(1000), MATH)
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
}

/**
 * Consumption between two readings.
 *
 * A meter that has been replaced or has rolled over reads lower than last time. There is
 * no honest way to guess what happened, so this refuses rather than inventing a huge
 * month or silently charging zero - the caller has to say what it means.
 */
fun consumedMilli(previousMilli: Long, currentMilli: Long): Long {
    require(currentMilli >= previousMilli) {
        "Reading $currentMilli is below the previous $previousMilli"
    }
    return currentMilli - previousMilli
}

/** A service that bills a fixed amount every month rather than from readings. */
fun MeterEntity.isMonthly(): Boolean = billing != BillingKind.METERED

/**
 * What a non-metered service costs each month, or null for a metered one (whose charge only
 * exists between two readings). NORM multiplies its fixed volume by the tariff(s); FIXED is
 * simply the tariff, which for it means kopecks-per-month.
 */
fun MeterEntity.monthlyChargeMinor(): Long? = when (billing) {
    BillingKind.FIXED -> tariffMinor
    BillingKind.NORM -> combinedChargeMinor(normMilli, tariffMinor, drainageTariffMinor)
    BillingKind.METERED -> null
}

/**
 * The total charge on a volume billed under a supply tariff and, optionally, a second
 * tariff on the same volume - водоотведение on a water meter.
 *
 * The two are rounded separately and then added, the way a bill lists supply and drainage
 * as their own rounded lines: 3,900 m3 is 110,14 for water and 126,67 for drainage, 236,81
 * together - not one HALF_UP of 3,900 x (28,24 + 32,48).
 */
fun combinedChargeMinor(volumeMilli: Long, tariffMinor: Long, drainageTariffMinor: Long): Long {
    val supply = chargeMinor(volumeMilli, tariffMinor)
    val drainage = if (drainageTariffMinor > 0) chargeMinor(volumeMilli, drainageTariffMinor) else 0
    return supply + drainage
}

/**
 * What a reading costs, given whatever the meter read last time - or nothing, if this is
 * the first reading entered for it.
 *
 * A meter that has been on the wall for years reads 16180 the day it is first written down
 * here. That number is where counting starts, not a month's worth of electricity: billing
 * it whole charges 102 904,80 руб. for a month that cost 1 252,92. So the first reading is
 * a baseline and costs nothing; the second one is the first that bills.
 *
 * [drainageTariffMinor] adds водоотведение on the same consumed volume for a water meter;
 * it is 0 for electricity and gas.
 */
fun readingChargeMinor(
    previousMilli: Long?,
    currentMilli: Long,
    tariffMinor: Long,
    drainageTariffMinor: Long = 0,
): Long = if (previousMilli == null) {
    0
} else {
    combinedChargeMinor(consumedMilli(previousMilli, currentMilli), tariffMinor, drainageTariffMinor)
}
