package com.findev.fintrack.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file format, with no database anywhere near it.
 *
 * Split out so the risky half can be tested. Reading a cursor and running INSERTs is
 * ordinary SQLite work; what actually breaks a restore is the format - a null that comes
 * back as the string "null", text that loses its encoding, a file from a newer build whose
 * extra columns get dropped in silence. All of that lives here and is covered by tests
 * that need no device and no Room.
 */

/** Bumped only when the file layout changes, not when the database schema does. */
const val BACKUP_FORMAT = 1

private const val KEY_FORMAT = "format"
private const val KEY_SCHEMA = "schemaVersion"
private const val KEY_CREATED_AT = "createdAt"
private const val KEY_APP_VERSION = "appVersion"
private const val KEY_TABLES = "tables"

/** One table's rows; every value is the column's text form, or null. */
typealias BackupTables = Map<String, List<Map<String, String?>>>

sealed interface DecodedBackup {
    data class Ok(val schemaVersion: Int, val tables: BackupTables) : DecodedBackup

    /** Not a backup at all, or damaged beyond reading. */
    data class Broken(val reason: String) : DecodedBackup
}

fun encodeBackup(
    schemaVersion: Int,
    appVersion: String,
    createdAt: Long,
    tables: BackupTables,
): String {
    val payload = JSONObject()
    for ((table, rows) in tables) {
        val array = JSONArray()
        for (row in rows) {
            val json = JSONObject()
            for ((column, value) in row) {
                // JSONObject.NULL, not a skipped key: on the way back in the two are the
                // same, but only the explicit null survives being read by a human.
                json.put(column, value ?: JSONObject.NULL)
            }
            array.put(json)
        }
        payload.put(table, array)
    }

    return JSONObject()
        .put(KEY_FORMAT, BACKUP_FORMAT)
        .put(KEY_SCHEMA, schemaVersion)
        .put(KEY_CREATED_AT, createdAt)
        .put(KEY_APP_VERSION, appVersion)
        .put(KEY_TABLES, payload)
        .toString(2)
}

fun decodeBackup(text: String): DecodedBackup {
    val root = try {
        JSONObject(text)
    } catch (e: Exception) {
        return DecodedBackup.Broken("это не файл резервной копии")
    }

    val schema = root.optInt(KEY_SCHEMA, -1)
    if (schema <= 0) return DecodedBackup.Broken("в файле нет версии схемы")

    val payload = root.optJSONObject(KEY_TABLES)
        ?: return DecodedBackup.Broken("в файле нет данных")

    val tables = mutableMapOf<String, List<Map<String, String?>>>()
    for (table in payload.keys()) {
        val array = payload.optJSONArray(table) ?: continue
        val rows = mutableListOf<Map<String, String?>>()
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val row = mutableMapOf<String, String?>()
            for (column in json.keys()) {
                row[column] = if (json.isNull(column)) null else json.getString(column)
            }
            rows += row
        }
        tables[table] = rows
    }
    return DecodedBackup.Ok(schema, tables)
}
