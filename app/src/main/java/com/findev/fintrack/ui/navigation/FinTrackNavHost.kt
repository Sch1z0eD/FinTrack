package com.findev.fintrack.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.findev.fintrack.ui.screens.accounts.AccountsScreen
import com.findev.fintrack.ui.screens.categories.CategoriesScreen
import com.findev.fintrack.ui.screens.loans.LoanDetailScreen
import com.findev.fintrack.ui.screens.loans.LoanFormScreen
import com.findev.fintrack.ui.screens.overview.OverviewScreen
import com.findev.fintrack.ui.screens.settings.SettingsScreen
import com.findev.fintrack.ui.screens.statistics.StatisticsScreen
import com.findev.fintrack.ui.screens.payments.PaymentsScreen
import com.findev.fintrack.ui.screens.payments.RecurringDetailScreen
import com.findev.fintrack.ui.screens.payments.RecurringFormScreen
import com.findev.fintrack.ui.screens.quickentry.QuickEntryScreen
import com.findev.fintrack.ui.screens.transactions.TransactionsScreen
import com.findev.fintrack.ui.screens.utilities.BatchReadingScreen
import com.findev.fintrack.ui.screens.utilities.MeterDetailScreen
import com.findev.fintrack.ui.screens.utilities.MeterFormScreen
import com.findev.fintrack.ui.screens.utilities.UtilitiesScreen

private const val TRANSITION_MS = 90

/** Routes that are not top-level bottom-bar destinations. */
const val QUICK_ENTRY_ROUTE = "quick_entry"
const val QUICK_ENTRY_ARG_TRANSACTION_ID = "transactionId"
const val ACCOUNTS_ROUTE = "accounts"
const val CATEGORIES_ROUTE = "categories"
const val STATISTICS_ROUTE = "statistics"
const val SETTINGS_ROUTE = "settings"
const val LOAN_FORM_ROUTE = "loan_form"
const val LOAN_FORM_ARG_LOAN_ID = "loanId"
const val LOAN_DETAIL_ARG_LOAN_ID = "loanId"
const val RECURRING_DETAIL_ARG_ID = "recurringDetailId"
const val RECURRING_FORM_ROUTE = "recurring_form"
const val RECURRING_FORM_ARG_ID = "recurringId"
const val METER_FORM_ROUTE = "meter_form"
const val METER_FORM_ARG_ID = "meterId"
const val METER_DETAIL_ARG_ID = "meterId"
const val BATCH_READING_ARG_GROUP_ID = "groupId"

private const val METER_FORM_ROUTE_PATTERN = "$METER_FORM_ROUTE?$METER_FORM_ARG_ID={$METER_FORM_ARG_ID}"

private const val METER_DETAIL_ROUTE_PATTERN = "meter_detail/{$METER_DETAIL_ARG_ID}"

fun meterDetailRoute(id: String): String = "meter_detail/$id"

private const val BATCH_READING_ROUTE_PATTERN = "meter_batch_reading/{$BATCH_READING_ARG_GROUP_ID}"

fun batchReadingRoute(groupId: String): String = "meter_batch_reading/$groupId"

/** Same screen for both: passing an id switches it to editing. */
fun meterFormRoute(id: String? = null): String =
    if (id == null) METER_FORM_ROUTE else "$METER_FORM_ROUTE?$METER_FORM_ARG_ID=$id"

private const val RECURRING_FORM_ROUTE_PATTERN =
    "$RECURRING_FORM_ROUTE?$RECURRING_FORM_ARG_ID={$RECURRING_FORM_ARG_ID}"

/** Same screen for both: passing an id switches it to editing. */
fun recurringFormRoute(id: String? = null): String =
    if (id == null) RECURRING_FORM_ROUTE else "$RECURRING_FORM_ROUTE?$RECURRING_FORM_ARG_ID=$id"

private const val RECURRING_DETAIL_ROUTE_PATTERN = "recurring_detail/{$RECURRING_DETAIL_ARG_ID}"

fun recurringDetailRoute(id: String): String = "recurring_detail/$id"

private const val LOAN_DETAIL_ROUTE_PATTERN = "loan_detail/{$LOAN_DETAIL_ARG_LOAN_ID}"

fun loanDetailRoute(loanId: String): String = "loan_detail/$loanId"

