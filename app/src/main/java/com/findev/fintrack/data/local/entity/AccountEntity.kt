package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** Opening balance in kopecks. */
    @ColumnInfo(name = "initial_balance_minor")
    val initialBalanceMinor: Long,

    /**
     * A closed account: hidden when picking an account for a new transaction, but its
     * history and balance stay. Deletion is reserved for accounts with no transactions.
     */
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    /**
     * Manual sort order, low first. The transaction picker and the accounts screen both
     * follow it, so the user decides which account leads rather than it being fixed by
     * creation time.
     */
    // defaultValue mirrors the migration's DEFAULT 0 so Room's schema check matches.
    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
