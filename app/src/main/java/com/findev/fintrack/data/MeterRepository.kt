package com.findev.fintrack.data

import com.findev.fintrack.data.local.dao.MeterDao
import com.findev.fintrack.data.local.dao.MeterReadingDao
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
import com.findev.fintrack.data.local.entity.MeterType
import com.findev.fintrack.data.local.entity.reminderDaysToStored
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class MeterRepository @Inject constructor(
    private val meterDao: MeterDao,
    private val meterReadingDao: MeterReadingDao,
) {
    fun observeAll(): Flow<List<MeterEntity>> = meterDao.observeAll()

    fun observeAllReadings(): Flow<List<MeterReadingEntity>> = meterReadingDao.observeAll()

    fun observeReadings(meterId: String): Flow<List<MeterReadingEntity>> =
        meterReadingDao.observeByMeter(meterId)

    suspend fun getById(id: String): MeterEntity? = meterDao.getById(id)

    suspend fun getAll(): List<MeterEntity> = meterDao.getAll()

    suspend fun getLatestReading(meterId: String): MeterReadingEntity? =
        meterReadingDao.getLatest(meterId)

    suspend fun create(
        name: String,
        type: MeterType,
        billing: BillingKind,
        tariffMinor: Long,
        drainageTariffMinor: Long,
        normMilli: Long,
        paymentDay: Int,
        reminderDays: List<Int>,
        groupId: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        meterDao.insert(
            MeterEntity(
                id = id,
                name = name,
                type = type,
                billing = billing,
                tariffMinor = tariffMinor,
                drainageTariffMinor = drainageTariffMinor,
                normMilli = normMilli,
                paymentDay = paymentDay,
                reminderDays = reminderDaysToStored(reminderDays),
                groupId = groupId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /**
     * Changing the tariff here only affects readings entered from now on. Past readings
     * keep the snapshot they were charged at - indexation must not rewrite history.
     */
    suspend fun update(
        id: String,
        name: String,
        type: MeterType,
        billing: BillingKind,
        tariffMinor: Long,
        drainageTariffMinor: Long,
        normMilli: Long,
        paymentDay: Int,
        reminderDays: List<Int>,
        groupId: String? = null,
    ) {
        val existing = requireNotNull(meterDao.getById(id)) { "No meter with id $id" }
        meterDao.update(
            existing.copy(
                name = name,
                type = type,
                billing = billing,
                tariffMinor = tariffMinor,
                drainageTariffMinor = drainageTariffMinor,
                normMilli = normMilli,
                paymentDay = paymentDay,
                reminderDays = reminderDaysToStored(reminderDays),
                groupId = groupId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Records a reading with the tariff and charge it was billed at.
     *
     * Both are snapshots, computed here and stored: tariffs are indexed (typically 1 July),
     * and recomputing an old month against today's tariff would rewrite what was actually
     * paid. The first reading of a meter only anchors where counting starts - see
     * [readingChargeMinor].
     */
    suspend fun addReading(meterId: String, valueMilli: Long, dateEpochDay: Long): String {
        val meter = requireNotNull(meterDao.getById(meterId)) { "No meter with id $meterId" }
        require(meter.billing == BillingKind.METERED) {
            "Meter $meterId is not metered and has no readings"
        }
        val previous = meterReadingDao.getLatest(meterId)

        val id = UUID.randomUUID().toString()
        meterReadingDao.insert(
            MeterReadingEntity(
                id = id,
                meterId = meterId,
                valueMilli = valueMilli,
                dateEpochDay = dateEpochDay,
                tariffMinor = meter.tariffMinor,
                drainageTariffMinor = meter.drainageTariffMinor,
                amountMinor = readingChargeMinor(
                    previous?.valueMilli,
                    valueMilli,
                    meter.tariffMinor,
                    meter.drainageTariffMinor,
                ),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun delete(id: String) = meterDao.softDelete(id, System.currentTimeMillis())

    suspend fun deleteReading(id: String) =
        meterReadingDao.softDelete(id, System.currentTimeMillis())
}
