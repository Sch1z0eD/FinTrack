package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.findev.fintrack.data.local.AccountBalance
import com.findev.fintrack.data.local.AverageBasis
import com.findev.fintrack.data.local.MonthTotals
import kotlinx.coroutines.flow.Flow

/** Cross-entity aggregates for the overview screen. */
@Dao
interface OverviewDao {

    /**
     * Money the user actually holds: the sum of the *active* accounts' balances.
     *
     * Archived accounts are excluded - they are closed, so their money is not on hand.
     * Written as a sum over the same per-account expression as [observeAccountBalances]
     * on purpose: the total is the sum of the listed balances by construction, so the
     * two can never drift apart. A transfer to an archived account therefore correctly
     * lowers the total instead of cancelling itself out.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            a.initial_balance_minor
            + COALESCE((
                SELECT SUM(CASE t.type
                    WHEN 'INCOME' THEN t.amount_minor
                    WHEN 'EXPENSE' THEN -t.amount_minor
                    WHEN 'TRANSFER' THEN -t.amount_minor
                END)
                FROM transactions t
                WHERE t.is_deleted = 0 AND t.account_id = a.id), 0)
            + COALESCE((
                SELECT SUM(t.amount_minor)
                FROM transactions t
                WHERE t.is_deleted = 0 AND t.type = 'TRANSFER' AND t.account_to_id = a.id), 0)
        ), 0)
        FROM account a
        WHERE a.is_deleted = 0 AND a.is_archived = 0
        """,
    )
    fun observeTotalBalance(): Flow<Long>

    /**
     * Per-account balance. Unlike the total, transfers DO matter here: they leave
     * [account_id] and arrive at [account_to_id]. Summing these rows still yields
     * the total, because every transfer cancels itself out across two accounts.
     *
     * Archived accounts stay in the list: they may still hold money, and dropping
     * them would break "sum of accounts == total balance".
     */
    @Query(
        """
        SELECT
            a.id AS id,
            a.name AS name,
            a.is_archived AS is_archived,
            a.initial_balance_minor
            + COALESCE((
                SELECT SUM(CASE t.type
                    WHEN 'INCOME' THEN t.amount_minor
                    WHEN 'EXPENSE' THEN -t.amount_minor
                    WHEN 'TRANSFER' THEN -t.amount_minor
                END)
                FROM transactions t
                WHERE t.is_deleted = 0 AND t.account_id = a.id), 0)
            + COALESCE((
                SELECT SUM(t.amount_minor)
                FROM transactions t
                WHERE t.is_deleted = 0 AND t.type = 'TRANSFER' AND t.account_to_id = a.id), 0)
            AS balance_minor
        FROM account a
        WHERE a.is_deleted = 0
        ORDER BY a.is_archived, a.position, a.name
        """,
    )
    fun observeAccountBalances(): Flow<List<AccountBalance>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expense_minor,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS income_minor
        FROM transactions
        WHERE is_deleted = 0
          AND date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observeTotals(fromEpochDay: Long, toEpochDay: Long): Flow<MonthTotals>

    /** All-time income/expense and the earliest transaction date, for the average figures. */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expense_minor,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS income_minor,
            MIN(date_epoch_day) AS first_day
        FROM transactions
        WHERE is_deleted = 0 AND type != 'TRANSFER'
        """,
    )
    fun observeAverageBasis(): Flow<AverageBasis>
}
