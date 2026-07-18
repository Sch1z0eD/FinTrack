package com.findev.fintrack.data

import com.findev.fintrack.data.local.CategoryTotal
import com.findev.fintrack.data.local.MonthlyTotal
import com.findev.fintrack.data.local.TransactionListItem
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
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** Latest settled due date per payment, derived from live transactions. */
    fun observePaidThrough(): Flow<Map<String, Long>> =
        transactionDao.observePaidThrough().map { rows ->
            rows.associate { it.paymentId to it.paidThroughEpochDay }
        }

    suspend fun getPaidThrough(): Map<String, Long> =
        transactionDao.getPaidThrough().associate { it.paymentId to it.paidThroughEpochDay }
}
