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

private val koboLogger = LoggerFactory.getLogger("booktower.KoboSyncService")

data class KoboDevice(
    val token: String,
    val userId: UUID,
    val deviceName: String?,
    val lastSyncAt: Instant?,
    val createdAt: Instant,
)

class KoboSyncService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
    private val appBaseUrl: String,
    private val userSettingsService: UserSettingsService? = null,
) {
    fun isKepubEnabled(userId: UUID): Boolean =
        userSettingsService?.get(userId, "kobo.kepub_enabled") == "true"

    fun registerDevice(userId: UUID, deviceName: String?): KoboDevice {
        val token = UUID.randomUUID().toString()
        val now = Instant.now()
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                "INSERT INTO kobo_devices (token, user_id, device_name, created_at) VALUES (?, ?, ?, ?)",
            ).bind(0, token).bind(1, userId.toString()).bind(2, deviceName).bind(3, now.toString()).execute()
        }
        koboLogger.info("Kobo device registered for user $userId: $deviceName")
        return KoboDevice(token = token, userId = userId, deviceName = deviceName, lastSyncAt = null, createdAt = now)
    }

    fun getDevice(token: String): KoboDevice? =
        jdbi.withHandle<KoboDevice?, Exception> { h ->
            h.createQuery("SELECT * FROM kobo_devices WHERE token = ?")
                .bind(0, token)
                .map { row ->
                    KoboDevice(
                        token = row.getColumn("token", String::class.java),
                        userId = UUID.fromString(row.getColumn("user_id", String::class.java)),
                        deviceName = row.getColumn("device_name", String::class.java),
                        lastSyncAt = row.getColumn("last_sync_at", String::class.java)?.let { runCatching { parseTs(it) }.getOrNull() },
                        createdAt = parseTs(row.getColumn("created_at", String::class.java)),
                    )
                }.firstOrNull()
        }

    fun listDevices(userId: UUID): List<KoboDevice> =
        jdbi.withHandle<List<KoboDevice>, Exception> { h ->
            h.createQuery("SELECT * FROM kobo_devices WHERE user_id = ? ORDER BY created_at DESC")
                .bind(0, userId.toString())
                .map { row ->
                    KoboDevice(
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
            h.createUpdate("DELETE FROM kobo_devices WHERE token = ? AND user_id = ?")
                .bind(0, token).bind(1, userId.toString()).execute()
        }
        return rows > 0
    }

    /** Returns the Kobo-formatted book list for the user. */
    fun buildBookList(userId: UUID): List<Map<String, Any>> {
        val books = bookService.getBooks(userId, null, page = 1, pageSize = 500).getBooks()
        return buildBookListFromBooks(books, userId)
    }

    fun updateReadingState(
        userId: UUID,
        bookId: UUID,
        percentRead: Double?,
        currentPage: Int?,
        location: String? = null,
        locationType: String? = null,
    ): Boolean {
        val book = bookService.getBook(userId, bookId) ?: return false
        val page = currentPage ?: percentRead?.let { pct ->
            ((book.pageCount ?: 0) * pct).toInt().coerceAtLeast(1)
        } ?: return false
        bookService.updateProgress(userId, bookId, org.booktower.models.UpdateProgressRequest(page))
        // Store CFI location if provided
        if (location != null || locationType != null) {
            try {
                jdbi.useHandle<Exception> { h ->
                    h.createUpdate(
                        "UPDATE reading_progress SET location = ?, location_type = ? WHERE user_id = ? AND book_id = ?",
                    ).bind(0, location).bind(1, locationType)
                        .bind(2, userId.toString()).bind(3, bookId.toString()).execute()
                }
            } catch (_: Exception) { /* non-critical */ }
        }
        return true
    }

    fun touchLastSync(token: String) {
        jdbi.useHandle<Exception> { h ->
            h.createUpdate("UPDATE kobo_devices SET last_sync_at = ? WHERE token = ?")
                .bind(0, Instant.now().toString()).bind(1, token).execute()
        }
    }

    /**
     * Delta sync: returns only books whose progress or metadata changed after [sinceToken].
     * [sinceToken] is an epoch-millisecond timestamp string from the previous sync response.
     * Returns all books if [sinceToken] is null or blank.
     */
    fun buildDeltaBookList(userId: UUID, sinceToken: String?): List<Map<String, Any>> {
        if (sinceToken.isNullOrBlank()) return buildBookList(userId)
        val sinceMs = sinceToken.toLongOrNull() ?: return buildBookList(userId)
        val sinceInstant = Instant.ofEpochMilli(sinceMs)
        val books = bookService.getBooks(userId, null, page = 1, pageSize = 500).getBooks()
        val changed = books.filter { book ->
            val updatedAt = runCatching { Instant.parse(book.addedAt) }
                .getOrElse { runCatching { parseTs(book.addedAt) }.getOrElse { Instant.now() } }
            updatedAt.isAfter(sinceInstant)
        }
        return if (changed.isEmpty()) emptyList() else buildBookListFromBooks(changed, userId)
    }

    /**
     * Full library snapshot — returns ALL books with current reading state for initial sync.
     */
    fun buildSnapshot(userId: UUID): Map<String, Any> {
        val allBooks = bookService.getBooks(userId, null, page = 1, pageSize = 500).getBooks()
        val books = buildBookListFromBooks(allBooks, userId)
        return mapOf(
            "Snapshot" to books,
            "SnapshotTimestamp" to Instant.now().toString(),
            "TotalCount" to books.size,
        )
    }

    /** Build Kobo-format book list from a specific book list. */
    private fun buildBookListFromBooks(books: List<org.booktower.models.BookDto>, userId: UUID? = null): List<Map<String, Any>> {
        val locationData = fetchLocationData(books.map { it.id })
        return books.map { book -> buildBookEntry(book, locationData[book.id], userId) }
    }

    private fun fetchLocationData(bookIds: List<String>): Map<String, Pair<String?, String?>> {
        if (bookIds.isEmpty()) return emptyMap()
        return try {
            jdbi.withHandle<Map<String, Pair<String?, String?>>, Exception> { h ->
                val result = mutableMapOf<String, Pair<String?, String?>>()
                for (id in bookIds) {
                    val row = h.createQuery(
                        "SELECT location, location_type FROM reading_progress WHERE book_id = ?",
                    ).bind(0, id)
                        .map { r -> Pair(r.getColumn("location", String::class.java), r.getColumn("location_type", String::class.java)) }
                        .firstOrNull()
                    if (row != null) result[id] = row
                }
                result
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun buildBookEntry(book: org.booktower.models.BookDto, locationInfo: Pair<String?, String?>?, userId: UUID? = null): Map<String, Any> {
        val ext = book.filePath?.substringAfterLast('.', "")?.lowercase() ?: ""
        val kepubEnabled = userId != null && ext == "epub" && isKepubEnabled(userId)
        val format = when {
            kepubEnabled -> "KEPUB"
            ext == "pdf" -> "PDF"
            else -> "EPUB3"
        }
        val downloadUrl = if (kepubEnabled) "$appBaseUrl/api/books/${book.id}/kepub"
                          else "$appBaseUrl/api/books/${book.id}/file"
        val iso = book.addedAt
        val pct = book.progress?.percentage?.div(100.0) ?: 0.0
        val readingStatus = if (pct > 0) "Reading" else "NotStarted"
        val location = locationInfo?.first
        val locationType = locationInfo?.second
        return mapOf(
            "EntitlementId" to book.id,
            "NewEntitlement" to mapOf(
                "BookEntitlement" to mapOf(
                    "Accessibility" to "Full",
                    "ActivePeriod" to mapOf("From" to iso),
                    "Created" to iso,
                    "CrossRevisionId" to book.id,
                    "Id" to book.id,
                    "IsRemoved" to false,
                    "IsHiddenFromDefaultLists" to false,
                    "IsInternetArchive" to false,
                ),
                "BookMetadata" to mapOf(
                    "Title" to book.title,
                    "Author" to (book.author ?: ""),
                    "Description" to (book.description ?: ""),
                    "RevisionId" to book.id,
                    "WorkId" to book.id,
                    "DownloadUrls" to listOf(
                        mapOf("Format" to format, "Url" to downloadUrl, "DRMType" to "None"),
                    ),
                ),
                "ReadingState" to mapOf(
                    "Created" to iso,
                    "CurrentBookmark" to mapOf(
                        "ContentSourceProgressPercent" to pct,
                        "Location" to location,
                        "LocationType" to locationType,
                    ),
                    "LastModified" to iso,
                    "PriorityTimestamp" to iso,
                    "StatusInfo" to mapOf(
                        "LastModified" to iso,
                        "Status" to readingStatus,
                        "TimesStartedReading" to 0,
                    ),
                ),
            ),
        )
    }
}
