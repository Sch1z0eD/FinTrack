package com.findev.fintrack.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.findev.fintrack.data.NextPaymentRepository
import com.findev.fintrack.data.ObligationsRepository
import com.findev.fintrack.data.OverviewRepository
import com.findev.fintrack.ui.screens.overview.monthBounds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import javax.inject.Inject

/**
 * Keeps the home-screen widgets in step with the data while the app process is alive. Each
 * widget watches only what it shows and repaints when that changes. The first emission is
 * dropped: each widget already loaded that snapshot itself when it was composed.
 */
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overviewRepository: OverviewRepository,
    private val nextPaymentRepository: NextPaymentRepository,
    private val obligationsRepository: ObligationsRepository,
) {
    fun observe(scope: CoroutineScope) {
        val bounds = monthBounds(LocalDate.now())
        // Balance widget: balance, this month's spending, and the obligations behind the
        // "free after obligations" figure. Obligations have to be watched separately -
        // adding a subscription moves that number without touching the balance.
        combine(
            overviewRepository.observeTotalBalance(),
            overviewRepository.observeTotals(bounds.first, bounds.last),
            obligationsRepository.observeForMonth(bounds),
        ) { balance, totals, obligations ->
            Triple(balance, totals.expenseMinor, obligations)
        }
            .drop(1)
            .onEach { BalanceWidget().updateAll(context) }
            .launchIn(scope)

        // Payments widget: it lists several rows, so it has to watch all of them - keying on
        // the soonest alone would leave a stale list when a later payment changes.
        nextPaymentRepository.observeUpcoming(LocalDate.now(), limit = UPCOMING_ROWS)
            .drop(1)
            .onEach { NextPaymentWidget().updateAll(context) }
            .launchIn(scope)
    }

    private companion object {
        /** Matches the largest layout of [NextPaymentWidget]. */
        const val UPCOMING_ROWS = 4
    }
}
