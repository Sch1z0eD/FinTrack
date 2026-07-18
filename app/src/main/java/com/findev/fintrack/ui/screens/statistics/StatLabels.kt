package com.findev.fintrack.ui.screens.statistics

import androidx.annotation.StringRes
import com.findev.fintrack.R
import com.findev.fintrack.data.StatPeriod

@StringRes
fun StatPeriod.labelRes(): Int = when (this) {
    StatPeriod.WEEK -> R.string.period_week
    StatPeriod.THIS_MONTH -> R.string.stats_this_month
    StatPeriod.LAST_MONTH -> R.string.stats_last_month
    StatPeriod.QUARTER -> R.string.period_quarter
    StatPeriod.THIS_YEAR -> R.string.stats_this_year
    StatPeriod.ALL -> R.string.period_all
    StatPeriod.CUSTOM -> R.string.period_custom
}
