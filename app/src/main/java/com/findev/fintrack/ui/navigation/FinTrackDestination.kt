package com.findev.fintrack.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.findev.fintrack.R

/** Top-level destinations shown in the bottom navigation bar. */
enum class FinTrackDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    OVERVIEW("overview", R.string.nav_overview, Icons.Filled.Home),
    TRANSACTIONS("transactions", R.string.nav_transactions, Icons.AutoMirrored.Filled.List),
    PAYMENTS("payments", R.string.nav_payments, Icons.Filled.DateRange),
    UTILITIES("utilities", R.string.nav_utilities, Icons.Filled.Build),
}
