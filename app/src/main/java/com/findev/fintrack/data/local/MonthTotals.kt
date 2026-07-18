package com.findev.fintrack.data.local

import androidx.room.ColumnInfo

/** Income and expense sums for a period, in kopecks. Transfers are excluded. */
data class MonthTotals(
    @ColumnInfo(name = "expense_minor")
    val expenseMinor: Long,

    @ColumnInfo(name = "income_minor")
    val incomeMinor: Long,
)
