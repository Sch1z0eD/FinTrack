package com.findev.fintrack.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** github flavor: the update check is the real GitHub-release scheduler. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateChecksModule {
    @Binds
    @Singleton
    abstract fun bindUpdateChecks(impl: UpdateCheckScheduler): UpdateChecks
}
