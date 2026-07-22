package com.findev.fintrack.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.CategoryTotal
import com.findev.fintrack.data.local.MonthlyTotal
import com.findev.fintrack.data.local.TransactionListItem
import com.findev.fintrack.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** What a payment has settled: its id and the latest due date covered by a live row. */
data class PaidThrough(
    @ColumnInfo(name = "settles_payment_id") val paymentId: String,
    @ColumnInfo(name = "paid_through_epoch_day") val paidThroughEpochDay: Long,
)

/** Money actually paid against one occurrence, whether or not it closed it. */
data class SettledAmount(
    @ColumnInfo(name = "settles_payment_id") val paymentId: String,
    @ColumnInfo(name = "settles_due_epoch_day") val dueEpochDay: Long,
    @ColumnInfo(name = "paid_minor") val paidMinor: Long,
)

/** One booked payment against an obligation, for the history sheet. */
data class SettlementRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "settles_due_epoch_day") val dueEpochDay: Long,
    @ColumnInfo(name = "settles_partial") val isPartial: Boolean,
)

@Dao
interface TransactionDao {

    /**
     * Everything ever paid against one obligation, newest first.
     *
     * Ordered by the day the money moved rather than the due date it covered: the question
     * this answers is "when did I pay, and how much", and a late payment belongs where it
     * actually happened.
     */
    @Query(
        "SELECT id, amount_minor, date_epoch_day, settles_due_epoch_day, settles_partial " +
            "FROM transactions " +
            "WHERE is_deleted = 0 AND settles_payment_id = :paymentId " +
            "AND settles_due_epoch_day IS NOT NULL " +
            "ORDER BY date_epoch_day DESC, created_at DESC",
    )
    fun observeSettlements(paymentId: String): Flow<List<SettlementRow>>

    /**
     * Money paid per occurrence - every settling row, part payment or not.
     *
     * Grouped by due date as well as payment: two ₽500 instalments against March are ₽1000
     * towards March and say nothing about April.
     *
     * This is what actually left the account, which is not the same as what was owed. A
     * utility bill differs from its nominal every month, and a payment can be settled for
     * less by arrangement - so "оплачено" has to be counted from here rather than from the
     * scheduled amount of whatever is marked closed.
     */
    @Query(
        "SELECT settles_payment_id, settles_due_epoch_day, SUM(amount_minor) AS paid_minor " +
            "FROM transactions " +
            "WHERE is_deleted = 0 " +
            "AND settles_payment_id IS NOT NULL AND settles_due_epoch_day IS NOT NULL " +
            "GROUP BY settles_payment_id, settles_due_epoch_day",
    )
    fun observeSettledAmounts(): Flow<List<SettledAmount>>

    /**
     * Settlements for every payment at once, derived from live transactions only.
     *
     * Deleted rows are excluded by the WHERE, which is the entire point: undo an expense
     * and the obligation goes back to unpaid without anything else having to notice.
     */
    @Query(
        "SELECT settles_payment_id, MAX(settles_due_epoch_day) AS paid_through_epoch_day " +
            "FROM transactions " +
            "WHERE is_deleted = 0 AND settles_payment_id IS NOT NULL " +
            "AND settles_due_epoch_day IS NOT NULL AND settles_partial = 0 " +
            "GROUP BY settles_payment_id",
    )
    fun observePaidThrough(): Flow<List<PaidThrough>>

    /** Same, one-shot: the alarm rebuild has no UI to observe from. */
    @Query(
        "SELECT settles_payment_id, MAX(settles_due_epoch_day) AS paid_through_epoch_day " +
            "FROM transactions " +
            "WHERE is_deleted = 0 AND settles_payment_id IS NOT NULL " +
            "AND settles_due_epoch_day IS NOT NULL AND settles_partial = 0 " +
            "GROUP BY settles_payment_id",
    )
    suspend fun getPaidThrough(): List<PaidThrough>

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY date_epoch_day DESC, updated_at DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** LEFT JOIN on category: transfers have none. */
    @Query(
        """
        SELECT t.id AS id,
               t.type AS type,
               t.amount_minor AS amount_minor,
               t.date_epoch_day AS date_epoch_day,
               t.note AS note,
               t.category_id AS category_id,
               c.name AS category_name,
               c.icon AS category_icon,
               c.color AS category_color,
               a.name AS account_name,
               t.settles_partial AS settles_partial
        FROM transactions t
        JOIN account a ON a.id = t.account_id
        LEFT JOIN category c ON c.id = t.category_id
        WHERE t.is_deleted = 0
        ORDER BY t.date_epoch_day DESC, t.created_at DESC
        """,
    )
    fun observeList(): Flow<List<TransactionListItem>>

