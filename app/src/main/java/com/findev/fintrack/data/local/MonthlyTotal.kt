package com.findev.fintrack.data.local

import androidx.room.ColumnInfo

/**
 * Income and expense sums for one calendar month, for the monthly trend chart.
 * [yearMonth] is "YYYY-MM" as produced by SQLite's strftime; a month with no
 * transactions of a given type simply reports 0 for it.
 */
data class MonthlyTotal(
    @ColumnInfo(name = "year_month") val yearMonth: String,
    @ColumnInfo(name = "income_minor") val incomeMinor: Long,
    @ColumnInfo(name = "expense_minor") val expenseMinor: Long,
)
