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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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
import com.findev.fintrack.data.MonthlyObligations
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.navigation.QUICK_ENTRY_ROUTE
import com.findev.fintrack.ui.screens.overview.monthBounds
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** What the widget shows, already formatted - the layouts do no arithmetic. */
private data class BalanceWidgetData(
    val balanceText: String,
    val freeText: String,
    val expenseText: String,
    val incomeText: String,
    val obligationsText: String,
    val obligationsStatus: String,
    val hasObligations: Boolean,
    val isOverdrawn: Boolean,
)

/**
 * Home-screen widget: balance, what is left once this month's obligations are covered, and
 * a jump into quick entry. Three layouts by size - a 2x1 tile has room for one number, and
 * cramming the rest in would just clip them.
 */
class BalanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val bounds = monthBounds(LocalDate.now())

        val balanceMinor = entryPoint.overviewRepository().observeTotalBalance().first()
        val totals = entryPoint.overviewRepository().observeTotals(bounds.first, bounds.last).first()
        val obligations = entryPoint.obligationsRepository().observeForMonth(bounds).first()

        val data = context.toWidgetData(balanceMinor, totals.expenseMinor, totals.incomeMinor, obligations)

        provideContent {
            GlanceTheme {
                when (LocalSize.current) {
                    SMALL -> SmallLayout(data)
                    LARGE -> LargeLayout(data)
                    else -> MediumLayout(data)
                }
            }
        }
    }

    private fun Context.toWidgetData(
        balanceMinor: Long,
        expenseMinor: Long,
        incomeMinor: Long,
        obligations: MonthlyObligations,
    ): BalanceWidgetData {
        val freeMinor = balanceMinor - obligations.remainingMinor
        fun money(minor: Long) = getString(R.string.money_with_currency, formatMinor(minor))

        return BalanceWidgetData(
            balanceText = money(balanceMinor),
            freeText = money(freeMinor),
            expenseText = money(expenseMinor),
            incomeText = money(incomeMinor),
            obligationsText = money(obligations.totalMinor),
            obligationsStatus = if (obligations.remainingMinor == 0L) {
                getString(R.string.widget_obligations_done)
            } else {
                getString(R.string.widget_obligations_left, formatMinor(obligations.remainingMinor))
            },
            hasObligations = obligations.totalMinor > 0,
            isOverdrawn = freeMinor < 0,
        )
    }

    companion object {
        // Heights are a budget, not a wish: the launcher picks the largest breakpoint that
        // fits, then squeezes whatever is composed into the real box. Declare a size the
        // layout does not actually need and the last child gets crushed to a few pixels -
        // which is exactly how the add button ended up 3px tall.
        // Usable height is the declared one minus 32dp of padding.
        val SMALL = DpSize(140.dp, 100.dp) // balance only: ~44dp
        val MEDIUM = DpSize(200.dp, 180.dp) // + free + button: ~128dp
        val LARGE = DpSize(250.dp, 260.dp) // + obligations + month totals: ~204dp
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_glass_background))
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun SmallLayout(data: BalanceWidgetData) {
    GlassPanel {
        Label(stringResource(R.string.overview_balance))
        Text(
            text = data.balanceText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun MediumLayout(data: BalanceWidgetData) {
    GlassPanel {
        Label(stringResource(R.string.overview_balance))
        Text(
            text = data.balanceText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (data.hasObligations) {
            Spacer(GlanceModifier.height(10.dp))
            ObligationsAndFree(data)
            Text(
                text = data.obligationsStatus,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        // Takes up whatever height is left so the button lands on the bottom edge instead
        // of leaving a dead strip under it.
        Spacer(GlanceModifier.defaultWeight())
        AddButton()
    }
}

@Composable
private fun ObligationsAndFree(data: BalanceWidgetData) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Label(stringResource(R.string.widget_obligations))
            Text(
                text = data.obligationsText,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp),
                maxLines = 1,
            )
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Label(stringResource(R.string.widget_free))
            Text(
                text = data.freeText,
                style = TextStyle(
                    color = if (data.isOverdrawn) {
                        GlanceTheme.colors.error
                    } else {
                        GlanceTheme.colors.onSurface
                    },
                    fontSize = 16.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LargeLayout(data: BalanceWidgetData) {
    GlassPanel {
        Label(stringResource(R.string.overview_balance))
        Text(
            text = data.balanceText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )

        if (data.hasObligations) {
            Spacer(GlanceModifier.height(10.dp))
            ObligationsAndFree(data)
            Text(
                text = data.obligationsStatus,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.height(10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Label(stringResource(R.string.overview_expense_month))
                Text(
                    text = data.expenseText,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp),
                    maxLines = 1,
                )
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Label(stringResource(R.string.overview_income_month))
                Text(
                    text = data.incomeText,
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.defaultWeight())
        AddButton()
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        maxLines = 1,
    )
}

@Composable
private fun AddButton() {
    val context = LocalContext.current
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_START_ROUTE, QUICK_ENTRY_ROUTE)
    }
    // The background/corner/padding chain has to sit on a container, not on the Text
    // itself - put it on the Text and the pill renders with no label inside it.
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(intent))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_add),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/** Glance has no stringResource of its own; the widget reads from the context it renders in. */
@Composable
private fun stringResource(resId: Int): String = LocalContext.current.getString(resId)
