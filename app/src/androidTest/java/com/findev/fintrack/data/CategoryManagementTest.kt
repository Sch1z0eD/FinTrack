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
class CategoryManagementTest {

    private lateinit var db: FinTrackDatabase
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinTrackDatabase::class.java)
            .addCallback(SeedCategoriesCallback(context))
            .build()
        categoryRepository = CategoryRepository(db.categoryDao())
        accountRepository = AccountRepository(db.accountDao())
        transactionRepository = TransactionRepository(db.transactionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun customCategoryAppearsInItsOwnGridOnly() = runBlocking {
        categoryRepository.create("Донаты", CategoryType.INCOME, "🎉", 0xFF4CAF50)

        val income = categoryRepository.observeByType(CategoryType.INCOME).first()
        val expense = categoryRepository.observeByType(CategoryType.EXPENSE).first()

        val created = income.single { it.name == "Донаты" }
        assertEquals("🎉", created.icon)
        assertEquals(0xFF4CAF50, created.color)
        assertFalse(created.isArchived)
        assertTrue(expense.none { it.name == "Донаты" })
    }

    @Test
    fun unusedCategoryCanBeDeleted() = runBlocking {
        val id = categoryRepository.create("Ошибка", CategoryType.EXPENSE, "❌", 0xFF9E9E9E)

        assertTrue(categoryRepository.canDelete(id))
        categoryRepository.delete(id)

        assertTrue(categoryRepository.observeAll().first().none { it.id == id })
    }

    @Test
    fun usedCategoryCannotBeDeleted() = runBlocking<Unit> {
        val accountId = accountRepository.create("Наличные", 0)
        val id = categoryRepository.create("Хобби", CategoryType.EXPENSE, "🎸", 0xFF9C27B0)
        transactionRepository.addIncomeOrExpense(
            TransactionType.EXPENSE, 1_000, accountId, id, 20_000,
        )

        assertFalse(categoryRepository.canDelete(id))
        assertThrows(CategoryInUseException::class.java) {
            runBlocking { categoryRepository.delete(id) }
        }
    }

    @Test
    fun categoryWithOnlySoftDeletedTransactionsStillCannotBeDeleted() = runBlocking {
        val accountId = accountRepository.create("Наличные", 0)
        val id = categoryRepository.create("Хобби", CategoryType.EXPENSE, "🎸", 0xFF9C27B0)
        transactionRepository.addIncomeOrExpense(
            TransactionType.EXPENSE, 1_000, accountId, id, 20_000,
        )
        val transactionId = db.transactionDao().observeAll().first().single().id
        transactionRepository.softDelete(transactionId)

        assertFalse(categoryRepository.canDelete(id))
    }

    @Test
    fun archivedCategoryLeavesTheGridButKeepsLabellingOldTransactions() = runBlocking {
        val accountId = accountRepository.create("Наличные", 0)
        val id = categoryRepository.create("Хобби", CategoryType.EXPENSE, "🎸", 0xFF9C27B0)
        transactionRepository.addIncomeOrExpense(
            TransactionType.EXPENSE, 1_000, accountId, id, 20_000,
        )

        categoryRepository.setArchived(id, archived = true)

        assertTrue(categoryRepository.observeByType(CategoryType.EXPENSE).first().none { it.id == id })
        assertTrue(categoryRepository.observeAll().first().single { it.id == id }.isArchived)

        // The feed must still show what the money was spent on.
        val item = transactionRepository.observeList().first().single()
        assertEquals("Хобби", item.categoryName)
        assertEquals("🎸", item.categoryIcon)
    }

    @Test
    fun seededCategoryCanBeArchivedAndRestored() = runBlocking {
        val seeded = categoryRepository.observeByType(CategoryType.EXPENSE).first()
            .single { it.name == "Образование" }

        categoryRepository.setArchived(seeded.id, archived = true)
        assertEquals(12, categoryRepository.observeByType(CategoryType.EXPENSE).first().size)

        categoryRepository.setArchived(seeded.id, archived = false)
        assertEquals(13, categoryRepository.observeByType(CategoryType.EXPENSE).first().size)
    }

    @Test
    fun editChangesNameIconAndColourAndBumpsUpdatedAt() = runBlocking {
        val id = categoryRepository.create("Хобби", CategoryType.EXPENSE, "🎸", 0xFF9C27B0)
        val before = categoryRepository.observeAll().first().single { it.id == id }
        Thread.sleep(5)

        categoryRepository.update(id, "Музыка", "🎹", 0xFF2196F3)

        val after = categoryRepository.observeAll().first().single { it.id == id }
        assertEquals("Музыка", after.name)
        assertEquals("🎹", after.icon)
        assertEquals(0xFF2196F3, after.color)
        // Type must survive an edit: it decides which grid the category lives in.
        assertEquals(CategoryType.EXPENSE, after.type)
        assertTrue(after.updatedAt > before.updatedAt)
    }
}
