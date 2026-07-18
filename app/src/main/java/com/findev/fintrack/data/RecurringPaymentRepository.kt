package com.findev.fintrack.data

import com.findev.fintrack.data.local.dao.RecurringPaymentDao
import com.findev.fintrack.data.local.entity.RecurrencePeriod
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/**
 * Recurring obligations. Due dates are not stored - they are derived from the start date
 * and the period, the same way a loan's schedule is derived rather than kept.
 */
class RecurringPaymentRepository @Inject constructor(
    private val recurringPaymentDao: RecurringPaymentDao,
) {
    fun observeAll(): Flow<List<RecurringPaymentEntity>> = recurringPaymentDao.observeAll()

    suspend fun getById(id: String): RecurringPaymentEntity? = recurringPaymentDao.getById(id)

    /** Reminder rebuilds see deleted rows too - see [RecurringPaymentDao.observeAllForReminders]. */
    fun observeAllForReminders(): Flow<List<RecurringPaymentEntity>> =
        recurringPaymentDao.observeAllForReminders()

    suspend fun getAllForReminders(): List<RecurringPaymentEntity> =
        recurringPaymentDao.getAllForReminders()

    suspend fun create(
        name: String,
        amountMinor: Long,
        period: RecurrencePeriod,
        startDateEpochDay: Long,
        endDateEpochDay: Long?,
        accountId: String,
        categoryId: String,
        reminderEnabled: Boolean,
    ): String {
        val id = UUID.randomUUID().toString()
        recurringPaymentDao.insert(
            RecurringPaymentEntity(
                id = id,
                name = name,
                amountMinor = amountMinor,
                period = period,
                startDateEpochDay = startDateEpochDay,
                endDateEpochDay = endDateEpochDay,
                accountId = accountId,
                categoryId = categoryId,
                reminderEnabled = reminderEnabled,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun update(
        id: String,
        name: String,
        amountMinor: Long,
        period: RecurrencePeriod,
        startDateEpochDay: Long,
        endDateEpochDay: Long?,
        accountId: String,
        categoryId: String,
        reminderEnabled: Boolean,
    ) {
        val existing = requireNotNull(recurringPaymentDao.getById(id)) {
            "No recurring payment with id $id"
        }
        recurringPaymentDao.update(
            existing.copy(
                name = name,
                amountMinor = amountMinor,
                period = period,
                startDateEpochDay = startDateEpochDay,
                endDateEpochDay = endDateEpochDay,
                accountId = accountId,
                categoryId = categoryId,
                reminderEnabled = reminderEnabled,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) =
        recurringPaymentDao.softDelete(id, System.currentTimeMillis())

}
