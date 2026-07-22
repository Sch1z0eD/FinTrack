package com.findev.fintrack.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.findev.fintrack.MainActivity
import com.findev.fintrack.R
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.screens.overview.monthBounds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val BUDGET_ALERT_CHANNEL_ID = "budget_alerts"
private const val BUDGET_NOTIFICATION_TAG = "budget_alert"

/**
 * Warns when spending crosses a category's monthly budget.
 *
 * The trigger is a single new expense, not a periodic sweep: [onExpenseAdded] compares the
 * category's month-to-date total before and after the amount just added, and notifies only when
 * that amount is what carried it past 80% or over the limit. Because the decision is a genuine
 * crossing, there is no per-month bookkeeping to keep - and nothing fires merely from opening
 * the app while a category is already over.
 */
@Singleton
class BudgetAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    fun createChannel() {
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                BUDGET_ALERT_CHANNEL_ID,
                context.getString(R.string.budget_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.budget_alert_channel_description)
            },
        )
    }

    /** Call right after a new expense is stored, with the amount that expense added. */
    suspend fun onExpenseAdded(categoryId: String, dateEpochDay: Long, addedAmountMinor: Long) {
        if (addedAmountMinor <= 0) return
        val category = categoryRepository.getById(categoryId) ?: return
        val limit = category.monthlyLimitMinor ?: return

        val month = monthBounds(LocalDate.ofEpochDay(dateEpochDay))
        val post = transactionRepository.sumExpenseForCategory(categoryId, month.first, month.last)
        val pre = post - addedAmountMinor
        val threshold80 = limit * 80 / 100

        when {
            // Over wins even when a single expense jumps straight past 80% to over the limit.
            pre < limit && post >= limit -> notify(category, post, limit, over = true)
            pre < threshold80 && post >= threshold80 -> notify(category, post, limit, over = false)
        }
    }

    private fun notify(category: CategoryEntity, spentMinor: Long, limitMinor: Long, over: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            category.id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = context.getString(
            if (over) R.string.budget_alert_over_title else R.string.budget_alert_near_title,
        )
        val text = context.getString(
            R.string.budget_alert_text,
            category.name,
            context.getString(R.string.money_with_currency, formatMinor(spentMinor)),
            context.getString(R.string.money_with_currency, formatMinor(limitMinor)),
        )
        val notification = NotificationCompat.Builder(context, BUDGET_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // One id per category, so a later alert updates that category's notice rather than
        // stacking a new one; a tag of its own keeps it clear of payment and meter ids.
        NotificationManagerCompat.from(context)
            .notify(BUDGET_NOTIFICATION_TAG, category.id.hashCode(), notification)
    }
}
