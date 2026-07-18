package com.findev.fintrack.data.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Inserts the default categories exactly once, when the database file is created.
 *
 * Runs synchronously inside Room's creation transaction, so the first query can
 * never observe an unseeded database.
 */
class SeedCategoriesCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        DEFAULT_CATEGORIES.forEach { seed ->
            db.execSQL(
                "INSERT INTO category (id, name, type, icon, color, is_archived, updated_at, is_deleted) " +
                    "VALUES (?, ?, ?, ?, ?, 0, ?, 0)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    context.getString(seed.nameRes),
                    seed.type.name,
                    seed.icon,
                    seed.color,
                    now,
                ),
            )
        }
    }
}
