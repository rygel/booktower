package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private fun parseTs(v: String): Instant = try {
    Instant.parse(v)
} catch (_: Exception) {
    LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]")).toInstant(ZoneOffset.UTC)
}

private val kreaderLogger = LoggerFactory.getLogger("booktower.KOReaderSyncService")

data class KOReaderDevice(
    val token: String,
    val userId: UUID,
    val deviceName: String?,
    val lastSyncAt: Instant?,
    val createdAt: Instant,
)

/**
 * Implements the KOReader sync server protocol (kosync).
 * KOReader uses GET /users/create, GET /users/auth, PUT /syncs/progress, GET /syncs/progress/{document}.
 */
class KOReaderSyncService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
) {

    fun registerDevice(userId: UUID, deviceName: String?): KOReaderDevice {
        val token = UUID.randomUUID().toString()
        val now = Instant.now()
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                "INSERT INTO koreader_devices (token, user_id, device_name, created_at) VALUES (?, ?, ?, ?)",
            ).bind(0, token).bind(1, userId.toString()).bind(2, deviceName).bind(3, now.toString()).execute()
        }
        kreaderLogger.info("KOReader device registered for user $userId: $deviceName")
        return KOReaderDevice(token = token, userId = userId, deviceName = deviceName, lastSyncAt = null, createdAt = now)
    }

    fun getDevice(token: String): KOReaderDevice? =
        jdbi.withHandle<KOReaderDevice?, Exception> { h ->
            h.createQuery("SELECT * FROM koreader_devices WHERE token = ?")
                .bind(0, token)
                .map { row ->
                    KOReaderDevice(
                        token = row.getColumn("token", String::class.java),
                        userId = UUID.fromString(row.getColumn("user_id", String::class.java)),
                        deviceName = row.getColumn("device_name", String::class.java),
                        lastSyncAt = row.getColumn("last_sync_at", String::class.java)?.let { runCatching { parseTs(it) }.getOrNull() },
                        createdAt = parseTs(row.getColumn("created_at", String::class.java)),
                    )
                }.firstOrNull()
        }

    fun listDevices(userId: UUID): List<KOReaderDevice> =
        jdbi.withHandle<List<KOReaderDevice>, Exception> { h ->
            h.createQuery("SELECT * FROM koreader_devices WHERE user_id = ? ORDER BY created_at DESC")
                .bind(0, userId.toString())
                .map { row ->
                    KOReaderDevice(
                        token = row.getColumn("token", String::class.java),
                        userId = userId,
                        deviceName = row.getColumn("device_name", String::class.java),
                        lastSyncAt = row.getColumn("last_sync_at", String::class.java)?.let { runCatching { parseTs(it) }.getOrNull() },
                        createdAt = parseTs(row.getColumn("created_at", String::class.java)),
                    )
                }.list()
        }

    fun deleteDevice(userId: UUID, token: String): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM koreader_devices WHERE token = ? AND user_id = ?")
                .bind(0, token).bind(1, userId.toString()).execute()
        }
        return rows > 0
    }

    /**
     * Updates reading progress by document hash (KOReader identifies books by MD5 of content).
     * We match by file_hash in the books table.
     */
    fun pushProgress(
        userId: UUID,
        document: String,
        progress: String,
        percentage: Double,
        device: String?,
        deviceId: String?,
    ): Boolean {
        // Find book by file_hash or id
        val bookId = jdbi.withHandle<String?, Exception> { h ->
            h.createQuery("SELECT b.id FROM books b JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ? AND (b.file_hash = ? OR b.id = ?)")
                .bind(0, userId.toString()).bind(1, document).bind(2, document)
                .mapTo(String::class.java).firstOrNull()
        } ?: return false
        val bookUuid = runCatching { UUID.fromString(bookId) }.getOrNull() ?: return false
        val book = bookService.getBook(userId, bookUuid) ?: return false
        val page = (percentage * (book.pageCount ?: 1)).toInt().coerceAtLeast(1)
        bookService.updateProgress(userId, bookUuid, org.booktower.models.UpdateProgressRequest(page))
        kreaderLogger.debug("KOReader progress: user=$userId doc=$document pct=$percentage")
        return true
    }

    /**
     * Retrieves reading progress for a document, returned in KOReader sync format.
     */
    fun getProgress(userId: UUID, document: String): Map<String, Any?>? {
        val bookId = jdbi.withHandle<String?, Exception> { h ->
            h.createQuery("SELECT b.id FROM books b JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ? AND (b.file_hash = ? OR b.id = ?)")
                .bind(0, userId.toString()).bind(1, document).bind(2, document)
                .mapTo(String::class.java).firstOrNull()
        } ?: return null
        val bookUuid = runCatching { UUID.fromString(bookId) }.getOrNull() ?: return null
        val book = bookService.getBook(userId, bookUuid) ?: return null
        val pct = book.progress?.percentage?.div(100.0) ?: 0.0
        val page = book.progress?.currentPage ?: 0
        return mapOf(
            "document" to document,
            "progress" to page.toString(),
            "percentage" to pct,
            "device" to "booktower",
            "device_id" to "booktower",
            "timestamp" to (book.progress?.lastReadAt ?: Instant.now().toString()),
        )
    }

    fun touchLastSync(token: String) {
        jdbi.useHandle<Exception> { h ->
            h.createUpdate("UPDATE koreader_devices SET last_sync_at = ? WHERE token = ?")
                .bind(0, Instant.now().toString()).bind(1, token).execute()
        }
    }
}
