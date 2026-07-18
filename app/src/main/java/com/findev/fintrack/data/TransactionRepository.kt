package com.findev.fintrack.data

import com.findev.fintrack.data.local.CategoryTotal
import com.findev.fintrack.data.local.MonthlyTotal
import com.findev.fintrack.data.local.TransactionListItem
import com.findev.fintrack.data.local.dao.SettlementRow
import com.findev.fintrack.data.local.dao.TransactionDao
import com.findev.fintrack.data.local.entity.TransactionEntity
import com.findev.fintrack.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {
    fun observeList(): Flow<List<TransactionListItem>> = transactionDao.observeList()

    /** Expense totals per category between two epoch days (inclusive), biggest first. */
    fun observeExpensesByCategory(
        fromEpochDay: Long,
        toEpochDay: Long,
        accountId: String? = null,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeExpensesByCategory(fromEpochDay, toEpochDay, accountId)

    /** Income/expense sums per month from [fromEpochDay] onward, oldest first (gaps not filled). */
    fun observeMonthlyTotals(fromEpochDay: Long, accountId: String? = null): Flow<List<MonthlyTotal>> =
        transactionDao.observeMonthlyTotals(fromEpochDay, accountId)

    /** One category's monthly expense trend from [fromEpochDay] onward (income column is 0). */
    fun observeMonthlyCategoryExpenses(
        fromEpochDay: Long,
        categoryId: String,
        accountId: String? = null,
    ): Flow<List<MonthlyTotal>> =
        transactionDao.observeMonthlyCategoryExpenses(fromEpochDay, categoryId, accountId)

    /** Soft delete, so [restore] can undo it and future sync still sees the row. */
    suspend fun softDelete(id: String) =
        transactionDao.softDelete(id, System.currentTimeMillis())

    suspend fun restore(id: String) =
        transactionDao.restore(id, System.currentTimeMillis())

    suspend fun getById(id: String): TransactionEntity? = transactionDao.getById(id)

    /**
     * Overwrites the editable fields of an existing income/expense.
     * Identity and creation stay untouched; [TransactionEntity.updatedAt] advances for sync.
     */
    suspend fun updateIncomeOrExpense(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        categoryId: String,
        dateEpochDay: Long,
        note: String? = null,
    ) {
        require(type != TransactionType.TRANSFER) { "Use a transfer-specific API for TRANSFER" }
        require(amountMinor > 0) { "Amount must be positive, was $amountMinor" }

        val existing = requireNotNull(transactionDao.getById(id)) { "No transaction with id $id" }
        transactionDao.update(
            existing.copy(
                type = type,
                amountMinor = amountMinor,
                accountId = accountId,
                // Income/expense never has a destination account, even if it once did.
                accountToId = null,
                categoryId = categoryId,
                dateEpochDay = dateEpochDay,
                note = note,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * [amountMinor] is always positive; [type] carries the direction.
     * TRANSFER is not created here - it needs a destination account.
     *
     * Returns the new row's id, so a caller that has to undo itself can find it again.
     */
    suspend fun addIncomeOrExpense(
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        categoryId: String,
        dateEpochDay: Long,
        note: String? = null,
        /** Set when this expense settles a loan or recurring payment. */
        settlesPaymentId: String? = null,
        settlesDueEpochDay: Long? = null,
        /** Part payment: recorded against the occurrence without closing it. */
        settlesPartial: Boolean = false,
    ): String {
        require(type != TransactionType.TRANSFER) { "Use a transfer-specific API for TRANSFER" }
        require(amountMinor > 0) { "Amount must be positive, was $amountMinor" }
        require((settlesPaymentId == null) == (settlesDueEpochDay == null)) {
            "A settlement needs both the payment and the due date it covers"
        }

        val id = UUID.randomUUID().toString()
        transactionDao.insert(
            TransactionEntity(
                id = id,
                type = type,
                amountMinor = amountMinor,
                accountId = accountId,
                categoryId = categoryId,
                dateEpochDay = dateEpochDay,
                note = note,
                settlesPaymentId = settlesPaymentId,
                settlesDueEpochDay = settlesDueEpochDay,
                settlesPartial = settlesPartial,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /**
     * Moves money between two of the user's own accounts.
     *
     * One row, not two. A transfer is a single event with a source and a destination, and
     * booking it as an expense plus an income would count it in both "расходы за месяц" and
     * "доходы за месяц" - inflating each by money that never left the user's pockets, and
     * poisoning every category statistic downstream. That is exactly what the app forced
     * until now: the schema had TRANSFER and account_to_id from the start, the feed could
     * render them, and nothing could create one.
     *
     * No category: a transfer is not spending, so it belongs in none of them.
     */
    suspend fun addTransfer(
        amountMinor: Long,
        fromAccountId: String,
        toAccountId: String,
        dateEpochDay: Long,
        note: String? = null,
    ): String {
        require(amountMinor > 0) { "Amount must be positive, was $amountMinor" }
        require(fromAccountId != toAccountId) {
            "A transfer needs two different accounts, both were $fromAccountId"
        }

        val id = UUID.randomUUID().toString()
        transactionDao.insert(
            TransactionEntity(
                id = id,
                type = TransactionType.TRANSFER,
                amountMinor = amountMinor,
                accountId = fromAccountId,
                accountToId = toAccountId,
                categoryId = null,
                dateEpochDay = dateEpochDay,
                note = note,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** Changes an existing transfer, keeping it a transfer. */
    suspend fun updateTransfer(
        id: String,
        amountMinor: Long,
        fromAccountId: String,
        toAccountId: String,
        dateEpochDay: Long,
        note: String? = null,
    ) {
        require(amountMinor > 0) { "Amount must be positive, was $amountMinor" }
        require(fromAccountId != toAccountId) {
            "A transfer needs two different accounts, both were $fromAccountId"
        }

        val existing = requireNotNull(transactionDao.getById(id)) { "No transaction with id $id" }
        transactionDao.update(
            existing.copy(
                type = TransactionType.TRANSFER,
                amountMinor = amountMinor,
                accountId = fromAccountId,
                accountToId = toAccountId,
                categoryId = null,
                dateEpochDay = dateEpochDay,
                note = note,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Latest settled due date per payment, derived from live transactions. */
    fun observePaidThrough(): Flow<Map<String, Long>> =
        transactionDao.observePaidThrough().map { rows ->
            rows.associate { it.paymentId to it.paidThroughEpochDay }
        }

    suspend fun getPaidThrough(): Map<String, Long> =
        transactionDao.getPaidThrough().associate { it.paymentId to it.paidThroughEpochDay }

    /** Everything ever paid against one obligation, newest first. */
    fun observeSettlements(paymentId: String): Flow<List<SettlementRow>> =
        transactionDao.observeSettlements(paymentId)

    /**
     * Money actually paid per occurrence, keyed by payment id and due date.
     *
     * Keyed by the pair because a payment belongs to one occurrence: money put against
     * March must not make April look started.
     */
    fun observeSettledAmounts(): Flow<Map<Pair<String, Long>, Long>> =
        transactionDao.observeSettledAmounts().map { rows ->
            rows.associate { (it.paymentId to it.dueEpochDay) to it.paidMinor }
        }
}
