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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the write path the quick-entry screen uses: account -> category -> transaction. */
@RunWith(AndroidJUnit4::class)
class QuickEntryPersistenceTest {

    private lateinit var db: FinTrackDatabase
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinTrackDatabase::class.java)
            .addCallback(SeedCategoriesCallback(context))
            .build()
        accountRepository = AccountRepository(db.accountDao())
        transactionRepository = TransactionRepository(db.transactionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun savesExpenseAgainstAccountAndCategory() = runBlocking {
        val accountId = accountRepository.create("Наличные", initialBalanceMinor = 100_000)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()

        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = 1_500,
            accountId = accountId,
            categoryId = category.id,
            dateEpochDay = 20_000,
        )

        val saved = db.transactionDao().observeAll().first()
        assertEquals(1, saved.size)
        with(saved.single()) {
            assertEquals(TransactionType.EXPENSE, type)
            assertEquals(1_500L, amountMinor)
            assertEquals(accountId, this.accountId)
            assertEquals(category.id, categoryId)
            assertEquals(20_000L, dateEpochDay)
            // Only transfers carry a destination account.
            assertNull(accountToId)
            assertTrue(updatedAt > 0)
            assertTrue(id.isNotBlank())
        }
    }

    @Test
    fun createdAccountsAreObservableAndDistinct() = runBlocking {
        accountRepository.create("Наличные", initialBalanceMinor = 0)
        accountRepository.create("Карта Сбер", initialBalanceMinor = 250_000)

        val accounts = accountRepository.observeAll().first()

        assertEquals(2, accounts.size)
        assertEquals(2, accounts.map { it.id }.toSet().size)
        assertEquals(250_000L, accounts.single { it.name == "Карта Сбер" }.initialBalanceMinor)
    }

    @Test
    fun noteIsStoredAndSurfacedInTheFeed() = runBlocking {
        val accountId = accountRepository.create("Наличные", 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()

        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountId,
            categoryId = category.id,
            dateEpochDay = 20_000,
            note = "Кофе с Ромой",
        )

        assertEquals("Кофе с Ромой", db.transactionDao().observeAll().first().single().note)
        assertEquals("Кофе с Ромой", transactionRepository.observeList().first().single().note)
    }

    @Test
    fun noteIsNullWhenNotGiven() = runBlocking {
        val accountId = accountRepository.create("Наличные", 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()

        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountId,
            categoryId = category.id,
            dateEpochDay = 20_000,
        )

        assertNull(db.transactionDao().observeAll().first().single().note)
    }

    @Test
    fun editCanAddChangeAndClearTheNote() = runBlocking {
        val accountId = accountRepository.create("Наличные", 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()
        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountId,
            categoryId = category.id,
            dateEpochDay = 20_000,
        )
        val id = db.transactionDao().observeAll().first().single().id

        fun edit(note: String?) = runBlocking {
            transactionRepository.updateIncomeOrExpense(
                id = id,
                type = TransactionType.EXPENSE,
                amountMinor = 1_000,
                accountId = accountId,
                categoryId = category.id,
                dateEpochDay = 20_000,
                note = note,
            )
        }

        edit("Такси")
        assertEquals("Такси", db.transactionDao().observeAll().first().single().note)

        edit("Такси до дома")
        assertEquals("Такси до дома", db.transactionDao().observeAll().first().single().note)

        // Clearing must reach the database, not leave the old note behind.
        edit(null)
        assertNull(db.transactionDao().observeAll().first().single().note)
    }

    @Test
    fun editReplacesFieldsKeepsIdentityAndBumpsUpdatedAt() = runBlocking {
        val cash = accountRepository.create("Наличные", 0)
        val card = accountRepository.create("Карта", 0)
        val expenseCategory = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()
        val incomeCategory = db.categoryDao().observeByType(CategoryType.INCOME).first().first()

        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = cash,
            categoryId = expenseCategory.id,
            dateEpochDay = 20_000,
        )
        val original = db.transactionDao().observeAll().first().single()
        Thread.sleep(5)

        transactionRepository.updateIncomeOrExpense(
            id = original.id,
            type = TransactionType.INCOME,
            amountMinor = 7_777,
            accountId = card,
            categoryId = incomeCategory.id,
            dateEpochDay = 20_005,
        )

        val edited = db.transactionDao().observeAll().first().single()
        assertEquals("edit must not create a second row", original.id, edited.id)
        assertEquals(TransactionType.INCOME, edited.type)
        assertEquals(7_777L, edited.amountMinor)
        assertEquals(card, edited.accountId)
        assertEquals(incomeCategory.id, edited.categoryId)
        assertEquals(20_005L, edited.dateEpochDay)
        assertTrue("updated_at must advance for sync", edited.updatedAt > original.updatedAt)
    }

    @Test
    fun editRejectsInvalidInput() = runBlocking {
        val accountId = accountRepository.create("Наличные", 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()
        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountId,
            categoryId = category.id,
            dateEpochDay = 20_000,
        )
        val id = db.transactionDao().observeAll().first().single().id

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionRepository.updateIncomeOrExpense(
                    id = id,
                    type = TransactionType.EXPENSE,
                    amountMinor = 0,
                    accountId = accountId,
                    categoryId = category.id,
                    dateEpochDay = 20_000,
                )
            }
        }
        // A rejected edit must leave the stored row untouched.
        assertEquals(1_000L, db.transactionDao().observeAll().first().single().amountMinor)
    }

    @Test
    fun editFailsForUnknownId() = runBlocking<Unit> {
        val accountId = accountRepository.create("Наличные", 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionRepository.updateIncomeOrExpense(
                    id = "does-not-exist",
                    type = TransactionType.EXPENSE,
                    amountMinor = 500,
                    accountId = accountId,
                    categoryId = category.id,
                    dateEpochDay = 20_000,
                )
            }
        }
    }

    @Test
    fun rejectsNonPositiveAmount() = runBlocking {
        val accountId = accountRepository.create("Наличные", initialBalanceMinor = 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionRepository.addIncomeOrExpense(
                    type = TransactionType.EXPENSE,
                    amountMinor = 0,
                    accountId = accountId,
                    categoryId = category.id,
                    dateEpochDay = 20_000,
                )
            }
        }
        assertTrue(db.transactionDao().observeAll().first().isEmpty())
    }

    @Test
    fun rejectsTransferThroughIncomeExpenseApi() = runBlocking<Unit> {
        val accountId = accountRepository.create("Наличные", initialBalanceMinor = 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionRepository.addIncomeOrExpense(
                    type = TransactionType.TRANSFER,
                    amountMinor = 100,
                    accountId = accountId,
                    categoryId = category.id,
                    dateEpochDay = 20_000,
                )
            }
        }
    }
}
