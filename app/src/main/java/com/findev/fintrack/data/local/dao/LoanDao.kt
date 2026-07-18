package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Query("SELECT * FROM loan WHERE is_deleted = 0 ORDER BY start_date_epoch_day DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loan WHERE id = :id AND is_deleted = 0")
    fun observeById(id: String): Flow<LoanEntity?>

    @Query("SELECT * FROM loan WHERE id = :id")
    suspend fun getById(id: String): LoanEntity?

    @Insert
    suspend fun insert(loan: LoanEntity)

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("UPDATE loan SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

}
