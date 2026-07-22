package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MeterType { ELECTRICITY, COLD_WATER, HOT_WATER, GAS, HEATING, OTHER }

/**
 * How a service is charged - "П" and "Н" on a Russian utility bill, plus a flat fee.
 *
 * [METERED] bills the gap between two readings. [NORM] bills a fixed volume every month and
 * takes no readings. [FIXED] is a flat monthly sum with no volume at all - капремонт,
 * отопление and содержание (площадь × ставка, constant), ОДН, «Системы безопасности»; the
 * sum lives in [MeterEntity.tariffMinor] and means kopecks-per-month, not per-unit.
 */
enum class BillingKind { METERED, NORM, FIXED }

@Entity(tableName = "meter")
data class MeterEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: MeterType,

    @ColumnInfo(name = "billing")
    val billing: BillingKind,

    /**
     * Current tariff per unit, in kopecks. This is the value applied to *new* readings;
     * past readings keep their own snapshot in meter_reading, so indexation does not
     * rewrite consumption history.
     */
    @ColumnInfo(name = "tariff_minor")
    val tariffMinor: Long,

    /**
     * Second tariff per unit, in kopecks, charged on the same volume as [tariffMinor].
     *
     * This is водоотведение (drainage) on a water meter: every cubic metre of water is
     * billed once for supply ([tariffMinor]) and once for drainage, so a water meter costs
     * (supply + drainage) per m3. 0 for meters that have no such second charge - electricity
     * and gas.
     */
    @ColumnInfo(name = "drainage_tariff_minor")
    val drainageTariffMinor: Long = 0,

    /**
     * Volume charged every month, in thousandths of a unit. Only meaningful when
     * [billing] is [BillingKind.NORM]; a metered service leaves it at 0 and gets its
     * volume from readings instead.
     */
    @ColumnInfo(name = "norm_milli")
    val normMilli: Long = 0,

    /**
     * Day of month the bill is due/paid (1..31). Applies to every billing kind - even a
     * metered service is paid on a date, not only read - and is what reminders count back from.
     * The 31st clamps to the last day of a short month.
     */
    // defaultValue mirrors the migration's DEFAULT so Room's schema check matches.
    @ColumnInfo(name = "payment_day", defaultValue = "1")
    val paymentDay: Int = 1,

    /**
     * Reminder lead times before [paymentDay], as a comma-separated list like a loan: "7,1"
     * warns a week and a day ahead, null/empty means no reminder. Replaced the old single
     * "reminder day" so any service - not just metered - can remind, and more than once.
     */
    @ColumnInfo(name = "reminder_days")
    val reminderDays: String? = null,

    /** The user-made group this service is filed under, or null for ungrouped («Прочее»). */
    @ColumnInfo(name = "group_id")
    val groupId: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
) {
    /** Lead times, furthest-out first, empty when reminders are off. */
    val reminderDaysList: List<Int> get() = reminderDaysFromStored(reminderDays)
}
