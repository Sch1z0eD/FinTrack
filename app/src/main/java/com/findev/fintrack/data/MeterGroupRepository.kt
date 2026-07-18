package com.findev.fintrack.data

import com.findev.fintrack.data.local.dao.MeterGroupDao
import com.findev.fintrack.data.local.entity.MeterGroupEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class MeterGroupRepository @Inject constructor(
    private val meterGroupDao: MeterGroupDao,
) {
    fun observeAll(): Flow<List<MeterGroupEntity>> = meterGroupDao.observeAll()

    suspend fun getById(id: String): MeterGroupEntity? = meterGroupDao.getById(id)

    /** New groups go to the end of the list. */
    suspend fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        val position = (meterGroupDao.lastByPosition()?.position ?: -1) + 1
        meterGroupDao.insert(
            MeterGroupEntity(
                id = id,
                name = name,
                position = position,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun rename(id: String, name: String) {
        val existing = requireNotNull(meterGroupDao.getById(id)) { "No group with id $id" }
        meterGroupDao.update(existing.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    /**
     * The group goes; its services do not. They are detached to «Прочее» rather than
     * deleted - removing a grouping must never remove what was grouped.
     */
    suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        meterGroupDao.detachMeters(id, now)
        meterGroupDao.softDelete(id, now)
    }
}
