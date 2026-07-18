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

@Dao
interface TransactionDao {

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
            "AND settles_due_epoch_day IS NOT NULL " +
            "GROUP BY settles_payment_id",
    )
    fun observePaidThrough(): Flow<List<PaidThrough>>

    /** Same, one-shot: the alarm rebuild has no UI to observe from. */
    @Query(
        "SELECT settles_payment_id, MAX(settles_due_epoch_day) AS paid_through_epoch_day " +
            "FROM transactions " +
            "WHERE is_deleted = 0 AND settles_payment_id IS NOT NULL " +
            "AND settles_due_epoch_day IS NOT NULL " +
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
               c.name AS category_name,
               c.icon AS category_icon,
               c.color AS category_color,
               a.name AS account_name
        FROM transactions t
        JOIN account a ON a.id = t.account_id
        LEFT JOIN category c ON c.id = t.category_id
        WHERE t.is_deleted = 0
        ORDER BY t.date_epoch_day DESC, t.updated_at DESC
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
