package com.findev.fintrack.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.findev.fintrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

const val UPDATE_CHANNEL_ID = "app_updates"
private const val UPDATE_CHECK_WORK = "update_check_daily"

/**
 * Enqueues the daily update check. Unique + KEEP, so launching the app repeatedly leaves the
 * existing schedule alone rather than stacking jobs.
 */
@Singleton
class UpdateCheckScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UpdateChecks {
    override fun setUp() {
        createChannel()
        schedule()
    }

    fun createChannel() {
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                // Low: a new version is news, not something to interrupt for.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.update_channel_description)
            },
        )
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(Duration.ofDays(1))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UPDATE_CHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
