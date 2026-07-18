package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LoanType { ANNUITY, DIFFERENTIATED, INSTALLMENT, INTEREST_ONLY }

@Entity(tableName = "loan")
data class LoanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: LoanType,

    /** Principal in kopecks. */
    @ColumnInfo(name = "principal_minor")
    val principalMinor: Long,

    /**
     * Initial annual rate in basis points (1 bp = 0.01%), so 16.9% is 1690.
     * Integer keeps floating point out of the interest math; later changes live in loan_rate.
     * INSTALLMENT loans use 0.
     */
    @ColumnInfo(name = "rate_bp")
    val rateBp: Int,

    @ColumnInfo(name = "start_date_epoch_day")
    val startDateEpochDay: Long,

    @ColumnInfo(name = "term_months")
    val termMonths: Int,

    /** Day of month the payment is due (1..31). */
    @ColumnInfo(name = "payment_day")
    val paymentDay: Int,

    /** One-off fee at origination, in kopecks. Not a scheduled payment, but part of the overpayment. */
    @ColumnInfo(name = "upfront_fee_minor")
    val upfrontFeeMinor: Long = 0,

    /** Servicing fee added to every payment, in kopecks. This is how a 0% INSTALLMENT earns. */
    @ColumnInfo(name = "monthly_fee_minor")
    val monthlyFeeMinor: Long = 0,

    /**
     * Where the payment is charged from and what it counts as. Both nullable: a loan is
     * a contract and stays valid without them - they only matter once "Оплачено" has to
     * post a real expense.
     */
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    /**
     * Days before the payment date to remind, or null for no reminder. 0 means on the day.
     * Unlike recurring payments (a single on-the-day flag), a loan instalment is worth a
     * heads-up so the money can be moved in time, and how many days is the user's call.
     */
    @ColumnInfo(name = "reminder_days_before")
    val reminderDaysBefore: Int? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
