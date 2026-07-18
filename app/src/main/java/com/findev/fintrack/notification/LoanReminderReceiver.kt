package com.findev.fintrack.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.findev.fintrack.MainActivity
import com.findev.fintrack.R
import com.findev.fintrack.ui.formatMinor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Says a loan payment is coming up. Like [PaymentReminderReceiver] it records nothing: only the
 * user decides what was actually paid.
 */
@AndroidEntryPoint
class LoanReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: LoanReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_PAYMENT_ID) ?: return
        val name = intent.getStringExtra(EXTRA_PAYMENT_NAME).orEmpty()
        val amountMinor = intent.getLongExtra(EXTRA_AMOUNT_MINOR, 0)

        notify(context, id, name, amountMinor)

        // This loan's alarm has just been spent; the next one is armed once the answer changes
        // (the instalment is marked paid). Rebuilding keeps every *other* loan armed after a
        // cold start by this broadcast.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, id: String, name: String, amountMinor: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, LOAN_REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.loan_reminder_title, name))
            .setContentText(
                context.getString(R.string.money_with_currency, formatMinor(amountMinor)),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }
}
