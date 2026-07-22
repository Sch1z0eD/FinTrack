package com.findev.fintrack.data

import com.findev.fintrack.data.local.dao.CategoryDao
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/** Deleting a category that is still used would strip existing transactions of their label. */
class CategoryInUseException : IllegalStateException("Category is still used by transactions")

class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    /** Archived included - for the categories screen. */
    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    /** A single category by id, or null if it was deleted - used by the budget check. */
    suspend fun getById(id: String): CategoryEntity? = categoryDao.getById(id)

    /** For the quick-entry grid. */
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(type)

    /**
     * The category a utility bill is booked to. Matched by the seeded 💡 icon rather than a
     * stored id (there is none) or a name (the user may translate or rename it); if it was
     * re-iconed or deleted, any expense category is a better place than none.
     */
    suspend fun utilitiesCategoryId(): String? {
        val expense = categoryDao.getByType(CategoryType.EXPENSE)
        return (expense.firstOrNull { it.icon == "💡" } ?: expense.firstOrNull())?.id
    }

    suspend fun create(
        name: String,
        type: CategoryType,
        icon: String,
        color: Long,
        monthlyLimitMinor: Long? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        categoryDao.insert(
            CategoryEntity(
                id = id,
                name = name,
                type = type,
                icon = icon,
                color = color,
                // A limit only makes sense against spending; income categories never carry one.
                monthlyLimitMinor = monthlyLimitMinor.takeIf { type == CategoryType.EXPENSE },
                // New categories go to the end of their type's manual order.
                position = categoryDao.nextPosition(type),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /**
     * Writes [orderedIds] as the new order, renumbering to 0..n-1. The caller passes the full
     * desired order of one type's categories; positions stay contiguous within the type.
     */
    suspend fun reorder(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            categoryDao.updatePosition(id, index, now)
        }
    }

    /** Type is intentionally not editable: it would move the category between grids. */
    suspend fun update(
        id: String,
        name: String,
        icon: String,
        color: Long,
        monthlyLimitMinor: Long? = null,
    ) {
        val existing = requireNotNull(categoryDao.getById(id)) { "No category with id $id" }
        categoryDao.update(
            existing.copy(
                name = name,
                icon = icon,
                color = color,
                monthlyLimitMinor = monthlyLimitMinor.takeIf { existing.type == CategoryType.EXPENSE },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val existing = requireNotNull(categoryDao.getById(id)) { "No category with id $id" }
        categoryDao.update(
            existing.copy(isArchived = archived, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun canDelete(id: String): Boolean = categoryDao.countTransactions(id) == 0

    suspend fun delete(id: String) {
        if (!canDelete(id)) throw CategoryInUseException()
        categoryDao.softDelete(id, System.currentTimeMillis())
    }
}
