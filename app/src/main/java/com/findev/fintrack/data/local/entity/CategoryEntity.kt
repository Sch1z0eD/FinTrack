package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CategoryType { INCOME, EXPENSE }

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: CategoryType,

    /** Emoji shown in the quick-entry grid, e.g. "🛒". */
    @ColumnInfo(name = "icon")
    val icon: String,

    /** ARGB, use toInt() for Compose Color. */
    @ColumnInfo(name = "color")
    val color: Long,

    /**
     * Hidden from the quick-entry grid but kept on existing transactions, so past
     * spending keeps its label. Deletion is reserved for unused categories.
     */
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    /**
     * Manual sort order within a type, low first. The quick-entry grid and the categories
     * screen follow it, so the user decides which categories lead.
     */
    // defaultValue mirrors the migration's DEFAULT 0 so Room's schema check matches.
    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
