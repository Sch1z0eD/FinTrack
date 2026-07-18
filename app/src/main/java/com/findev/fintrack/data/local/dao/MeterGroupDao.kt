package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.entity.MeterGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterGroupDao {

    @Query("SELECT * FROM meter_group WHERE is_deleted = 0 ORDER BY position, name")
    fun observeAll(): Flow<List<MeterGroupEntity>>

    @Query("SELECT * FROM meter_group WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): MeterGroupEntity?

    @Query("SELECT * FROM meter_group WHERE is_deleted = 0 ORDER BY position DESC LIMIT 1")
    suspend fun lastByPosition(): MeterGroupEntity?

    @Insert
    suspend fun insert(group: MeterGroupEntity)

    @Update
    suspend fun update(group: MeterGroupEntity)

    @Query("UPDATE meter_group SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

    /** When a group is removed, its services are not: they fall back to «Прочее». */
    @Query("UPDATE meter SET group_id = NULL, updated_at = :updatedAt WHERE group_id = :groupId")
    suspend fun detachMeters(groupId: String, updatedAt: Long)
}
