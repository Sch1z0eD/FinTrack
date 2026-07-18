package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Rate change history; the schedule generator replays these onto the loan. */
@Entity(
    tableName = "loan_rate",
    indices = [Index("loan_id"), Index("effective_from_epoch_day")],
)
data class LoanRateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "loan_id")
    val loanId: String,

    /** Annual rate in basis points (1 bp = 0.01%). */
    @ColumnInfo(name = "rate_bp")
    val rateBp: Int,

    @ColumnInfo(name = "effective_from_epoch_day")
    val effectiveFromEpochDay: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
