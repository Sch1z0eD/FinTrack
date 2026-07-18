package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringPaymentDao {

    @Query("SELECT * FROM recurring_payment WHERE is_deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<RecurringPaymentEntity>>

    /**
     * Deleted rows included, on purpose: the reminder rebuild has to cancel the alarm of a
     * payment that has just gone away, and a row filtered out of the list is a row whose
     * alarm nobody would ever cancel.
     */
    @Query("SELECT * FROM recurring_payment")
    fun observeAllForReminders(): Flow<List<RecurringPaymentEntity>>

    /** Same, one-shot: the alarm rebuild has no UI to observe from. */
    @Query("SELECT * FROM recurring_payment")
    suspend fun getAllForReminders(): List<RecurringPaymentEntity>

    @Query("SELECT * FROM recurring_payment WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): RecurringPaymentEntity?

    @Insert
    suspend fun insert(payment: RecurringPaymentEntity)

    @Update
    suspend fun update(payment: RecurringPaymentEntity)

    @Query("UPDATE recurring_payment SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

}
