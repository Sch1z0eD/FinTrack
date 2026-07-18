package com.findev.fintrack.data.local

import androidx.annotation.StringRes
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.CategoryType

/**
 * Seed data inserted once, when the database is created. Names are resolved from
 * resources at insert time; afterwards each row is ordinary user-editable data.
 */
data class CategorySeed(
    @param:StringRes val nameRes: Int,
    val type: CategoryType,
    val icon: String,
    val color: Long,
)

val DEFAULT_CATEGORIES: List<CategorySeed> = listOf(
    CategorySeed(R.string.category_groceries, CategoryType.EXPENSE, "🛒", 0xFF4CAF50),
    CategorySeed(R.string.category_cafe, CategoryType.EXPENSE, "☕", 0xFFFF9800),
    CategorySeed(R.string.category_transport, CategoryType.EXPENSE, "🚌", 0xFF2196F3),
    CategorySeed(R.string.category_car, CategoryType.EXPENSE, "🚗", 0xFF607D8B),
    CategorySeed(R.string.category_utilities, CategoryType.EXPENSE, "💡", 0xFFFFC107),
    CategorySeed(R.string.category_loans, CategoryType.EXPENSE, "💳", 0xFF673AB7),
    CategorySeed(R.string.category_communication, CategoryType.EXPENSE, "📱", 0xFF00BCD4),
    CategorySeed(R.string.category_health, CategoryType.EXPENSE, "💊", 0xFFF44336),
    CategorySeed(R.string.category_clothes, CategoryType.EXPENSE, "👕", 0xFFE91E63),
    CategorySeed(R.string.category_entertainment, CategoryType.EXPENSE, "🎬", 0xFF9C27B0),
    CategorySeed(R.string.category_home, CategoryType.EXPENSE, "🏠", 0xFF795548),
    CategorySeed(R.string.category_education, CategoryType.EXPENSE, "📚", 0xFF3F51B5),
    CategorySeed(R.string.category_gifts, CategoryType.EXPENSE, "🎁", 0xFFFF5722),
    CategorySeed(R.string.category_other_expense, CategoryType.EXPENSE, "📦", 0xFF9E9E9E),

    CategorySeed(R.string.category_salary, CategoryType.INCOME, "💰", 0xFF4CAF50),
    CategorySeed(R.string.category_advance, CategoryType.INCOME, "💵", 0xFF8BC34A),
    CategorySeed(R.string.category_bonus, CategoryType.INCOME, "🏆", 0xFFFFC107),
    CategorySeed(R.string.category_side_job, CategoryType.INCOME, "💼", 0xFF009688),
    CategorySeed(R.string.category_other_income, CategoryType.INCOME, "➕", 0xFF9E9E9E),
)
