package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * Table is named "transactions" because TRANSACTION is a reserved SQLite keyword.
 *
 * TRANSFER rows move money between accounts: [accountId] is the source,
 * [accountToId] the destination, and [categoryId] is null. Income/expense
 * aggregates must exclude TRANSFER rows.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("account_id"),
        Index("account_to_id"),
        Index("category_id"),
        Index("date_epoch_day"),
        Index("settles_payment_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: TransactionType,

    /** Always positive, in kopecks; [type] carries the direction. */
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    /** Destination account, set only for TRANSFER. */
    @ColumnInfo(name = "account_to_id")
    val accountToId: String? = null,

    /** Null for TRANSFER. */
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    /** Local calendar day, stored as epoch day to keep day grouping timezone-stable. */
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    @ColumnInfo(name = "note")
    val note: String? = null,

    /**
     * The loan or recurring payment this row settles, if any.
     *
     * This is what makes "оплачено" a fact rather than a flag: the transaction *is* the
     * record of payment, so deleting it un-marks the obligation by itself. A separate
     * paid-through column could not survive the user deleting the expense behind it - it
     * would keep insisting the payment was made.
     *
     * Loans and recurring payments both use UUIDs, so one column serves both; which kind
     * it is never matters to the question being asked ("what has this id settled?").
     */
    @ColumnInfo(name = "settles_payment_id")
    val settlesPaymentId: String? = null,

    /** Which occurrence was settled - its due date, not the day the money moved. */
    @ColumnInfo(name = "settles_due_epoch_day")
    val settlesDueEpochDay: Long? = null,

    /**
     * A part payment: real money against this occurrence that does not close it.
     *
     * Without this, paying 100 ₽ against a 6 696 ₽ instalment marked the whole month
     * settled - the paid-through query only ever asked whether a settling row existed, not
     * how much it was for. Amount alone cannot decide it either: a final instalment is
     * legitimately smaller than the rest, and a bank can accept less by arrangement. So the
     * user says which it was, and the query counts only the rows that closed something.
     */
    // The default is declared here as well as in the migration on purpose. ALTER TABLE ADD
    // COLUMN cannot add a NOT NULL column without one, so the database will have a default
    // whatever happens; saying so here keeps the exported schema and the real table
    // identical instead of relying on Room being lenient about the difference.
    @ColumnInfo(name = "settles_partial", defaultValue = "0")
    val settlesPartial: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /**
     * When the row was first entered, never changed afterwards. The feed sorts a day's rows by
     * it rather than by [updatedAt], so deleting and undoing (or editing) a transaction no
     * longer jumps it to the top of its day - it returns to where it was.
     */
    // defaultValue mirrors the migration's DEFAULT 0 so Room's schema check matches; the
    // migration backfills it from updated_at and every insert sets it explicitly.
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
