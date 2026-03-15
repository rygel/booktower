package org.booktower.services

import org.booktower.models.*
import org.booktower.models.FetchedMetadata
import org.booktower.models.ReadStatus
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.BookService")

class BookService(
    private val jdbi: Jdbi,
    private val analyticsService: AnalyticsService? = null,
    private val readingSessionService: ReadingSessionService? = null,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
        private const val PERCENTAGE_MULTIPLIER = 100.0
    }
    fun getBooks(
        userId: UUID,
        libraryId: String?,
        page: Int = 1,
        pageSize: Int = 20,
        sortBy: BookSortOrder = BookSortOrder.TITLE,
        statusFilter: String? = null,
        tagFilter: String? = null,
        ratingGte: Int? = null,
    ): BookListDto {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val offset = (safePage - 1) * safePageSize
        val orderClause = sortBy.sql // whitelisted from enum, safe to interpolate
        val statusClause = if (statusFilter != null) " AND bs.status = ?" else ""
        val tagClause = if (tagFilter != null) " AND EXISTS (SELECT 1 FROM book_tags bt WHERE bt.book_id = b.id AND bt.user_id = ? AND bt.tag = ?)" else ""
        val ratingGteClause = if (ratingGte != null) " AND br.rating >= ?" else ""

        val books =
            if (libraryId != null) {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    val q = handle.createQuery(
                        """
                        SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                        INNER JOIN libraries l ON b.library_id = l.id
                        LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                        LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                        WHERE b.library_id = ? AND l.user_id = ?${statusClause}${tagClause}${ratingGteClause}
                        ORDER BY $orderClause LIMIT ? OFFSET ?
                        """,
                    )
                        .bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, libraryId)
                        .bind(3, userId.toString())
                    var idx = 4
                    if (statusFilter != null) { q.bind(idx++, statusFilter) }
                    if (tagFilter != null) { q.bind(idx++, userId.toString()); q.bind(idx++, tagFilter) }
                    if (ratingGte != null) { q.bind(idx++, ratingGte) }
                    q.bind(idx++, safePageSize)
                    q.bind(idx, offset)
                    q.map { row -> mapBook(row) }.list()
                }
            } else {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    val q = handle.createQuery(
                        """
                    SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                    LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                    WHERE l.user_id = ?${statusClause}${tagClause}${ratingGteClause}
                    ORDER BY $orderClause LIMIT ? OFFSET ?
                """,
                    )
                        .bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, userId.toString())
                    var idx = 3
                    if (statusFilter != null) { q.bind(idx++, statusFilter) }
                    if (tagFilter != null) { q.bind(idx++, userId.toString()); q.bind(idx++, tagFilter) }
                    if (ratingGte != null) { q.bind(idx++, ratingGte) }
                    q.bind(idx++, safePageSize)
                    q.bind(idx, offset)
                    q.map { row -> mapBook(row) }.list()
                }
            }

        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val enriched = books.map { it.copy(tags = tagMap[it.id] ?: emptyList()) }

        val total =
            if (libraryId != null) {
                jdbi.withHandle<Int, Exception> { handle ->
                    val q = handle.createQuery(
                        """
                        SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                        LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                        LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                        WHERE b.library_id = ? AND l.user_id = ?${statusClause}${tagClause}${ratingGteClause}
                        """,
                    )
                        .bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, libraryId)
                        .bind(3, userId.toString())
                    var idx = 4
                    if (statusFilter != null) { q.bind(idx++, statusFilter) }
                    if (tagFilter != null) { q.bind(idx++, userId.toString()); q.bind(idx++, tagFilter) }
                    if (ratingGte != null) { q.bind(idx++, ratingGte) }
                    q.mapTo(Int::class.java).first() ?: 0
                }
            } else {
                jdbi.withHandle<Int, Exception> { handle ->
                    val q = handle.createQuery(
                        """
                        SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                        LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                        LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                        WHERE l.user_id = ?${statusClause}${tagClause}${ratingGteClause}
                        """,
                    )
                        .bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, userId.toString())
                    var idx = 3
                    if (statusFilter != null) { q.bind(idx++, statusFilter) }
                    if (tagFilter != null) { q.bind(idx++, userId.toString()); q.bind(idx++, tagFilter) }
                    if (ratingGte != null) { q.bind(idx++, ratingGte) }
                    q.mapTo(Int::class.java).first() ?: 0
                }
            }

        return BookListDto(enriched, total, safePage, safePageSize)
    }

    fun searchBooks(
        userId: UUID,
        query: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): BookListDto {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val offset = (safePage - 1) * safePageSize
        val likeQuery = "%${query.trim().lowercase()}%"

        val books = jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ?
                  AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.description) LIKE ?)
                ORDER BY b.title LIMIT ? OFFSET ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, userId.toString())
                .bind(2, userId.toString())
                .bind(3, likeQuery)
                .bind(4, likeQuery)
                .bind(5, likeQuery)
                .bind(6, safePageSize)
                .bind(7, offset)
                .map { row -> mapBook(row) }.list()
        }

        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val enriched = books.map { it.copy(tags = tagMap[it.id] ?: emptyList()) }

        val total = jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery(
                """
                SELECT COUNT(*) FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                WHERE l.user_id = ?
                  AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.description) LIKE ?)
                """,
            )
                .bind(0, userId.toString())
                .bind(1, userId.toString())
                .bind(2, likeQuery)
                .bind(3, likeQuery)
                .bind(4, likeQuery)
                .mapTo(java.lang.Integer::class.java).first()?.toInt() ?: 0
        }

        return BookListDto(enriched, total, safePage, safePageSize)
    }

    fun getBook(
        userId: UUID,
        bookId: UUID,
    ): BookDto? {
        val book = jdbi.withHandle<BookDto?, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.*, rp.current_page AS rp_current_page, rp.total_pages AS rp_total_pages,
                       rp.percentage AS rp_percentage, rp.last_read_at AS rp_last_read_at,
                       bs.status AS book_status_value, br.rating AS book_rating_value
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                LEFT JOIN reading_progress rp ON rp.book_id = b.id AND rp.user_id = :userId
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = :userId
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = :userId
                WHERE b.id = :bookId AND l.user_id = :userId
                """,
            )
                .bind("userId", userId.toString())
                .bind("bookId", bookId.toString())
                .map { row ->
                    val book = mapBook(row)
                    val currentPage = try {
                        row.getColumn("rp_current_page", java.lang.Integer::class.java)?.toInt()
                    } catch (_: Exception) { null }
                    if (currentPage != null) {
                        book.copy(progress = ReadingProgressDto(
                            currentPage = currentPage,
                            totalPages = try {
                                row.getColumn("rp_total_pages", java.lang.Integer::class.java)?.toInt()
                            } catch (_: Exception) { null },
                            percentage = try {
                                row.getColumn("rp_percentage", java.lang.Double::class.java)?.toDouble()
                            } catch (_: Exception) { null },
                            lastReadAt = try {
                                row.getColumn("rp_last_read_at", String::class.java) ?: ""
                            } catch (_: Exception) { "" },
                        ))
                    } else book
                }.firstOrNull()
        }
        val tags = if (book != null) fetchTagsForBooks(userId, listOf(book.id))[book.id] ?: emptyList() else emptyList()
        return book?.copy(tags = tags)
    }

    fun getRecentBooks(
        userId: UUID,
        limit: Int = 10,
    ): List<BookDto> {
        return jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.* FROM reading_progress rp
                INNER JOIN books b ON rp.book_id = b.id
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE rp.user_id = ?
                ORDER BY rp.last_read_at DESC LIMIT ?
            """,
            )
                .bind(0, userId.toString())
                .bind(1, limit)
                .map { row -> mapBook(row) }.list()
        }
    }

    fun createBook(
        userId: UUID,
        request: CreateBookRequest,
    ): Result<BookDto> {
        val libId = UUID.fromString(request.libraryId)

        jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM libraries WHERE user_id = ? AND id = ?")
                .bind(0, userId.toString())
                .bind(1, libId.toString())
                .mapTo(String::class.java).firstOrNull()
        } ?: return Result.failure(IllegalArgumentException("Library not found"))

        val now = Instant.now()
        val bookId = UUID.randomUUID()

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO books (id, library_id, title, author, description,
                    file_path, file_size, added_at, updated_at)
                VALUES (?, ?, ?, ?, ?, '', 0, ?, ?)
                """,
            )
                .bind(0, bookId.toString())
                .bind(1, libId.toString())
                .bind(2, request.title)
                .bind(3, request.author)
                .bind(4, request.description)
                .bind(5, now.toString())
                .bind(6, now.toString())
                .execute()
        }

        logger.info("Book created: ${request.title}")

        return Result.success(
            BookDto(
                id = bookId.toString(),
                libraryId = libId.toString(),
                title = request.title,
                author = request.author,
                description = request.description,
                coverUrl = null,
                pageCount = null,
                fileSize = 0,
                addedAt = now.toString(),
                progress = null,
            ),
        )
    }

    fun updateBook(
        userId: UUID,
        bookId: UUID,
        request: UpdateBookRequest,
    ): BookDto? {
        val book = getBook(userId, bookId) ?: return null

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                "UPDATE books SET title = ?, author = ?, description = ?, series = ?, series_index = ?, updated_at = ? WHERE id = ?",
            )
                .bind(0, request.title)
                .bind(1, request.author)
                .bind(2, request.description)
                .bind(3, request.series)
                .bind(4, request.seriesIndex)
                .bind(5, Instant.now().toString())
                .bind(6, bookId.toString())
                .execute()
        }

        logger.info("Book updated: ${request.title}")
        return book.copy(
            title = request.title,
            author = request.author,
            description = request.description,
            series = request.series,
            seriesIndex = request.seriesIndex,
        )
    }

    fun applyFetchedMetadata(
        userId: UUID,
        bookId: UUID,
        meta: FetchedMetadata,
    ): BookDto? {
        val book = getBook(userId, bookId) ?: return null
        val newTitle = meta.title?.takeIf { it.isNotBlank() } ?: book.title
        val newAuthor = meta.author?.takeIf { it.isNotBlank() } ?: book.author
        val newDesc = meta.description?.takeIf { it.isNotBlank() } ?: book.description
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                """UPDATE books SET title = ?, author = ?, description = ?,
                   isbn = ?, publisher = ?, published_date = ?, updated_at = ?
                   WHERE id = ?"""
            )
                .bind(0, newTitle)
                .bind(1, newAuthor)
                .bind(2, newDesc)
                .bind(3, meta.isbn ?: book.isbn)
                .bind(4, meta.publisher ?: book.publisher)
                .bind(5, normalizeDate(meta.publishedDate ?: book.publishedDate))
                .bind(6, Instant.now().toString())
                .bind(7, bookId.toString())
                .execute()
        }
        logger.info("Metadata applied for book $bookId from Open Library")
        return book.copy(
            title = newTitle,
            author = newAuthor,
            description = newDesc,
            isbn = meta.isbn ?: book.isbn,
            publisher = meta.publisher ?: book.publisher,
            publishedDate = meta.publishedDate ?: book.publishedDate,
        )
    }

    /** Converts a year-only string like "1984" to "1984-01-01" for DATE columns. */
    private fun normalizeDate(date: String?): String? =
        if (date != null && date.matches(Regex("\\d{4}"))) "$date-01-01" else date

    /**
     * Moves a book to a different library, both of which must belong to [userId].
     * Returns the updated book, or null if the book or target library is not found.
     */
    fun moveBook(userId: UUID, bookId: UUID, targetLibraryId: UUID): BookDto? {
        val book = getBook(userId, bookId) ?: return null
        // Verify the target library belongs to this user
        val targetExists = jdbi.withHandle<Boolean, Exception> { handle ->
            handle.createQuery("SELECT COUNT(*) FROM libraries WHERE id = ? AND user_id = ?")
                .bind(0, targetLibraryId.toString())
                .bind(1, userId.toString())
                .mapTo(Int::class.java)
                .first() > 0
        }
        if (!targetExists) return null
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("UPDATE books SET library_id = ?, updated_at = ? WHERE id = ?")
                .bind(0, targetLibraryId.toString())
                .bind(1, Instant.now().toString())
                .bind(2, bookId.toString())
                .execute()
        }
        logger.info("Book moved: ${book.title} → library $targetLibraryId")
        return book.copy(libraryId = targetLibraryId.toString())
    }

    fun deleteBook(
        userId: UUID,
        bookId: UUID,
    ): Boolean {
        val book = getBook(userId, bookId) ?: return false

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM books WHERE id = ?").bind(0, bookId.toString()).execute()
        }

        logger.info("Book deleted: ${book.title}")
        return true
    }

    fun getBookFilePath(
        userId: UUID,
        bookId: UUID,
    ): String? {
        return jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.file_path FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE b.id = ? AND l.user_id = ?
                """,
            )
                .bind(0, bookId.toString())
                .bind(1, userId.toString())
                .mapTo(String::class.java).firstOrNull()
        }
    }

    fun updateFileInfo(
        userId: UUID,
        bookId: UUID,
        filePath: String,
        fileSize: Long,
    ): Boolean {
        val updated = jdbi.withHandle<Int, Exception> { handle ->
            handle.createUpdate(
                """
                UPDATE books SET file_path = ?, file_size = ?, updated_at = ?
                WHERE id = ?
                AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)
                """,
            )
                .bind(0, filePath)
                .bind(1, fileSize)
                .bind(2, java.time.Instant.now().toString())
                .bind(3, bookId.toString())
                .bind(4, userId.toString())
                .execute()
        }
        return updated > 0
    }

    fun updateCoverPath(userId: UUID, bookId: UUID, coverFilename: String): Boolean {
        val updated = jdbi.withHandle<Int, Exception> { handle ->
            handle.createUpdate(
                """
                UPDATE books SET cover_path = ?, updated_at = ?
                WHERE id = ?
                AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)
                """,
            )
                .bind(0, coverFilename)
                .bind(1, Instant.now().toString())
                .bind(2, bookId.toString())
                .bind(3, userId.toString())
                .execute()
        }
        return updated > 0
    }

    fun updateProgress(
        userId: UUID,
        bookId: UUID,
        request: UpdateProgressRequest,
    ): ReadingProgressDto? {
        val book = getBook(userId, bookId) ?: return null
        val now = Instant.now()
        val totalPages = book.pageCount ?: 0
        val percentage = if (totalPages > 0) (request.currentPage.toDouble() / totalPages) * PERCENTAGE_MULTIPLIER else null

        val existing =
            jdbi.withHandle<ReadingProgressDto?, Exception> { handle ->
                handle.createQuery("SELECT * FROM reading_progress WHERE user_id = ? AND book_id = ?")
                    .bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .map { row ->
                        ReadingProgressDto(
                            currentPage = row.getColumn("current_page", java.lang.Integer::class.java)?.toInt() ?: 0,
                            totalPages = row.getColumn("total_pages", java.lang.Integer::class.java)?.toInt(),
                            percentage = row.getColumn("percentage", java.lang.Double::class.java)?.toDouble(),
                            lastReadAt = row.getColumn("last_read_at", String::class.java),
                        )
                    }.firstOrNull()
            }

        val previousPage = existing?.currentPage ?: 0

        if (existing != null) {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate(
                    "UPDATE reading_progress SET current_page = ?, percentage = ?, last_read_at = ? WHERE user_id = ? AND book_id = ?",
                )
                    .bind(0, request.currentPage)
                    .bind(1, percentage)
                    .bind(2, now.toString())
                    .bind(3, userId.toString())
                    .bind(4, bookId.toString())
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate(
                    """
                    INSERT INTO reading_progress
                        (id, user_id, book_id, current_page, total_pages, percentage, last_read_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                )
                    .bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, request.currentPage)
                    .bind(4, totalPages.takeIf { it > 0 })
                    .bind(5, percentage)
                    .bind(6, now.toString())
                    .bind(7, now.toString())
                    .execute()
            }
        }

        val delta = maxOf(0, request.currentPage - previousPage)
        analyticsService?.recordProgress(userId, bookId, delta)
        if (delta > 0) {
            readingSessionService?.recordSession(userId, bookId, previousPage, request.currentPage, delta)
        }

        logger.info("Progress updated for book ${book.title}: page ${request.currentPage}")
        return ReadingProgressDto(request.currentPage, totalPages.takeIf { it > 0 }, percentage, now.toString())
    }

    fun getRecentlyAddedBooks(userId: UUID, limit: Int = 6): List<BookDto> {
        return jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.* FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ?
                ORDER BY b.added_at DESC, b.title
                LIMIT ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, limit)
                .map { row -> mapBook(row) }.list()
        }
    }

    private fun mapBook(row: RowView): BookDto {
        val pageCount: Int? = try {
            row.getColumn("page_count", java.lang.Integer::class.java)?.toInt()
        } catch (e: Exception) {
            null
        }
        val status: String? = try {
            row.getColumn("book_status_value", String::class.java)
        } catch (_: Exception) { null }
        val rating: Int? = try {
            row.getColumn("book_rating_value", java.lang.Integer::class.java)?.toInt()
        } catch (_: Exception) { null }
        return BookDto(
            id = row.getColumn("id", String::class.java),
            libraryId = row.getColumn("library_id", String::class.java) ?: "",
            title = row.getColumn("title", String::class.java),
            author = row.getColumn("author", String::class.java),
            description = row.getColumn("description", String::class.java),
            coverUrl = row.getColumn("cover_path", String::class.java)?.let { "/covers/$it" },
            pageCount = pageCount,
            fileSize = row.getColumn("file_size", java.lang.Long::class.java)?.toLong() ?: 0L,
            addedAt = row.getColumn("added_at", String::class.java),
            progress = null,
            status = status,
            rating = rating,
            isbn = try { row.getColumn("isbn", String::class.java) } catch (_: Exception) { null },
            publisher = try { row.getColumn("publisher", String::class.java) } catch (_: Exception) { null },
            publishedDate = try { row.getColumn("published_date", String::class.java) } catch (_: Exception) { null },
            series = try { row.getColumn("series", String::class.java) } catch (_: Exception) { null },
            seriesIndex = try { row.getColumn("series_index", java.lang.Double::class.java)?.toDouble() } catch (_: Exception) { null },
        )
    }

    private fun fetchTagsForBooks(userId: UUID, bookIds: List<String>): Map<String, List<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return jdbi.withHandle<Map<String, List<String>>, Exception> { handle ->
            val placeholders = bookIds.joinToString(",") { "?" }
            val q = handle.createQuery(
                "SELECT book_id, tag FROM book_tags WHERE user_id = ? AND book_id IN ($placeholders) ORDER BY tag"
            )
            q.bind(0, userId.toString())
            bookIds.forEachIndexed { i, id -> q.bind(i + 1, id) }
            q.map { row ->
                val bookId = row.getColumn("book_id", String::class.java)
                val tag = row.getColumn("tag", String::class.java)
                Pair(bookId, tag)
            }.list().groupBy({ it.first }, { it.second })
        }
    }

    /** Returns all books for a user matching a shelf rule (no pagination limit). Used by MagicShelfService. */
    fun getBooksForShelf(
        userId: UUID,
        statusFilter: String? = null,
        tagFilter: String? = null,
        ratingGte: Int? = null,
    ): List<BookDto> {
        val statusClause = if (statusFilter != null) " AND bs.status = ?" else ""
        val tagClause = if (tagFilter != null)
            " AND EXISTS (SELECT 1 FROM book_tags bt WHERE bt.book_id = b.id AND bt.user_id = ? AND bt.tag = ?)"
        else ""
        val ratingGteClause = if (ratingGte != null) " AND br.rating >= ?" else ""

        val books = jdbi.withHandle<List<BookDto>, Exception> { handle ->
            val q = handle.createQuery(
                """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ?${statusClause}${tagClause}${ratingGteClause}
                ORDER BY b.title
                """,
            )
            q.bind(0, userId.toString())
            q.bind(1, userId.toString())
            q.bind(2, userId.toString())
            var idx = 3
            if (statusFilter != null) q.bind(idx++, statusFilter)
            if (tagFilter != null) { q.bind(idx++, userId.toString()); q.bind(idx++, tagFilter) }
            if (ratingGte != null) q.bind(idx++, ratingGte)
            q.map { row -> mapBook(row) }.list()
        }
        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        return books.map { it.copy(tags = tagMap[it.id] ?: emptyList()) }
    }

    fun setTags(userId: UUID, bookId: UUID, tags: List<String>) {
        val cleanTags = tags.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it.length <= 50 }
            .distinct()
            .take(10)
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM book_tags WHERE user_id = ? AND book_id = ?")
                .bind(0, userId.toString()).bind(1, bookId.toString()).execute()
            for (tag in cleanTags) {
                handle.createUpdate(
                    "INSERT INTO book_tags (id, user_id, book_id, tag) VALUES (?, ?, ?, ?)"
                )
                .bind(0, java.util.UUID.randomUUID().toString())
                .bind(1, userId.toString())
                .bind(2, bookId.toString())
                .bind(3, tag)
                .execute()
            }
        }
        logger.info("Tags set for book $bookId: $cleanTags")
    }

    fun getUserTags(userId: UUID): List<String> {
        return jdbi.withHandle<List<String>, Exception> { handle ->
            handle.createQuery(
                "SELECT DISTINCT tag FROM book_tags WHERE user_id = ? ORDER BY tag"
            )
            .bind(0, userId.toString())
            .mapTo(String::class.java).list()
        }
    }

    fun setStatus(userId: UUID, bookId: UUID, status: ReadStatus?) {
        val now = Instant.now().toString()
        val existing = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM book_status WHERE user_id = ? AND book_id = ?")
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .mapTo(String::class.java).firstOrNull()
        }
        if (status == null) {
            if (existing != null) {
                jdbi.useHandle<Exception> { handle ->
                    handle.createUpdate("DELETE FROM book_status WHERE user_id = ? AND book_id = ?")
                        .bind(0, userId.toString()).bind(1, bookId.toString()).execute()
                }
            }
            return
        }
        if (existing != null) {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate("UPDATE book_status SET status = ?, updated_at = ? WHERE user_id = ? AND book_id = ?")
                    .bind(0, status.name).bind(1, now)
                    .bind(2, userId.toString()).bind(3, bookId.toString()).execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate("INSERT INTO book_status (id, user_id, book_id, status, updated_at) VALUES (?, ?, ?, ?, ?)")
                    .bind(0, UUID.randomUUID().toString()).bind(1, userId.toString())
                    .bind(2, bookId.toString()).bind(3, status.name).bind(4, now).execute()
            }
        }
        logger.info("Book status set to ${status.name} for book $bookId")
    }

    fun countFinishedThisYear(userId: UUID, year: Int): Int {
        return jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery(
                """
                SELECT COUNT(*) FROM book_status
                WHERE user_id = ? AND status = 'FINISHED'
                  AND YEAR(updated_at) = ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, year)
                .mapTo(java.lang.Integer::class.java).first()?.toInt() ?: 0
        }
    }

    fun setRating(userId: UUID, bookId: UUID, rating: Int?) {
        val existing = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM book_ratings WHERE user_id = ? AND book_id = ?")
                .bind(0, userId.toString()).bind(1, bookId.toString())
                .mapTo(String::class.java).firstOrNull()
        }
        if (rating == null || rating !in 1..5) {
            if (existing != null) {
                jdbi.useHandle<Exception> { handle ->
                    handle.createUpdate("DELETE FROM book_ratings WHERE user_id = ? AND book_id = ?")
                        .bind(0, userId.toString()).bind(1, bookId.toString()).execute()
                }
            }
            return
        }
        val now = Instant.now().toString()
        if (existing != null) {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate("UPDATE book_ratings SET rating = ?, updated_at = ? WHERE user_id = ? AND book_id = ?")
                    .bind(0, rating).bind(1, now).bind(2, userId.toString()).bind(3, bookId.toString()).execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate("INSERT INTO book_ratings (id, user_id, book_id, rating, updated_at) VALUES (?, ?, ?, ?, ?)")
                    .bind(0, UUID.randomUUID().toString()).bind(1, userId.toString())
                    .bind(2, bookId.toString()).bind(3, rating).bind(4, now).execute()
            }
        }
        logger.info("Book rating set to $rating for book $bookId")
    }

    // ── Bulk operations ───────────────────────────────────────────────────────

    fun bulkMove(userId: UUID, bookIds: List<UUID>, targetLibraryId: UUID): Int {
        val targetExists = jdbi.withHandle<Boolean, Exception> { handle ->
            handle.createQuery("SELECT COUNT(*) FROM libraries WHERE id = ? AND user_id = ?")
                .bind(0, targetLibraryId.toString()).bind(1, userId.toString())
                .mapTo(Int::class.java).first() > 0
        }
        if (!targetExists) return 0
        return jdbi.withHandle<Int, Exception> { handle ->
            val now = java.time.Instant.now().toString()
            bookIds.count { bookId ->
                handle.createUpdate(
                    """UPDATE books SET library_id = ?, updated_at = ?
                       WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)""",
                )
                    .bind(0, targetLibraryId.toString()).bind(1, now)
                    .bind(2, bookId.toString()).bind(3, userId.toString())
                    .execute() > 0
            }
        }
    }

    fun bulkDelete(userId: UUID, bookIds: List<UUID>): Int =
        jdbi.withHandle<Int, Exception> { handle ->
            bookIds.count { bookId ->
                handle.createUpdate(
                    "DELETE FROM books WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)",
                )
                    .bind(0, bookId.toString()).bind(1, userId.toString())
                    .execute() > 0
            }
        }

    fun bulkTag(userId: UUID, bookIds: List<UUID>, tags: List<String>): Int =
        jdbi.withHandle<Int, Exception> { handle ->
            bookIds.count { bookId ->
                val owned = handle.createQuery(
                    """SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                       WHERE b.id = ? AND l.user_id = ?""",
                )
                    .bind(0, bookId.toString()).bind(1, userId.toString())
                    .mapTo(Int::class.java).first() > 0
                if (!owned) return@count false
                handle.createUpdate("DELETE FROM book_tags WHERE book_id = ?")
                    .bind(0, bookId.toString()).execute()
                tags.forEach { tag ->
                    handle.createUpdate(
                        "INSERT INTO book_tags (id, user_id, book_id, tag) VALUES (?, ?, ?, ?)",
                    )
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, bookId.toString())
                        .bind(3, tag)
                        .execute()
                }
                true
            }
        }

    fun bulkStatus(userId: UUID, bookIds: List<UUID>, status: String): Int {
        val readStatus = if (status.isBlank() || status == "NONE") null
                         else runCatching { ReadStatus.valueOf(status) }.getOrNull()
        val now = java.time.Instant.now().toString()
        return jdbi.withHandle<Int, Exception> { handle ->
            bookIds.count { bookId ->
                // Verify ownership
                val owned = handle.createQuery(
                    "SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE b.id = ? AND l.user_id = ?",
                )
                    .bind(0, bookId.toString()).bind(1, userId.toString())
                    .mapTo(Int::class.java).first() > 0
                if (!owned) return@count false
                val existing = handle.createQuery(
                    "SELECT id FROM book_status WHERE user_id = ? AND book_id = ?",
                )
                    .bind(0, userId.toString()).bind(1, bookId.toString())
                    .mapTo(String::class.java).firstOrNull()
                if (readStatus == null) {
                    if (existing != null) {
                        handle.createUpdate("DELETE FROM book_status WHERE user_id = ? AND book_id = ?")
                            .bind(0, userId.toString()).bind(1, bookId.toString()).execute()
                    }
                } else if (existing != null) {
                    handle.createUpdate("UPDATE book_status SET status = ?, updated_at = ? WHERE user_id = ? AND book_id = ?")
                        .bind(0, readStatus.name).bind(1, now)
                        .bind(2, userId.toString()).bind(3, bookId.toString()).execute()
                } else {
                    handle.createUpdate("INSERT INTO book_status (id, user_id, book_id, status, updated_at) VALUES (?, ?, ?, ?, ?)")
                        .bind(0, UUID.randomUUID().toString()).bind(1, userId.toString())
                        .bind(2, bookId.toString()).bind(3, readStatus.name).bind(4, now).execute()
                }
                true
            }
        }
    }
}
