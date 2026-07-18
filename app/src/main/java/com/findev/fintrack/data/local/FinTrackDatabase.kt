package com.findev.fintrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.findev.fintrack.data.local.dao.AccountDao
import com.findev.fintrack.data.local.dao.CategoryDao
import com.findev.fintrack.data.local.dao.LoanDao
import com.findev.fintrack.data.local.dao.LoanPrepaymentDao
import com.findev.fintrack.data.local.dao.LoanRateDao
import com.findev.fintrack.data.local.dao.MeterDao
import com.findev.fintrack.data.local.dao.MeterGroupDao
import com.findev.fintrack.data.local.dao.MeterReadingDao
import com.findev.fintrack.data.local.dao.OverviewDao
import com.findev.fintrack.data.local.dao.RecurringPaymentDao
import com.findev.fintrack.data.local.dao.TransactionDao
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.LoanEntity
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.entity.LoanRateEntity
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterGroupEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import com.findev.fintrack.data.local.entity.TransactionEntity

/**
 * Schema is exported to app/schemas; migrations are always written explicitly
 * (no destructive fallback) so data survives upgrades.
 *
 * DAOs are added per feature.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        LoanEntity::class,
        LoanRateEntity::class,
        LoanPrepaymentEntity::class,
        MeterEntity::class,
        MeterReadingEntity::class,
        MeterGroupEntity::class,
        RecurringPaymentEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class FinTrackDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun overviewDao(): OverviewDao
    abstract fun loanDao(): LoanDao
    abstract fun loanRateDao(): LoanRateDao
    abstract fun loanPrepaymentDao(): LoanPrepaymentDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao
    abstract fun meterDao(): MeterDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun meterGroupDao(): MeterGroupDao
}
