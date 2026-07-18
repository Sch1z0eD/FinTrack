package com.findev.fintrack.ui.screens.utilities

import androidx.annotation.StringRes
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterType

@StringRes
fun MeterType.labelRes(): Int = when (this) {
    MeterType.ELECTRICITY -> R.string.meter_type_electricity
    MeterType.COLD_WATER -> R.string.meter_type_cold_water
    MeterType.HOT_WATER -> R.string.meter_type_hot_water
    MeterType.GAS -> R.string.meter_type_gas
    MeterType.HEATING -> R.string.meter_type_heating
    MeterType.OTHER -> R.string.meter_type_other
}

@StringRes
fun BillingKind.labelRes(): Int = when (this) {
    BillingKind.METERED -> R.string.meter_billing_metered
    BillingKind.NORM -> R.string.meter_billing_norm
    BillingKind.FIXED -> R.string.meter_billing_fixed
}

/** What the meter counts, and therefore what its tariff is priced per. */
@StringRes
fun MeterType.unitRes(): Int = when (this) {
    MeterType.ELECTRICITY -> R.string.meter_unit_kwh
    MeterType.COLD_WATER, MeterType.HOT_WATER, MeterType.GAS -> R.string.meter_unit_m3
    MeterType.HEATING -> R.string.meter_unit_gcal
    // OTHER is only ever a fixed fee, which has no per-unit tariff; never actually shown.
    MeterType.OTHER -> R.string.meter_unit_month
}
