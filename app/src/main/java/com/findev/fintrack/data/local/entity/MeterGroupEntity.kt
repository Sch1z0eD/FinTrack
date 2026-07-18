package com.findev.fintrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-made grouping of utility services - "Вода", "Отопление", "Дом" - so several
 * services can be read and paid together. Groups are free-form: the user decides what goes
 * where, and a service with no group falls under «Прочее».
 */
@Entity(tableName = "meter_group")
data class MeterGroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** Manual order in the list; lower comes first. */
    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
)
