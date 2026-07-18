package com.findev.fintrack.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.findev.fintrack.R
import com.findev.fintrack.data.LoanRepository
import com.findev.fintrack.data.LoanWithSchedule
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.nextLoanDue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val LOAN_REMINDER_CHANNEL_ID = "loan_reminders"

/** Late morning: early enough to act on today, late enough not to be an alarm clock. */
private val LOAN_REMINDER_TIME: LocalTime = LocalTime.of(10, 0)

/**
 * Keeps the "payment coming up" alarm for each loan in step with the loans table.
 *
 * Same mirror discipline as [PaymentReminderScheduler]: the whole set is rebuilt whenever the
 * loans or their settlements change, so there is nothing to forget to call and nothing left
 * stale. A loan reminds [LoanEntity.reminderDaysBefore] days ahead (its own channel, so it can
 * be silenced apart from the recurring "due today" reminders).
 */
@Singleton
class LoanReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val loanRepository: LoanRepository,
    private val transactionRepository: TransactionRepository,
) {
    private val alarmManager: AlarmManager? = context.getSystemService()

    fun createChannel() {
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                LOAN_REMINDER_CHANNEL_ID,
                context.getString(R.string.loan_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.loan_reminder_channel_description)
            },
        )
    }

    fun observeLoans(scope: CoroutineScope) {
        scope.launch {
            combine(
                loanRepository.observeAllWithSchedules(),
                transactionRepository.observePaidThrough(),
            ) { loans, paidThrough -> loans to paidThrough }
                .collectLatest { (loans, paidThrough) -> rescheduleAll(loans, paidThrough) }
        }
    }

    suspend fun rescheduleAll() = rescheduleAll(
        loanRepository.observeAllWithSchedules().first(),
        transactionRepository.getPaidThrough(),
    )

    private fun rescheduleAll(loans: List<LoanWithSchedule>, paidThrough: Map<String, Long>) {
        loans.forEach { loan ->
            cancel(loan.loan.id)
            val daysBefore = loan.loan.reminderDaysBefore
            if (!loan.loan.isDeleted && daysBefore != null) {
                schedule(loan, daysBefore, paidThrough[loan.loan.id])
            }
        }
    }

    private fun schedule(loan: LoanWithSchedule, daysBefore: Int, paidThroughEpochDay: Long?) {
        val alarms = alarmManager ?: return
        val next = nextLoanDue(
            loan.schedule,
            paidThroughEpochDay?.let(LocalDate::ofEpochDay),
            LocalDate.now(),
        ) ?: return

        val triggerAt = next.date.minusDays(daysBefore.toLong())
            .atTime(LOAN_REMINDER_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // If the reminder window has already passed (including an overdue instalment), setting
        // an alarm in the past would fire on every reschedule. The red card is the reminder now.
        if (triggerAt <= System.currentTimeMillis()) return

        val pending = pendingIntent(
            loanId = loan.loan.id,
            name = loan.loan.name,
            amountMinor = next.paymentMinor,
            flag = PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancel(loanId: String) {
        val alarms = alarmManager ?: return
        val existing = PendingIntent.getBroadcast(
            context,
            loanId.hashCode(),
            Intent(context, LoanReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        existing?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(loanId: String, name: String, amountMinor: Long, flag: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            loanId.hashCode(),
            Intent(context, LoanReminderReceiver::class.java).apply {
                putExtra(EXTRA_PAYMENT_ID, loanId)
                putExtra(EXTRA_PAYMENT_NAME, name)
                putExtra(EXTRA_AMOUNT_MINOR, amountMinor)
            },
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canScheduleExact(): Boolean = alarmManager?.canScheduleExactAlarms() == true
}
