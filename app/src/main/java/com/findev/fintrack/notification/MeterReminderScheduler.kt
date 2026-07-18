package com.findev.fintrack.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.findev.fintrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val METER_REMINDER_CHANNEL_ID = "meter_reminders"
private const val METER_REMINDER_WORK = "meter_reminder_daily"

/** Late morning, like the payment reminder - a nudge, not an alarm clock. */
private val REMINDER_TIME: LocalTime = LocalTime.of(10, 0)

/**
 * Enqueues the once-a-day check that reminds about meter readings.
 *
 * The work is periodic and keyed by a unique name with KEEP, so re-running this on every
 * launch does not stack duplicate jobs - it just leaves the existing schedule in place.
 * The decision of *which* meters are due lives in the worker; this only makes sure it runs.
 */
@Singleton
class MeterReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                METER_REMINDER_CHANNEL_ID,
                context.getString(R.string.meter_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.meter_reminder_channel_description)
            },
        )
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<MeterReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(initialDelay())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            METER_REMINDER_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Aims the first run at the next 10:00 so the daily notification lands mid-morning
     * rather than whenever the app first happened to launch.
     */
    private fun initialDelay(): Duration {
        val now = LocalDate.now().atTime(LocalTime.now())
        var next = LocalDate.now().atTime(REMINDER_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val zone = ZoneId.systemDefault()
        return Duration.between(now.atZone(zone), next.atZone(zone))
    }
}
