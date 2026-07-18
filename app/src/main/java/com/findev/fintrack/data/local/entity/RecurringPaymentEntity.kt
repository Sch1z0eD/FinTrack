package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecurrencePeriod { DAY, WEEK, MONTH, YEAR }

/**
 * An obligation that repeats on its own schedule: rent, internet, a subscription.
 *
 * Kept apart from [LoanEntity] on purpose. A loan carries interest, a rate history and
 * prepayments; this carries a period. Folding both into one table would leave a
 * subscription holding empty rate columns and a loan holding a meaningless "every week".
 *
 * Nothing here is ever posted automatically: the due date raises a reminder, and the
 * transaction appears only when the user says it was paid. The balance has to follow
 * what actually happened, not what was supposed to.
 */
@Entity(
    tableName = "recurring_payment",
    indices = [Index("account_id"), Index("category_id"), Index("start_date_epoch_day")],
)
data class RecurringPaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** Always positive, in kopecks. An obligation is always money going out. */
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "period")
    val period: RecurrencePeriod,

    /** The first due date; every later one is derived from it. */
    @ColumnInfo(name = "start_date_epoch_day")
    val startDateEpochDay: Long,

    /** Null means open-ended - a subscription runs until it is cancelled. */
    @ColumnInfo(name = "end_date_epoch_day")
    val endDateEpochDay: Long? = null,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "category_id")
    val categoryId: String,

    @ColumnInfo(name = "reminder_enabled")
    val reminderEnabled: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
