package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private val logger = LoggerFactory.getLogger("booktower.BulkMetadataRefreshService")

data class BulkMetadataRefreshResult(
    val queued: Int,
    val skipped: Int,
)

/**
 * Scans books missing metadata (cover, ISBN, description) and queues background
 * metadata fetches from OpenLibrary / Google Books.
 */
class BulkMetadataRefreshService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
    private val metadataFetchService: MetadataFetchService,
    private val backgroundTaskService: BackgroundTaskService,
) {
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "bulk-metadata-refresh").also {
                it.isDaemon = true
                it.priority = Thread.MIN_PRIORITY
            }
        }

    /**
     * Finds books owned by [userId] that are missing metadata (no ISBN, no description,
     * or no cover) and queues background metadata fetch for each.
     * If [libraryId] is provided, scopes to that library only.
     */
    fun refresh(
        userId: UUID,
        libraryId: String? = null,
    ): BulkMetadataRefreshResult {
        val libFilter = if (libraryId != null) "AND b.library_id = ?" else ""
        val books =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
                val q =
                    h.createQuery(
                        """
                    SELECT b.id, b.title, b.author
                    FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ?
                      AND (b.isbn IS NULL OR b.isbn = ''
                           OR b.description IS NULL OR b.description = ''
                           OR b.cover_path IS NULL OR b.cover_path = '')
                      $libFilter
                    ORDER BY b.title
                    """,
                    )
                q.bind(0, userId.toString())
                if (libraryId != null) q.bind(1, libraryId)
                q.mapToMap().list()
            }

        if (books.isEmpty()) return BulkMetadataRefreshResult(0, 0)

        val queued = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val taskId =
            backgroundTaskService.start(
                userId,
                "bulk-metadata-refresh",
                "Refreshing metadata for ${books.size} book(s)",
            )

        executor.submit {
            try {
                for (book in books) {
                    val bookId = book["id"] as? String ?: continue
                    val title = book["title"] as? String
                    val author = book["author"] as? String

                    if (title.isNullOrBlank()) {
                        skipped.incrementAndGet()
                        continue
                    }

                    try {
                        val meta = metadataFetchService.fetchMetadata(title, author)
                        if (meta == null) {
                            skipped.incrementAndGet()
                            continue
                        }
                        applyMetadata(userId, bookId, meta)
                        queued.incrementAndGet()
                        logger.debug("Metadata refreshed for '$title' from ${meta.source}")
                        Thread.sleep(500) // rate-limit external API calls
                    } catch (e: Exception) {
                        logger.warn("Metadata fetch failed for '$title': ${e.message}")
                        skipped.incrementAndGet()
                    }
                }
                backgroundTaskService.complete(
                    taskId,
                    "${queued.get()} updated, ${skipped.get()} skipped (of ${books.size} total)",
                )
                logger.info("Bulk metadata refresh complete: ${queued.get()} updated, ${skipped.get()} skipped")
            } catch (e: Exception) {
                logger.error("Bulk metadata refresh failed", e)
                backgroundTaskService.fail(taskId, e.message)
            }
        }

        return BulkMetadataRefreshResult(books.size, 0)
    }

    private fun applyMetadata(
        userId: UUID,
        bookId: String,
        meta: org.booktower.models.FetchedMetadata,
    ) {
        val uid = userId
        val bid = UUID.fromString(bookId)
        val existing = bookService.getBook(uid, bid) ?: return

        // Only fill in missing fields — never overwrite existing data
        val update =
            org.booktower.models.UpdateBookRequest(
                title = existing.title,
                author = existing.author,
                description = if (existing.description.isNullOrBlank()) meta.description else existing.description,
                isbn = if (existing.isbn.isNullOrBlank()) meta.isbn else existing.isbn,
                publisher = if (existing.publisher.isNullOrBlank()) meta.publisher else existing.publisher,
                publishedDate = if (existing.publishedDate.isNullOrBlank()) meta.publishedDate else existing.publishedDate,
                pageCount = existing.pageCount ?: meta.pageCount,
                series = if (existing.series.isNullOrBlank()) meta.series else existing.series,
                seriesIndex = existing.seriesIndex ?: meta.seriesIndex,
                language = if (existing.language.isNullOrBlank()) meta.language else existing.language,
            )
        bookService.updateBook(uid, bid, update)

        // Fetch cover if missing
        if (existing.coverUrl.isNullOrBlank() && meta.coverUrl != null) {
            fetchAndSaveCover(bookId, meta.coverUrl)
        } else if (existing.coverUrl.isNullOrBlank() && meta.openLibraryCoverId != null) {
            fetchAndSaveCover(bookId, "https://covers.openlibrary.org/b/id/${meta.openLibraryCoverId}-L.jpg")
        }
    }

    private fun fetchAndSaveCover(
        bookId: String,
        coverUrl: String,
    ) {
        try {
            val conn =
                java.net
                    .URI(coverUrl)
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "BookTower/1.0")
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) return

            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size < 1000) return // too small, likely placeholder

            val ext =
                when {
                    coverUrl.contains(".png", ignoreCase = true) -> "png"
                    coverUrl.contains(".webp", ignoreCase = true) -> "webp"
                    else -> "jpg"
                }
            val coverFilename = "$bookId.$ext"
            val coversDir =
                java.io.File(
                    org.booktower.config.AppConfig
                        .load()
                        .storage.coversPath,
                )
            if (!coversDir.exists() && !coversDir.mkdirs()) return
            java.io.File(coversDir, coverFilename).writeBytes(bytes)

            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate("UPDATE books SET cover_url = ? WHERE id = ?")
                    .bind(0, coverFilename)
                    .bind(1, bookId)
                    .execute()
            }
        } catch (e: Exception) {
            logger.debug("Cover fetch failed for $bookId: ${e.message}")
        }
    }
}
