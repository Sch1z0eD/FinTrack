package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {

    @Query("SELECT * FROM meter WHERE is_deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<MeterEntity>>

    @Query("SELECT * FROM meter WHERE is_deleted = 0")
    suspend fun getAll(): List<MeterEntity>

    @Query("SELECT * FROM meter WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): MeterEntity?

    @Insert
    suspend fun insert(meter: MeterEntity)

    @Update
    suspend fun update(meter: MeterEntity)

    @Query("UPDATE meter SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)
}

@Dao
interface MeterReadingDao {

    @Query(
        "SELECT * FROM meter_reading WHERE meter_id = :meterId AND is_deleted = 0 " +
            "ORDER BY date_epoch_day DESC, updated_at DESC",
    )
    fun observeByMeter(meterId: String): Flow<List<MeterReadingEntity>>

    /** Every meter's readings at once: the list screen needs them all, not N+1 queries. */
    @Query("SELECT * FROM meter_reading WHERE is_deleted = 0 ORDER BY date_epoch_day DESC, updated_at DESC")
    fun observeAll(): Flow<List<MeterReadingEntity>>

    /**
     * The reading a new one is measured against. Ordered by date first, then entry time,
     * so two readings on the same day still have a defined "latest".
     */
    @Query(
        "SELECT * FROM meter_reading WHERE meter_id = :meterId AND is_deleted = 0 " +
            "ORDER BY date_epoch_day DESC, updated_at DESC LIMIT 1",
    )
    suspend fun getLatest(meterId: String): MeterReadingEntity?

    @Insert
    suspend fun insert(reading: MeterReadingEntity)

    @Query("UPDATE meter_reading SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)
}
