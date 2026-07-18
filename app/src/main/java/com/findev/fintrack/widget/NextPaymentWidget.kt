package com.findev.fintrack.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.findev.fintrack.MainActivity
import com.findev.fintrack.R
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.navigation.FinTrackDestination
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row, already formatted. */
private data class PaymentRow(
    val name: String,
    val amountText: String,
    val dateText: String,
    val isOverdue: Boolean,
)

/**
 * Home-screen widget for upcoming obligations. Small shows the single soonest one; the taller
 * sizes list several, because "what is coming up" is a question about the next few weeks and
 * one row cannot answer it.
 */
class NextPaymentWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payments = widgetEntryPoint(context).nextPaymentRepository()
            .observeUpcoming(LocalDate.now(), limit = MAX_ROWS)
            .first()

        val dateFormat = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
        val overdueText = context.getString(R.string.widget_overdue)

        val rows = payments.map { payment ->
            val date = LocalDate.ofEpochDay(payment.dueEpochDay).format(dateFormat)
            PaymentRow(
                name = payment.name,
                amountText = context.getString(
                    R.string.money_with_currency,
                    formatMinor(payment.amountMinor),
                ),
                dateText = if (payment.isOverdue) "$date · $overdueText" else date,
                isOverdue = payment.isOverdue,
            )
        }

        provideContent {
            GlanceTheme {
                val visibleRows = when (LocalSize.current) {
                    SMALL -> 1
                    MEDIUM -> 2
                    else -> MAX_ROWS
                }
                Content(rows = rows.take(visibleRows), showTitle = LocalSize.current != SMALL)
            }
        }
    }

    companion object {
        private const val MAX_ROWS = 4
        // See BalanceWidget: the declared height has to cover what the layout actually
        // needs, or the launcher crushes the last row. A row is ~40dp, padding is 32dp.
        val SMALL = DpSize(140.dp, 100.dp) // one row, no title
        val MEDIUM = DpSize(200.dp, 160.dp) // title + 2 rows
        val LARGE = DpSize(250.dp, 260.dp) // title + 4 rows
    }
}

@Composable
private fun Content(rows: List<PaymentRow>, showTitle: Boolean) {
    val context = LocalContext.current
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_START_ROUTE, FinTrackDestination.PAYMENTS.route)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_glass_background))
            .padding(16.dp)
            .clickable(actionStartActivity(intent)),
    ) {
        if (showTitle) {
            Text(
                text = context.getString(
                    if (rows.size > 1) {
                        R.string.widget_next_payments_title
                    } else {
                        R.string.widget_next_payment_title
                    },
                ),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
        }

        if (rows.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_next_payment_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp),
            )
            return@Column
        }

        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(GlanceModifier.height(8.dp))
            PaymentRowView(row)
        }
    }
}

@Composable
private fun PaymentRowView(row: PaymentRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = row.dateText,
                style = TextStyle(
                    color = if (row.isOverdue) {
                        GlanceTheme.colors.error
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
        Text(
            text = row.amountText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}
