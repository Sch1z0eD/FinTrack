package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** Everything the user still has, archived included - for the categories screen. */
    @Query("SELECT * FROM category WHERE is_deleted = 0 ORDER BY is_archived, position, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** Only categories offered in the quick-entry grid, in the user's chosen order. */
    @Query(
        "SELECT * FROM category WHERE is_deleted = 0 AND is_archived = 0 AND type = :type " +
            "ORDER BY position, name",
    )
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query(
        "SELECT * FROM category WHERE is_deleted = 0 AND is_archived = 0 AND type = :type " +
            "ORDER BY position, name",
    )
    suspend fun getByType(type: CategoryType): List<CategoryEntity>

    /** Next free sort position within a type, so a new category lands at the end of its grid. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category WHERE is_deleted = 0 AND type = :type")
    suspend fun nextPosition(type: CategoryType): Int

    @Query("UPDATE category SET position = :position, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int, updatedAt: Long)

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    /** Soft-deleted transactions count too: undo must never orphan them. */
    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun countTransactions(id: String): Int

    @Query("UPDATE category SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)
}
