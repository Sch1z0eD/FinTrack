package com.findev.fintrack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.findev.fintrack.data.local.FinTrackDatabase
import com.findev.fintrack.data.local.SeedCategoriesCallback
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.data.local.entity.TransactionEntity
import com.findev.fintrack.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OverviewAggregatesTest {

    private lateinit var db: FinTrackDatabase
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var overviewRepository: OverviewRepository

    private lateinit var accountId: String
    private lateinit var expenseCategoryId: String
    private lateinit var incomeCategoryId: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinTrackDatabase::class.java)
            .addCallback(SeedCategoriesCallback(context))
            .build()
        accountRepository = AccountRepository(db.accountDao())
        transactionRepository = TransactionRepository(db.transactionDao())
        overviewRepository = OverviewRepository(db.overviewDao())

        accountId = accountRepository.create("Наличные", initialBalanceMinor = 100_000)
        expenseCategoryId = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first().id
        incomeCategoryId = db.categoryDao().observeByType(CategoryType.INCOME).first().first().id
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addExpense(amountMinor: Long, dateEpochDay: Long = 20_000) =
        transactionRepository.addIncomeOrExpense(
            TransactionType.EXPENSE, amountMinor, accountId, expenseCategoryId, dateEpochDay,
        )

    private suspend fun addIncome(amountMinor: Long, dateEpochDay: Long = 20_000) =
        transactionRepository.addIncomeOrExpense(
            TransactionType.INCOME, amountMinor, accountId, incomeCategoryId, dateEpochDay,
        )

    @Test
    fun balanceStartsFromAccountOpeningBalances() = runBlocking {
        assertEquals(100_000L, overviewRepository.observeTotalBalance().first())

        accountRepository.create("Карта", initialBalanceMinor = 50_000)

        assertEquals(150_000L, overviewRepository.observeTotalBalance().first())
    }

    @Test
    fun balanceAddsIncomeAndSubtractsExpense() = runBlocking {
        addIncome(30_000)
        addExpense(12_500)

        // 100 000 + 30 000 - 12 500
        assertEquals(117_500L, overviewRepository.observeTotalBalance().first())
    }

    @Test
    fun transferDoesNotChangeTotalBalance() = runBlocking {
        val other = accountRepository.create("Карта", initialBalanceMinor = 0)
        val before = overviewRepository.observeTotalBalance().first()

        db.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                amountMinor = 40_000,
                accountId = accountId,
                accountToId = other,
                categoryId = null,
                dateEpochDay = 20_000,
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
            ),
        )

        // Moving money between own accounts must not create or destroy any.
        assertEquals(before, overviewRepository.observeTotalBalance().first())
    }

    @Test
    fun softDeletedTransactionsLeaveBalanceAndTotals() = runBlocking {
        addExpense(10_000)
        val id = db.transactionDao().observeAll().first().single().id

        transactionRepository.softDelete(id)

        assertEquals(100_000L, overviewRepository.observeTotalBalance().first())
        assertEquals(0L, overviewRepository.observeTotals(19_000, 21_000).first().expenseMinor)
    }

    @Test
    fun totalsCountOnlyTheRequestedPeriodAndSplitByType() = runBlocking {
        addExpense(1_000, dateEpochDay = 19_999) // before period
        addExpense(2_000, dateEpochDay = 20_000) // first day, inclusive
        addExpense(3_000, dateEpochDay = 20_030) // last day, inclusive
        addExpense(4_000, dateEpochDay = 20_031) // after period
        addIncome(7_000, dateEpochDay = 20_010)

        val totals = overviewRepository.observeTotals(20_000, 20_030).first()

        assertEquals(5_000L, totals.expenseMinor)
        assertEquals(7_000L, totals.incomeMinor)
    }

    @Test
    fun transfersAreExcludedFromMonthTotals() = runBlocking {
        val other = accountRepository.create("Карта", initialBalanceMinor = 0)
        addExpense(2_000, dateEpochDay = 20_000)
        db.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                amountMinor = 99_000,
                accountId = accountId,
                accountToId = other,
                categoryId = null,
                dateEpochDay = 20_000,
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
            ),
        )

        val totals = overviewRepository.observeTotals(20_000, 20_030).first()

        assertEquals(2_000L, totals.expenseMinor)
        assertEquals(0L, totals.incomeMinor)
    }

    @Test
    fun accountBalanceCountsOwnInitialIncomeAndExpense() = runBlocking {
        accountRepository.create("Карта", initialBalanceMinor = 500)
        addIncome(30_000)
        addExpense(12_500)

        val balances = overviewRepository.observeAccountBalances().first()

        // Untouched account keeps just its opening balance.
        assertEquals(500L, balances.single { it.name == "Карта" }.balanceMinor)
        assertEquals(117_500L, balances.single { it.name == "Наличные" }.balanceMinor)
    }

    @Test
    fun transferMovesMoneyBetweenAccountBalances() = runBlocking {
        val other = accountRepository.create("Карта", initialBalanceMinor = 0)
        db.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                amountMinor = 40_000,
                accountId = accountId,
                accountToId = other,
                categoryId = null,
                dateEpochDay = 20_000,
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
            ),
        )

        val balances = overviewRepository.observeAccountBalances().first()

        // Unlike the total, per-account balances must feel the transfer.
        assertEquals(60_000L, balances.single { it.name == "Наличные" }.balanceMinor)
        assertEquals(40_000L, balances.single { it.name == "Карта" }.balanceMinor)
    }

    @Test
    fun accountBalancesSumToTotalEvenWithTransfers() = runBlocking {
        val other = accountRepository.create("Карта", initialBalanceMinor = 25_000)
        addIncome(30_000)
        addExpense(12_500)
        db.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                amountMinor = 40_000,
                accountId = accountId,
                accountToId = other,
                categoryId = null,
                dateEpochDay = 20_000,
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
            ),
        )

        // Archived accounts are excluded from the total, so the invariant is over active ones.
        val perAccountSum = overviewRepository.observeAccountBalances().first()
            .filterNot { it.isArchived }
            .sumOf { it.balanceMinor }

        assertEquals(overviewRepository.observeTotalBalance().first(), perAccountSum)
    }

    @Test
    fun accountBalanceIgnoresSoftDeletedTransactions() = runBlocking {
        addExpense(10_000)
        val id = db.transactionDao().observeAll().first().single().id

        transactionRepository.softDelete(id)

        assertEquals(
            100_000L,
            overviewRepository.observeAccountBalances().first().single().balanceMinor,
        )
    }

    @Test
    fun emptyDatabaseReportsZeroNotNull() = runBlocking {
        val empty = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FinTrackDatabase::class.java,
        ).build()
        val repository = OverviewRepository(empty.overviewDao())

        assertEquals(0L, repository.observeTotalBalance().first())
        assertEquals(0L, repository.observeTotals(0, 99_999).first().expenseMinor)
        assertEquals(0L, repository.observeTotals(0, 99_999).first().incomeMinor)

        empty.close()
    }
}
