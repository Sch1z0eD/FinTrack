package com.findev.fintrack.data.local

/** How much was spent in one category over a period, for a breakdown chart. */
data class CategoryTotal(
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val totalMinor: Long,
)
