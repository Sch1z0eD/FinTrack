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
     * Initial annual rate in thousandths of a percent, so 16.9% is 16900.
     * Integer keeps floating point out of the interest math; later changes live in loan_rate.
     * INSTALLMENT loans use 0.
     *
     * Basis points (0.01%) came first and were not enough: contracts quote three decimals,
     * and 28.572% is not a whole number of them.
     */
    @ColumnInfo(name = "rate_milli_percent")
    val rateMilliPercent: Int,

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
     * Lead times to remind at, comma separated, or null for no reminder. "7,1" is a week
     * ahead and again the day before; "0" is on the day itself.
     *
     * A single Int was not enough: one warning is either too early to act on or too late to
     * move money. A child table would be the textbook answer, but this is a handful of small
     * numbers that are always read and written together with the loan, and never queried on
     * their own - a join for that would cost more than it explains. Parsed by
     * [reminderDaysList].
     */
    @ColumnInfo(name = "reminder_days")
    val reminderDays: String? = null,

    /**
     * Epoch day of the first payment, when it is not one month after the loan.
     *
     * See com.findev.fintrack.loanengine.Loan.firstPaymentDate: banks often set it 30-60
     * days out, and without it the whole schedule sits a month early.
     */
    @ColumnInfo(name = "first_payment_epoch_day")
    val firstPaymentEpochDay: Long? = null,

    /**
     * Payment size copied from the contract, or null to derive it from the rate.
     *
     * See com.findev.fintrack.loanengine.Loan.fixedPaymentMinor: some contracts publish a
     * level payment the annuity formula cannot reproduce.
     */
    @ColumnInfo(name = "fixed_payment_minor")
    val fixedPaymentMinor: Long? = null,

    /**
     * The one prepayment mode the contract allows, or null when the bank offers both.
     *
     * Not a preference - a term. A contract reading «уменьшение суммы платежей при
     * сохранении дат» simply will not shorten the term, so offering that choice would
     * show the user a schedule the bank is never going to produce.
     */
    @ColumnInfo(name = "allowed_prepayment_mode")
    val allowedPrepaymentMode: PrepaymentMode? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
) {
    /** Lead times, sorted furthest-out first, empty when reminders are off. */
    val reminderDaysList: List<Int>
        get() = reminderDays
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.distinct()
            ?.sortedDescending()
            .orEmpty()
}

/** Renders lead times back into the stored form; empty means "no reminder". */
fun reminderDaysToStored(days: List<Int>): String? =
    days.distinct().sortedDescending().joinToString(",").takeIf { it.isNotEmpty() }
