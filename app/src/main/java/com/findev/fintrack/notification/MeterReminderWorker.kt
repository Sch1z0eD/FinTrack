package com.findev.fintrack.notification

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
import com.findev.fintrack.data.MeterRepository
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.metersToRemindToday
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Runs about once a day and, on a meter's reminder day, says "submit your reading".
 *
 * A daily check rather than a per-meter alarm because "the 23rd of every month" is a rule,
 * not a moment: which day that is depends on the month, and re-deciding each day keeps a
 * reminder set for the 31st firing in February without any date arithmetic to maintain.
 * WorkManager persists the schedule across reboots, which is why it is used here and not
 * AlarmManager - the reading reminder is not time-critical the way a payment due-date is.
 */
@HiltWorker
class MeterReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val meterRepository: MeterRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val due = metersToRemindToday(meterRepository.getAll(), LocalDate.now())
        due.forEach { notify(it) }
        return Result.success()
    }

    private fun notify(meter: MeterEntity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            meter.id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // A metered service is reminded to submit readings; a norm or fixed one has nothing to
        // read, so it is simply reminded to pay.
        val text = if (meter.billing == BillingKind.METERED) {
            context.getString(R.string.meter_reminder_text, meter.name)
        } else {
            context.getString(R.string.meter_reminder_pay_text, meter.name)
        }
        val notification = NotificationCompat.Builder(context, METER_REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.meter_reminder_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // A namespace distinct from payment ids: two features hashing ids into the same
        // notification space could otherwise collide and overwrite each other.
        NotificationManagerCompat.from(context).notify(METER_NOTIFICATION_TAG, meter.id.hashCode(), notification)
    }
}

private const val METER_NOTIFICATION_TAG = "meter_reading"
