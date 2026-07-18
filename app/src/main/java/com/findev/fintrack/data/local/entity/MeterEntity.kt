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
     * Day of month to remind about submitting readings (1..31), or 0 for a service that
     * has nothing to submit - a normative service is billed whether you look at it or not.
     */
    @ColumnInfo(name = "reminder_day")
    val reminderDay: Int,

    /** The user-made group this service is filed under, or null for ungrouped («Прочее»). */
    @ColumnInfo(name = "group_id")
    val groupId: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
