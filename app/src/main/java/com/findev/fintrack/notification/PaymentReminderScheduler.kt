package com.findev.fintrack.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.findev.fintrack.R
import com.findev.fintrack.data.RecurringPaymentRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.entity.RecurringPaymentEntity
import com.findev.fintrack.data.nextDueRecurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val REMINDER_CHANNEL_ID = "payment_reminders"

/** Late morning: early enough to act on today, late enough not to be an alarm clock. */
private val REMINDER_TIME: LocalTime = LocalTime.of(10, 0)

/**
 * Keeps the alarm for each payment in step with the payments table.
 *
 * Alarms are not state - they are a projection of it, so rather than being poked from
 * every place that edits a payment, the whole set is rebuilt whenever the table changes.
 * There is nothing to forget to call, and nothing to leave stale.
 */
@Singleton
class PaymentReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
) {
    private val alarmManager: AlarmManager? = context.getSystemService()

    fun createChannel() {
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    /**
     * Mirrors the table into alarms for as long as [scope] lives.
     *
     * Settlements are watched too, not just the payments: marking a bill paid moves its
     * next due date, and an alarm still pointing at the old one would fire for something
     * already handled.
     */
    fun observePayments(scope: CoroutineScope) {
        scope.launch {
            combine(
                recurringPaymentRepository.observeAllForReminders(),
                transactionRepository.observePaidThrough(),
            ) { payments, paidThrough -> payments to paidThrough }
                .collectLatest { (payments, paidThrough) -> rescheduleAll(payments, paidThrough) }
        }
    }

    suspend fun rescheduleAll() = rescheduleAll(
        recurringPaymentRepository.getAllForReminders(),
        transactionRepository.getPaidThrough(),
    )

    /**
     * Every alarm is cancelled and only the live, reminder-enabled ones are set again.
     *
     * Cancelling first - including for deleted rows - is what makes this a mirror rather
     * than an accumulation: a payment that was deleted, or had its reminder switched off,
     * or simply moved, must not leave yesterday's alarm behind to wake the phone about
     * something that no longer exists.
     */
    private fun rescheduleAll(
        payments: List<RecurringPaymentEntity>,
        paidThrough: Map<String, Long>,
    ) {
        payments.forEach { payment ->
            cancel(payment.id)
            if (!payment.isDeleted && payment.reminderEnabled) {
                schedule(payment, paidThrough[payment.id])
            }
        }
    }

    private fun schedule(payment: RecurringPaymentEntity, paidThroughEpochDay: Long?) {
        val alarms = alarmManager ?: return
        val due = nextDueRecurrence(
            start = LocalDate.ofEpochDay(payment.startDateEpochDay),
            period = payment.period,
            end = payment.endDateEpochDay?.let(LocalDate::ofEpochDay),
            paidThrough = paidThroughEpochDay?.let(LocalDate::ofEpochDay),
            today = LocalDate.now(),
        ) ?: return

        val triggerAt = due.atTime(REMINDER_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // An overdue payment's due date is in the past, and an alarm set in the past fires
        // at once - which would mean a notification on every reschedule until it is paid.
        // The card is already red; that is the reminder from here on.
        if (triggerAt <= System.currentTimeMillis()) return

        val pending = pendingIntent(payment, PendingIntent.FLAG_UPDATE_CURRENT)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // The permission is revocable, and a bill reminder that slips into the next
            // maintenance window is still a reminder. Crashing instead would not be.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancel(paymentId: String) {
        val alarms = alarmManager ?: return
        val existing = PendingIntent.getBroadcast(
            context,
            paymentId.hashCode(),
            Intent(context, PaymentReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        existing?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(payment: RecurringPaymentEntity, flag: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            payment.id.hashCode(),
            Intent(context, PaymentReminderReceiver::class.java).apply {
                putExtra(EXTRA_PAYMENT_ID, payment.id)
                putExtra(EXTRA_PAYMENT_NAME, payment.name)
                putExtra(EXTRA_AMOUNT_MINOR, payment.amountMinor)
            },
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Revocable since Android 12, and denied by default from 14 - so this is normally
     * false, not an edge case.
     */
    private fun canScheduleExact(): Boolean = alarmManager?.canScheduleExactAlarms() == true
}
