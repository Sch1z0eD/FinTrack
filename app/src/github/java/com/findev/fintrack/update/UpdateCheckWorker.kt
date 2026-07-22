package com.findev.fintrack.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.findev.fintrack.MainActivity
import com.findev.fintrack.R
import com.findev.fintrack.data.AvailableUpdate
import com.findev.fintrack.data.SettingsRepository
import com.findev.fintrack.data.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

private const val UPDATE_NOTIFICATION_TAG = "app_update"
private const val UPDATE_NOTIFICATION_ID = 1

/**
 * Daily check for a newer release, when the user has switched it on.
 *
 * The switch is read here rather than used to cancel the job: the work is a cheap flag read
 * when disabled, and keeping one always-enqueued job means the schedule cannot drift out of
 * sync with the preference through a missed cancellation.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.autoUpdateCheck.first()) return Result.success()

        val update = updateRepository.fetchLatest().getOrElse {
            // Offline, or GitHub rate-limited us. Not worth a notification; the next daily
            // run will try again.
            return Result.success()
        } ?: return Result.success()

        notify(update)
        return Result.success()
    }

    private fun notify(update: AvailableUpdate) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_START_ROUTE, com.findev.fintrack.ui.navigation.SETTINGS_ROUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(
                context.getString(R.string.update_notification_text, update.versionName),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context)
            .notify(UPDATE_NOTIFICATION_TAG, UPDATE_NOTIFICATION_ID, notification)
    }
}
