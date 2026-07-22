package com.findev.fintrack.di

import android.content.Context
import androidx.room.Room
import com.findev.fintrack.data.local.FinTrackDatabase
import com.findev.fintrack.data.local.MIGRATION_1_2
import com.findev.fintrack.data.local.MIGRATION_2_3
import com.findev.fintrack.data.local.MIGRATION_3_4
import com.findev.fintrack.data.local.MIGRATION_4_5
import com.findev.fintrack.data.local.MIGRATION_5_6
import com.findev.fintrack.data.local.MIGRATION_6_7
import com.findev.fintrack.data.local.MIGRATION_7_8
import com.findev.fintrack.data.local.MIGRATION_8_9
import com.findev.fintrack.data.local.MIGRATION_9_10
import com.findev.fintrack.data.local.SeedCategoriesCallback
import com.findev.fintrack.data.local.dao.AccountDao
import com.findev.fintrack.data.local.dao.CategoryDao
import com.findev.fintrack.data.local.dao.LoanDao
import com.findev.fintrack.data.local.dao.LoanPrepaymentDao
import com.findev.fintrack.data.local.dao.MeterDao
import com.findev.fintrack.data.local.dao.MeterGroupDao
import com.findev.fintrack.data.local.dao.MeterReadingDao
import com.findev.fintrack.data.local.dao.RecurringPaymentDao
import com.findev.fintrack.data.local.dao.LoanRateDao
import com.findev.fintrack.data.local.dao.OverviewDao
import com.findev.fintrack.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinTrackDatabase =
        // No fallbackToDestructiveMigration: migrations are always explicit.
        Room.databaseBuilder(context, FinTrackDatabase::class.java, "fintrack.db")
            .addCallback(SeedCategoriesCallback(context))
            // No destructive fallback on purpose: a missing migration must fail loudly
            // rather than quietly wipe the only copy of the user's data.
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            )
            .build()

    @Provides
    fun provideCategoryDao(database: FinTrackDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideAccountDao(database: FinTrackDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideTransactionDao(database: FinTrackDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideOverviewDao(database: FinTrackDatabase): OverviewDao = database.overviewDao()

    @Provides
    fun provideLoanDao(database: FinTrackDatabase): LoanDao = database.loanDao()

    @Provides
    fun provideLoanRateDao(database: FinTrackDatabase): LoanRateDao = database.loanRateDao()

    @Provides
    fun provideLoanPrepaymentDao(database: FinTrackDatabase): LoanPrepaymentDao =
        database.loanPrepaymentDao()

    @Provides
    fun provideRecurringPaymentDao(database: FinTrackDatabase): RecurringPaymentDao =
        database.recurringPaymentDao()

    @Provides
    fun provideMeterDao(database: FinTrackDatabase): MeterDao = database.meterDao()

    @Provides
    fun provideMeterReadingDao(database: FinTrackDatabase): MeterReadingDao =
        database.meterReadingDao()

    @Provides
    fun provideMeterGroupDao(database: FinTrackDatabase): MeterGroupDao =
        database.meterGroupDao()
}
