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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Covers the feed: joined display data, day ordering and soft delete / undo. */
@RunWith(AndroidJUnit4::class)
class TransactionFeedTest {

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

    private suspend fun seedExpense(dateEpochDay: Long, amountMinor: Long = 1_000): String {
        val accountId = accountRepository.create("Наличные", 0)
        val category = db.categoryDao().observeByType(CategoryType.EXPENSE).first().first()
        transactionRepository.addIncomeOrExpense(
            type = TransactionType.EXPENSE,
            amountMinor = amountMinor,
            accountId = accountId,
            categoryId = category.id,
            dateEpochDay = dateEpochDay,
        )
        return transactionRepository.observeList().first().first { it.dateEpochDay == dateEpochDay }.id
    }

    @Test
    fun listItemCarriesCategoryAndAccountNames() = runBlocking {
        seedExpense(dateEpochDay = 20_000, amountMinor = 25_050)

        val item = transactionRepository.observeList().first().single()

        assertEquals("Наличные", item.accountName)
        assertNotNull(item.categoryName)
        assertNotNull(item.categoryIcon)
        assertNotNull(item.categoryColor)
        assertEquals(25_050L, item.amountMinor)
    }

    @Test
    fun feedIsOrderedByDayDescending() = runBlocking {
        seedExpense(dateEpochDay = 20_000)
        seedExpense(dateEpochDay = 20_002)
        seedExpense(dateEpochDay = 20_001)

        val days = transactionRepository.observeList().first().map { it.dateEpochDay }

        assertEquals(listOf(20_002L, 20_001L, 20_000L), days)
    }

    @Test
    fun softDeleteHidesRowAndUndoRestoresIt() = runBlocking {
        val id = seedExpense(dateEpochDay = 20_000)

        transactionRepository.softDelete(id)
        assertTrue(transactionRepository.observeList().first().isEmpty())

        // The row must survive the delete so undo (and future sync) can see it.
        val stored = db.transactionDao().observeAll()
        assertTrue(stored.first().isEmpty())

        transactionRepository.restore(id)
        val restored = transactionRepository.observeList().first()
        assertEquals(1, restored.size)
        assertEquals(id, restored.single().id)
    }

    @Test
    fun softDeleteBumpsUpdatedAtForSync() = runBlocking {
        val id = seedExpense(dateEpochDay = 20_000)
        val before = transactionRepository.observeList().first().single()
        val beforeUpdatedAt = db.transactionDao().observeAll().first().single().updatedAt
        assertEquals(id, before.id)

        Thread.sleep(5)
        transactionRepository.softDelete(id)
        transactionRepository.restore(id)

        val afterUpdatedAt = db.transactionDao().observeAll().first().single().updatedAt
        assertTrue("updated_at must advance on soft delete", afterUpdatedAt > beforeUpdatedAt)
    }

    @Test
    fun transferAppearsWithoutCategory() = runBlocking {
        val from = accountRepository.create("Наличные", 0)
        val to = accountRepository.create("Карта", 0)
        db.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                amountMinor = 5_000,
                accountId = from,
                accountToId = to,
                categoryId = null,
                dateEpochDay = 20_000,
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
            ),
        )

        val item = transactionRepository.observeList().first().single()

        assertEquals(TransactionType.TRANSFER, item.type)
        assertEquals("Наличные", item.accountName)
        // LEFT JOIN must keep the row even though it has no category.
        assertNull(item.categoryName)
        assertNull(item.categoryIcon)
    }
}
