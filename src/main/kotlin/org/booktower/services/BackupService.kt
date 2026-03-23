package org.booktower.services

import org.booktower.config.Json
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Instant
import java.util.UUID

private val backupLog = LoggerFactory.getLogger("booktower.BackupService")

data class BackupMetadata(
    val version: Int,
    val createdAt: String,
    val bookTowerVersion: String,
    val tableCount: Int,
    val totalRows: Int,
)

/**
 * Exports and imports the full BookTower database as JSON.
 * Works with both H2 and PostgreSQL.
 *
 * The export format is a JSON object with:
 * - `metadata`: backup version, timestamp, row counts
 * - `tables`: map of table name → list of row maps
 *
 * User-facing files (book files, covers) are NOT included in the backup.
 * Only database state is exported.
 */
class BackupService(
    private val jdbi: Jdbi,
) {
    companion object {
        private const val BACKUP_VERSION = 1

        private val TABLES =
            listOf(
                "users",
                "libraries",
                "books",
                "book_status",
                "book_ratings",
                "book_tags",
                "book_categories",
                "book_moods",
                "bookmarks",
                "book_annotations",
                "book_authors",
                "book_journal_entries",
                "book_notebooks",
                "book_reviews",
                "book_links",
                "book_sharing_tokens",
                "book_files",
                "reading_progress",
                "reading_sessions",
                "reading_daily",
                "magic_shelves",
                "api_tokens",
                "koreader_devices",
                "koreader_sync",
                "kobo_devices",
                "kobo_reading_state",
                "user_settings",
                "filter_presets",
                "notifications",
                "audit_log",
                "collections",
                "collection_books",
            )
    }

    /**
     * Exports all database tables as a JSON string.
     * Admin-only — exports ALL users' data.
     */
    fun export(): String {
        val tables = mutableMapOf<String, List<Map<String, Any?>>>()
        var totalRows = 0

        for (tableName in TABLES) {
            try {
                val rows =
                    jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
                        h.createQuery("SELECT * FROM $tableName").mapToMap().list()
                    }
                tables[tableName] = rows
                totalRows += rows.size
            } catch (e: Exception) {
                backupLog.debug("Skipping table $tableName: ${e.message}")
            }
        }

        val metadata =
            BackupMetadata(
                version = BACKUP_VERSION,
                createdAt = Instant.now().toString(),
                bookTowerVersion = VersionService.info.version,
                tableCount = tables.size,
                totalRows = totalRows,
            )

        val backup = mapOf("metadata" to metadata, "tables" to tables)
        backupLog.info("Database backup created: ${tables.size} tables, $totalRows rows")
        return Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(backup)
    }

    /**
     * Imports a previously exported backup, replacing all data.
     * Clears existing tables before importing.
     * Admin-only — replaces ALL data for ALL users.
     * Returns the backup metadata on success.
     */
    fun import(input: InputStream): BackupMetadata {
        val tree = Json.mapper.readTree(input)
        val version = tree.get("metadata")?.get("version")?.asInt() ?: 0
        if (version != BACKUP_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: $version (expected $BACKUP_VERSION)")
        }

        val tablesNode = tree.get("tables") ?: throw IllegalArgumentException("No tables in backup")
        var totalRows = 0

        jdbi.useHandle<Exception> { h ->
            // Clear in reverse order to respect foreign keys
            for (tableName in TABLES.reversed()) {
                try {
                    h.execute("DELETE FROM $tableName")
                } catch (e: Exception) {
                    backupLog.debug("Could not clear $tableName: ${e.message}")
                }
            }

            // Import in order
            for (tableName in TABLES) {
                val rows = tablesNode.get(tableName) ?: continue
                if (!rows.isArray || rows.isEmpty) continue

                for (row in rows) {
                    val fields = mutableListOf<String>()
                    val values = mutableListOf<Any?>()
                    row.fields().forEach { (key, value) ->
                        fields += key
                        values +=
                            when {
                                value.isNull -> null
                                value.isBoolean -> value.asBoolean()
                                value.isInt -> value.asInt()
                                value.isLong -> value.asLong()
                                value.isDouble || value.isFloat -> value.asDouble()
                                else -> value.asText()
                            }
                    }
                    if (fields.isEmpty()) continue

                    val placeholders = fields.joinToString(",") { "?" }
                    val columns = fields.joinToString(",")
                    val q = h.createUpdate("INSERT INTO $tableName ($columns) VALUES ($placeholders)")
                    values.forEachIndexed { i, v -> q.bind(i, v) }
                    q.execute()
                    totalRows++
                }
            }
        }

        backupLog.info("Database restore complete: $totalRows rows imported")
        return BackupMetadata(
            version = BACKUP_VERSION,
            createdAt = tree.get("metadata")?.get("createdAt")?.asText() ?: "",
            bookTowerVersion = tree.get("metadata")?.get("bookTowerVersion")?.asText() ?: "",
            tableCount = TABLES.size,
            totalRows = totalRows,
        )
    }
}