private const val LOAN_FORM_ROUTE_PATTERN =
    "$LOAN_FORM_ROUTE?$LOAN_FORM_ARG_LOAN_ID={$LOAN_FORM_ARG_LOAN_ID}"

/** Same screen for both: passing an id switches it to editing. */
fun loanFormRoute(loanId: String? = null): String =
    if (loanId == null) LOAN_FORM_ROUTE else "$LOAN_FORM_ROUTE?$LOAN_FORM_ARG_LOAN_ID=$loanId"

private const val QUICK_ENTRY_ROUTE_PATTERN =
    "$QUICK_ENTRY_ROUTE?$QUICK_ENTRY_ARG_TRANSACTION_ID={$QUICK_ENTRY_ARG_TRANSACTION_ID}"

/** Same screen for both: passing an id switches it to editing. */
fun quickEntryRoute(transactionId: String? = null): String =
    if (transactionId == null) {
        QUICK_ENTRY_ROUTE
    } else {
        "$QUICK_ENTRY_ROUTE?$QUICK_ENTRY_ARG_TRANSACTION_ID=$transactionId"
    }

@Composable
fun FinTrackNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = FinTrackDestination.OVERVIEW.route,
        modifier = modifier,
        // The defaults slide and fade over ~300ms, which reads as sluggish when switching
        // tabs. A short cross-fade keeps the change legible without making it a journey.
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = { fadeOut(tween(TRANSITION_MS)) },
    ) {
        composable(FinTrackDestination.OVERVIEW.route) {
            OverviewScreen(
                onOpenAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
                onOpenStatistics = { navController.navigate(STATISTICS_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        composable(ACCOUNTS_ROUTE) {
            AccountsScreen(onBack = { navController.popBackStack() })
        }
        composable(STATISTICS_ROUTE) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
        composable(SETTINGS_ROUTE) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(CATEGORIES_ROUTE) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }
        composable(FinTrackDestination.TRANSACTIONS.route) {
            TransactionsScreen(
                onAddTransaction = { navController.navigate(quickEntryRoute()) },
                onEditTransaction = { id -> navController.navigate(quickEntryRoute(id)) },
            )
        }
        composable(FinTrackDestination.PAYMENTS.route) {
            PaymentsScreen(
                onAddLoan = { navController.navigate(loanFormRoute()) },
                onAddRecurring = { navController.navigate(recurringFormRoute()) },
                onOpenLoan = { id -> navController.navigate(loanDetailRoute(id)) },
                onOpenRecurring = { id -> navController.navigate(recurringDetailRoute(id)) },
            )
        }
        composable(
            route = RECURRING_FORM_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(RECURRING_FORM_ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            RecurringFormScreen(
                onDone = { navController.popBackStack() },
                onOpenAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
            )
        }
        composable(RECURRING_DETAIL_ROUTE_PATTERN) {
            RecurringDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(recurringFormRoute(id)) },
            )
        }
        composable(LOAN_DETAIL_ROUTE_PATTERN) {
            LoanDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(loanFormRoute(id)) },
            )
        }
        composable(
            route = LOAN_FORM_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(LOAN_FORM_ARG_LOAN_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            LoanFormScreen(onDone = { navController.popBackStack() })
        }
        composable(FinTrackDestination.UTILITIES.route) {
            UtilitiesScreen(
                onAddMeter = { navController.navigate(meterFormRoute()) },
                onOpenMeter = { id -> navController.navigate(meterDetailRoute(id)) },
                onSubmitGroupReadings = { groupId -> navController.navigate(batchReadingRoute(groupId)) },
            )
        }
        composable(METER_DETAIL_ROUTE_PATTERN) {
            MeterDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(meterFormRoute(id)) },
            )
        }
        composable(BATCH_READING_ROUTE_PATTERN) {
            BatchReadingScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = METER_FORM_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(METER_FORM_ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            MeterFormScreen(
                onDone = { navController.popBackStack() },
                // Deleting the meter kills the detail screen underneath, so land back on the
                // list rather than popping onto an empty detail.
                onDeleted = {
                    navController.popBackStack(FinTrackDestination.UTILITIES.route, inclusive = false)
                },
            )
        }
        composable(
            route = QUICK_ENTRY_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(QUICK_ENTRY_ARG_TRANSACTION_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            QuickEntryScreen(
                onDone = { navController.popBackStack() },
                onOpenAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
                onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
            )
        }
    }
}
