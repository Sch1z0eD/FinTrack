package com.findev.fintrack.update

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * rustore flavor: there is no update check. RuStore delivers updates itself and forbids apps
 * that fetch and install their own, so this build must never schedule the check.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateChecksModule {
    @Provides
    @Singleton
    fun provideUpdateChecks(): UpdateChecks = object : UpdateChecks {
        override fun setUp() = Unit
    }
}
