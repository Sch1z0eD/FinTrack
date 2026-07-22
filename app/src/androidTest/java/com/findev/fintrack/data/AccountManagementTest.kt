package com.findev.fintrack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.findev.fintrack.data.local.FinTrackDatabase
import com.findev.fintrack.data.local.SeedCategoriesCallback
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountManagementTest {

    private lateinit var db: FinTrackDatabase
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var overviewRepository: OverviewRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinTrackDatabase::class.java)
            .addCallback(SeedCategoriesCallback(context))
            .build()
        accountRepository = AccountRepository(db.accountDao())
        transactionRepository = TransactionRepository(db.transactionDao())
        overviewRepository = OverviewRepository(db.overviewDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addExpense(accountId: String, amountMinor: Long = 1_000) {
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()
        transactionRepository.addIncomeOrExpense(
            TransactionType.EXPENSE, amountMinor, accountId, category.id, 20_000,
        )
    }

    @Test
    fun emptyAccountCanBeDeleted() = runBlocking {
        val id = accountRepository.create("Ошибка", 0)

        assertTrue(accountRepository.canDelete(id))
        accountRepository.delete(id)

        assertTrue(accountRepository.observeAll().first().isEmpty())
    }

    @Test
    fun accountWithTransactionsCannotBeDeleted() = runBlocking {
        val id = accountRepository.create("Наличные", 100_000)
        addExpense(id)

        assertFalse(accountRepository.canDelete(id))
        assertThrows(AccountHasTransactionsException::class.java) {
            runBlocking { accountRepository.delete(id) }
        }
        assertEquals(1, accountRepository.observeAll().first().size)
    }

    @Test
    fun accountBecomesDeletableOnceItsTransactionsAreDeleted() = runBlocking {
        val id = accountRepository.create("Наличные", 0)
        addExpense(id)
        assertFalse(accountRepository.canDelete(id))

        val transactionId = db.transactionDao().observeAll().first().single().id
        transactionRepository.softDelete(transactionId)

        // The user deleted the operations, so the account is empty as far as they can see.
        assertTrue(accountRepository.canDelete(id))
        accountRepository.delete(id)
        assertTrue(accountRepository.observeAll().first().isEmpty())
    }

    @Test
    fun undoCannotResurrectATransactionOntoADeletedAccount() = runBlocking {
        val id = accountRepository.create("Наличные", 0)
        addExpense(id)
        val transactionId = db.transactionDao().observeAll().first().single().id
        transactionRepository.softDelete(transactionId)
        accountRepository.delete(id)

        transactionRepository.restore(transactionId)

        // Otherwise it would count towards the total while belonging to no listed account.
        assertTrue(transactionRepository.observeList().first().isEmpty())
        assertEquals(0L, overviewRepository.observeTotalBalance().first())
    }

    @Test
    fun archivedAccountLeavesTheTotalButKeepsItsOwnBalance() = runBlocking {
        val cash = accountRepository.create("Наличные", 100_000)
        val card = accountRepository.create("Карта", 50_000)
        addExpense(card, 10_000)

        accountRepository.setArchived(card, archived = true)

        // Not offered for new transactions...
        val active = accountRepository.observeActive().first()
        assertEquals(listOf("Наличные"), active.map { it.name })

        // ...still listed with its own money...
        val balances = overviewRepository.observeAccountBalances().first()
        assertEquals(2, balances.size)
        assertTrue(balances.single { it.id == card }.isArchived)
        assertEquals(40_000L, balances.single { it.id == card }.balanceMinor)
        assertEquals(100_000L, balances.single { it.id == cash }.balanceMinor)

        // ...but a closed account's money is not money on hand.
        assertEquals(100_000L, overviewRepository.observeTotalBalance().first())
        assertEquals(
            overviewRepository.observeTotalBalance().first(),
            balances.filterNot { it.isArchived }.sumOf { it.balanceMinor },
        )
    }

    @Test
    fun transferToAnArchivedAccountLowersTheTotal() = runBlocking {
        val cash = accountRepository.create("Наличные", 100_000)
        val card = accountRepository.create("Карта", 0)
        accountRepository.setArchived(card, archived = true)

        db.transactionDao().insert(
            com.findev.fintrack.data.local.entity.TransactionEntity(
                id = java.util.UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                amountMinor = 30_000,
                accountId = cash,
                accountToId = card,
                categoryId = null,
                dateEpochDay = 20_000,
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
            ),
        )

        // Money moved out of the active accounts, so it must leave the total.
        assertEquals(70_000L, overviewRepository.observeTotalBalance().first())
    }

    @Test
    fun archivingIsReversible() = runBlocking {
        val id = accountRepository.create("Карта", 0)

        accountRepository.setArchived(id, archived = true)
        assertTrue(accountRepository.observeActive().first().isEmpty())

        accountRepository.setArchived(id, archived = false)
        assertEquals(1, accountRepository.observeActive().first().size)
    }

    @Test
    fun renameChangesNameAndOpeningBalanceAndBumpsUpdatedAt() = runBlocking {
        val id = accountRepository.create("Карта", 50_000)
        val before = accountRepository.observeAll().first().single()
        Thread.sleep(5)

        accountRepository.rename(id, "Карта Сбер", 75_000)

        val after = accountRepository.observeAll().first().single()
        assertEquals(id, after.id)
        assertEquals("Карта Сбер", after.name)
        assertEquals(75_000L, after.initialBalanceMinor)
        assertTrue(after.updatedAt > before.updatedAt)
        // Opening balance is part of the balance, so it must follow through.
        assertEquals(75_000L, overviewRepository.observeTotalBalance().first())
    }

    @Test
    fun deletedAccountLeavesTheTotalBalance() = runBlocking {
        accountRepository.create("Наличные", 100_000)
        val card = accountRepository.create("Карта", 50_000)

        accountRepository.delete(card)

        assertEquals(100_000L, overviewRepository.observeTotalBalance().first())
        assertEquals(1, overviewRepository.observeAccountBalances().first().size)
    }
}
