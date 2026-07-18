package com.findev.fintrack.data.local

import androidx.room.ColumnInfo
import com.findev.fintrack.data.local.entity.TransactionType

/** A transaction joined with the names it is displayed with. */
data class TransactionListItem(
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: TransactionType,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    @ColumnInfo(name = "note")
    val note: String?,

    /** Null for transfers, which carry no category. */
    @ColumnInfo(name = "category_name")
    val categoryName: String?,

    /** A part payment against an obligation, so the feed can say so without being opened. */
    @ColumnInfo(name = "settles_partial")
    val isPartialSettlement: Boolean = false,

    @ColumnInfo(name = "category_icon")
    val categoryIcon: String?,

    @ColumnInfo(name = "category_color")
    val categoryColor: Long?,

    @ColumnInfo(name = "account_name")
    val accountName: String,
)