    /**
     * Expense totals per category within a date range, biggest first - the shape a
     * breakdown chart needs. Transfers and income are excluded; a category with nothing
     * spent in the range simply does not appear. [accountId] null means all accounts;
     * otherwise only that account's expenses count.
     */
    @Query(
        """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               c.icon AS categoryIcon,
               c.color AS categoryColor,
               SUM(t.amount_minor) AS totalMinor
        FROM transactions t
        JOIN category c ON c.id = t.category_id
        WHERE t.is_deleted = 0
          AND t.type = 'EXPENSE'
          AND t.date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
          AND (:accountId IS NULL OR t.account_id = :accountId)
        GROUP BY c.id
        ORDER BY totalMinor DESC
        """,
    )
    fun observeExpensesByCategory(
        fromEpochDay: Long,
        toEpochDay: Long,
        accountId: String?,
    ): Flow<List<CategoryTotal>>

    /** One-shot expense total for a single category over a day range - for the budget check. */
    @Query(
        """
        SELECT COALESCE(SUM(amount_minor), 0) FROM transactions
        WHERE is_deleted = 0
          AND type = 'EXPENSE'
          AND category_id = :categoryId
          AND date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    suspend fun sumExpenseForCategory(categoryId: String, fromEpochDay: Long, toEpochDay: Long): Long

    /**
     * Income and expense sums per calendar month from [fromEpochDay] onward, oldest first -
     * the shape a monthly trend chart needs. Transfers are excluded. The epoch day is turned
     * into a "YYYY-MM" bucket via strftime (86400 seconds per day). Months with no rows do
     * not appear here; the caller zero-fills the gaps so the axis stays continuous.
     * [accountId] null means all accounts.
     */
    @Query(
        """
        SELECT strftime('%Y-%m', t.date_epoch_day * 86400, 'unixepoch') AS year_month,
               SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_minor ELSE 0 END) AS income_minor,
               SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_minor ELSE 0 END) AS expense_minor
        FROM transactions t
        WHERE t.is_deleted = 0
          AND t.type IN ('INCOME', 'EXPENSE')
          AND t.date_epoch_day >= :fromEpochDay
          AND (:accountId IS NULL OR t.account_id = :accountId)
        GROUP BY year_month
        ORDER BY year_month
        """,
    )
    fun observeMonthlyTotals(fromEpochDay: Long, accountId: String?): Flow<List<MonthlyTotal>>

    /**
     * Monthly expense totals for a single category, oldest first - the trend shown when a
     * category is tapped in the breakdown. income_minor is always 0 here: categories are an
     * expense concept, so the trend is expense-only. Same zero-fill contract as above.
     */
    @Query(
        """
        SELECT strftime('%Y-%m', t.date_epoch_day * 86400, 'unixepoch') AS year_month,
               0 AS income_minor,
               SUM(t.amount_minor) AS expense_minor
        FROM transactions t
        WHERE t.is_deleted = 0
          AND t.type = 'EXPENSE'
          AND t.category_id = :categoryId
          AND t.date_epoch_day >= :fromEpochDay
          AND (:accountId IS NULL OR t.account_id = :accountId)
        GROUP BY year_month
        ORDER BY year_month
        """,
    )
    fun observeMonthlyCategoryExpenses(
        fromEpochDay: Long,
        categoryId: String,
        accountId: String?,
    ): Flow<List<MonthlyTotal>>

    /** Soft delete: the row stays for future sync, only the flag flips. */
    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

    /**
     * Undo only works while the account still exists. Otherwise the row would count
     * towards the total balance while belonging to no listed account, and
     * "sum of accounts == total balance" would quietly stop holding.
     */
    @Query(
        """
        UPDATE transactions SET is_deleted = 0, updated_at = :updatedAt
        WHERE id = :id
          AND EXISTS (
              SELECT 1 FROM account a
              WHERE a.id = transactions.account_id AND a.is_deleted = 0
          )
        """,
    )
    suspend fun restore(id: String, updatedAt: Long)
}
