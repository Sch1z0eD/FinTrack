package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PrepaymentMode { REDUCE_TERM, REDUCE_PAYMENT }

@Entity(
    tableName = "loan_prepayment",
    indices = [Index("loan_id"), Index("date_epoch_day")],
)
data class LoanPrepaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "loan_id")
    val loanId: String,

    /** Prepayment amount in kopecks. */
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    @ColumnInfo(name = "mode")
    val mode: PrepaymentMode,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
