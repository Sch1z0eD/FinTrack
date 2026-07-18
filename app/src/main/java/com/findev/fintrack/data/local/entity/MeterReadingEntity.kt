package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single meter reading plus the tariff that was in force when it was entered.
 *
 * [tariffMinor] and [amountMinor] are snapshots on purpose: utility tariffs are
 * indexed (typically on 1 July), and without the snapshot every past month would
 * silently recalculate at the new tariff.
 */
@Entity(
    tableName = "meter_reading",
    indices = [Index("meter_id"), Index("date_epoch_day")],
)
data class MeterReadingEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "meter_id")
    val meterId: String,

    /**
     * Meter face value scaled by 1000 (water meters read to 3 decimals),
     * e.g. 12345.678 m3 is stored as 12345678. Integer, never floating point.
     */
    @ColumnInfo(name = "value_milli")
    val valueMilli: Long,

    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    /** Tariff per unit in kopecks at the moment of entry. */
    @ColumnInfo(name = "tariff_minor")
    val tariffMinor: Long,

    /** Drainage tariff snapshot (водоотведение on a water meter); 0 when there is none. */
    @ColumnInfo(name = "drainage_tariff_minor")
    val drainageTariffMinor: Long = 0,

    /**
     * Charge for this reading in kopecks: delta x supply tariff plus delta x drainage
     * tariff, each rounded HALF_UP separately - the way a bill rounds its per-service lines.
     */
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
