package com.findev.fintrack.data.local

import androidx.room.ColumnInfo

/** Income and expense sums for a period, in kopecks. Transfers are excluded. */
data class MonthTotals(
    @ColumnInfo(name = "expense_minor")
    val expenseMinor: Long,

    @ColumnInfo(name = "income_minor")
    val incomeMinor: Long,
)

/**
 * All-time income and expense plus the earliest transaction date - the basis for the average
 * daily/weekly/… figures. [firstDayEpochDay] is null when there are no transactions yet.
 */
data class AverageBasis(
    @ColumnInfo(name = "expense_minor")
    val expenseMinor: Long,

    @ColumnInfo(name = "income_minor")
    val incomeMinor: Long,

    @ColumnInfo(name = "first_day")
    val firstDayEpochDay: Long?,
)
