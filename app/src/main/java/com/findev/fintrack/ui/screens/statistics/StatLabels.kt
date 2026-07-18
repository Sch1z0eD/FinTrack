package com.findev.fintrack.ui.screens.statistics

import androidx.annotation.StringRes
import com.findev.fintrack.R
import com.findev.fintrack.data.StatPeriod

@StringRes
fun StatPeriod.labelRes(): Int = when (this) {
    StatPeriod.THIS_MONTH -> R.string.stats_this_month
    StatPeriod.LAST_MONTH -> R.string.stats_last_month
    StatPeriod.THIS_YEAR -> R.string.stats_this_year
}
