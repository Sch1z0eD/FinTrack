package com.findev.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file format.
 *
 * These cover what actually breaks a restore: a null coming back as the string "null",
 * text losing its encoding, a file from a newer build, a file that is not a backup at all.
 * Reading a cursor and running INSERTs is ordinary SQLite work and is not tested here -
 * doing that in a JVM test would mean re-implementing the repository beside it, and a test
 * that restates the code proves only that it was written twice.
 */
class BackupFormatTest {

    private val sample: BackupTables = mapOf(
        "account" to listOf(
            mapOf("id" to "a1", "name" to "Карта Т-Банк", "initial_balance_minor" to "4800000"),
            mapOf("id" to "a2", "name" to "Наличные", "initial_balance_minor" to "750000"),
        ),
        "transactions" to listOf(
            mapOf("id" to "t1", "note" to "Пятёрочка", "settles_partial" to "0"),
            mapOf("id" to "t2", "note" to null, "settles_partial" to "1"),
        ),
    )

    private fun roundTrip(tables: BackupTables = sample): DecodedBackup.Ok {
        val text = encodeBackup(
            schemaVersion = 4,
            appVersion = "0.3.1",
            createdAt = 1_760_000_000_000,
            tables = tables,
        )
        val decoded = decodeBackup(text)
        assertTrue("decode failed: $decoded", decoded is DecodedBackup.Ok)
        return decoded as DecodedBackup.Ok
    }

    @Test
    fun `every row comes back exactly as it went in`() {
        assertEquals(sample, roundTrip().tables)
    }

    @Test
    fun `the schema version survives, so an old file can still be recognised`() {
        assertEquals(4, roundTrip().schemaVersion)
    }

    /** A null note read back as the string "null" would put that word in the user's feed. */
    @Test
    fun `a null value stays null rather than becoming the text null`() {
        val row = roundTrip().tables.getValue("transactions").first { it["id"] == "t2" }
        assertEquals(null, row["note"])
        assertTrue("note key was dropped entirely", row.containsKey("note"))
    }

    @Test
    fun `non-ascii text is preserved`() {
        val row = roundTrip().tables.getValue("account").first { it["id"] == "a1" }
        assertEquals("Карта Т-Банк", row["name"])
    }

    /**
     * The flag that decides whether an obligation is closed. Restoring it as 0 would
     * silently turn every part payment into a full one.
     */
    @Test
    fun `the partial payment flag survives`() {
        val row = roundTrip().tables.getValue("transactions").first { it["id"] == "t2" }
        assertEquals("1", row["settles_partial"])
    }

    @Test
    fun `an empty database produces a file that restores to nothing`() {
        val decoded = roundTrip(mapOf("account" to emptyList(), "transactions" to emptyList()))
        assertEquals(emptyList<Map<String, String?>>(), decoded.tables.getValue("account"))
    }

    /**
     * A table nobody has written support for still round-trips: the dump is driven by
     * sqlite_master, so an entity added tomorrow is included without this file being touched.
     */
    @Test
    fun `an unknown table is carried through untouched`() {
        val decoded = roundTrip(mapOf("budget" to listOf(mapOf("id" to "b1", "limit_minor" to "3000000"))))
        assertEquals("3000000", decoded.tables.getValue("budget").single()["limit_minor"])
    }

    @Test
    fun `something that is not json is rejected with a reason`() {
        val decoded = decodeBackup("не файл вовсе")
        assertTrue(decoded is DecodedBackup.Broken)
    }

    @Test
    fun `json without a schema version is rejected`() {
        val decoded = decodeBackup("""{"tables":{}}""")
        assertTrue(decoded is DecodedBackup.Broken)
    }

    @Test
    fun `json without any tables is rejected`() {
        val decoded = decodeBackup("""{"schemaVersion":4}""")
        assertTrue(decoded is DecodedBackup.Broken)
    }
}
