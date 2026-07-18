package com.findev.fintrack.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.findev.fintrack.data.local.entity.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeedCategoriesTest {

    private lateinit var db: FinTrackDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinTrackDatabase::class.java)
            .addCallback(SeedCategoriesCallback(context))
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedsDefaultCategoriesOnDatabaseCreation() = runBlocking {
        val categories = db.categoryDao().observeAll().first()

        assertEquals(DEFAULT_CATEGORIES.size, categories.size)
        assertEquals(13, categories.count { it.type == CategoryType.EXPENSE })
        assertEquals(5, categories.count { it.type == CategoryType.INCOME })
        assertTrue(categories.all { it.id.isNotBlank() })
        assertTrue(categories.all { it.icon.isNotBlank() })
        assertTrue(categories.all { it.updatedAt > 0 })
        assertTrue(categories.none { it.isDeleted })
    }

    @Test
    fun observeByTypeReturnsOnlyExpenseCategories() = runBlocking {
        val expense = db.categoryDao().observeByType(CategoryType.EXPENSE).first()

        assertEquals(13, expense.size)
        assertTrue(expense.all { it.type == CategoryType.EXPENSE })
    }
}
