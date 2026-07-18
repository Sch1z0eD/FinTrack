package com.findev.fintrack.data.local

import androidx.room.ColumnInfo

/** An account with its current balance in kopecks. */
data class AccountBalance(
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,
)
