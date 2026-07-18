package com.findev.fintrack.data

import android.content.Context
import android.net.Uri
import com.findev.fintrack.data.local.FinTrackDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room's own bookkeeping, and Android's locale row. Neither belongs in a backup. */
private val SKIPPED_TABLES = setOf("room_master_table", "android_metadata", "sqlite_sequence")

sealed interface ImportResult {
    data class Success(val rowsRestored: Int) : ImportResult

    /** The file was written by a newer app; importing it would silently drop columns. */
    data class TooNew(val fileSchema: Int, val appSchema: Int) : ImportResult

    data class Invalid(val reason: String) : ImportResult
}

/**
 * Whole-database backup as a single JSON file.
 *
 * JSON rather than a copy of the .db, even though the copy would be less code: a database
 * file is only meaningful to the exact schema that wrote it, and a backup is precisely the
 * thing you reach for after something went wrong with a schema. Text can be read, diffed
 * and repaired by hand; a corrupted page in a binary file cannot.
 *
 * The dump is generic - it walks sqlite_master rather than naming tables - so a new entity
 * is included the day it is added, with nobody having to remember this file exists. That
 * matters more than type safety here: the failure mode of a forgotten table is silent data
 * loss at restore time.
 */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: FinTrackDatabase,
) {

    suspend fun export(target: Uri, appVersion: String): Int = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase

        val tables = mutableMapOf<String, List<Map<String, String?>>>()
        var rows = 0
        for (table in tableNames()) {
            val collected = mutableListOf<Map<String, String?>>()
            db.query("SELECT * FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = mutableMapOf<String, String?>()
                    for (i in 0 until cursor.columnCount) {
                        row[cursor.getColumnName(i)] =
                            if (cursor.isNull(i)) null else cursor.getString(i)
                    }
                    collected += row
                    rows++
                }
            }
            tables[table] = collected
        }

        val text = encodeBackup(
            schemaVersion = db.version,
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            tables = tables,
        )
        requireNotNull(context.contentResolver.openOutputStream(target, "wt")) {
            "Cannot write to $target"
        }.use { it.write(text.toByteArray()) }

        rows
    }

    /**
     * Replaces everything with the contents of [source].
     *
     * A restore, not a merge. Merging two histories needs a rule for every conflict, and
     * the honest rule for "I lost my phone" is "this file is the truth now". The dialog
     * that leads here says so.
     */
    suspend fun import(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = try {
            requireNotNull(context.contentResolver.openInputStream(source)) {
                "Cannot read $source"
            }.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            return@withContext ImportResult.Invalid(e.message ?: "не удалось прочитать файл")
        }

        val decoded = when (val result = decodeBackup(text)) {
            is DecodedBackup.Broken -> return@withContext ImportResult.Invalid(result.reason)
            is DecodedBackup.Ok -> result
        }

        val db = database.openHelper.writableDatabase
        // Older files are fine - Room migrated this database forward from those very
        // versions - but a newer one describes columns this build has never heard of, and
        // restoring it would quietly drop them.
        if (decoded.schemaVersion > db.version) {
            return@withContext ImportResult.TooNew(decoded.schemaVersion, db.version)
        }

        val known = tableNames()
        var restored = 0
        db.beginTransaction()
        try {
            // Wipe first, in the same transaction: a half-applied restore over live data
            // would be worse than either state on its own.
            known.forEach { db.execSQL("DELETE FROM `$it`") }

            for (table in known) {
                for (row in decoded.tables[table].orEmpty()) {
                    if (row.isEmpty()) continue
                    val columns = row.keys.toList()
                    val names = columns.joinToString(",") { "`$it`" }
                    val placeholders = columns.joinToString(",") { "?" }
                    db.execSQL(
                        "INSERT OR REPLACE INTO `$table` ($names) VALUES ($placeholders)",
                        columns.map { row[it] }.toTypedArray(),
                    )
                    restored++
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            return@withContext ImportResult.Invalid(e.message ?: "файл повреждён")
        } finally {
            db.endTransaction()
        }

        ImportResult.Success(restored)
    }

    /** App tables, in a stable order so two exports of the same data are identical files. */
    private fun tableNames(): List<String> {
        val db = database.openHelper.readableDatabase
        val names = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name !in SKIPPED_TABLES) names += name
            }
        }
        return names
    }
}

/** Suggested file name: sorts chronologically and says what it is at a glance. */
fun backupFileName(millis: Long = System.currentTimeMillis()): String {
    val date = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    return "fintrack-backup-$date.json"
}
