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
    private val metadataLockService: MetadataLockService? = null,
    private val ftsService: org.booktower.services.FtsService? = null,
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
        formatFilter: String? = null,
    ): BookListDto {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val offset = (safePage - 1) * safePageSize
        val orderClause = sortBy.sql // whitelisted from enum, safe to interpolate
        val statusClause = if (statusFilter != null) " AND bs.status = ?" else ""
        val formatClause = if (formatFilter != null) " AND b.book_format = ?" else ""
        val tagClause = if (tagFilter != null) " AND EXISTS (SELECT 1 FROM book_tags bt WHERE bt.book_id = b.id AND bt.user_id = ? AND bt.tag = ?)" else ""
        val ratingGteClause = if (ratingGte != null) " AND br.rating >= ?" else ""

        val books =
            if (libraryId != null) {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    val q =
                        handle
                            .createQuery(
                                """
                        SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                        INNER JOIN libraries l ON b.library_id = l.id
                        LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                        LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                        WHERE b.library_id = ? AND l.user_id = ?${statusClause}${tagClause}${ratingGteClause}$formatClause
                        ORDER BY $orderClause LIMIT ? OFFSET ?
                        """,
                            ).bind(0, userId.toString())
                            .bind(1, userId.toString())
                            .bind(2, libraryId)
                            .bind(3, userId.toString())
                    var idx = 4
                    if (statusFilter != null) q.bind(idx++, statusFilter)
                    if (tagFilter != null) {
                        q.bind(idx++, userId.toString())
                        q.bind(idx++, tagFilter)
                    }
                    if (ratingGte != null) q.bind(idx++, ratingGte)
                    if (formatFilter != null) q.bind(idx++, formatFilter.uppercase())
                    q.bind(idx++, safePageSize)
                    q.bind(idx, offset)
                    q.map { row -> mapBook(row) }.list()
                }
            } else {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    val q =
                        handle
                            .createQuery(
                                """
                    SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                    LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                    WHERE l.user_id = ?${statusClause}${tagClause}${ratingGteClause}$formatClause
                    ORDER BY $orderClause LIMIT ? OFFSET ?
                """,
                            ).bind(0, userId.toString())
                            .bind(1, userId.toString())
                            .bind(2, userId.toString())
                    var idx = 3
                    if (statusFilter != null) q.bind(idx++, statusFilter)
                    if (tagFilter != null) {
                        q.bind(idx++, userId.toString())
                        q.bind(idx++, tagFilter)
                    }
                    if (ratingGte != null) q.bind(idx++, ratingGte)
                    if (formatFilter != null) q.bind(idx++, formatFilter.uppercase())
                    q.bind(idx++, safePageSize)
                    q.bind(idx, offset)
                    q.map { row -> mapBook(row) }.list()
                }
            }

        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val authorMap = fetchAuthorsForBooks(books.map { it.id })
        val categoryMap = fetchCategoriesForBooks(userId, books.map { it.id })
        val moodMap = fetchMoodsForBooks(userId, books.map { it.id })
        val enriched =
            books.map {
                it.copy(
                    tags = tagMap[it.id] ?: emptyList(),
                    authors = authorMap[it.id] ?: emptyList(),
                    categories = categoryMap[it.id] ?: emptyList(),
                    moods = moodMap[it.id] ?: emptyList(),
                )
            }

        val total =
            if (libraryId != null) {
                jdbi.withHandle<Int, Exception> { handle ->
                    val q =
                        handle
                            .createQuery(
                                """
                        SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                        LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                        LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                        WHERE b.library_id = ? AND l.user_id = ?${statusClause}${tagClause}${ratingGteClause}$formatClause
                        """,
                            ).bind(0, userId.toString())
                            .bind(1, userId.toString())
                            .bind(2, libraryId)
                            .bind(3, userId.toString())
                    var idx = 4
                    if (statusFilter != null) q.bind(idx++, statusFilter)
                    if (tagFilter != null) {
                        q.bind(idx++, userId.toString())
                        q.bind(idx++, tagFilter)
                    }
                    if (ratingGte != null) q.bind(idx++, ratingGte)
                    if (formatFilter != null) q.bind(idx++, formatFilter.uppercase())
                    q.mapTo(Int::class.java).first() ?: 0
                }
            } else {
                jdbi.withHandle<Int, Exception> { handle ->
                    val q =
                        handle
                            .createQuery(
                                """
                        SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                        LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                        LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                        WHERE l.user_id = ?${statusClause}${tagClause}${ratingGteClause}$formatClause
                        """,
                            ).bind(0, userId.toString())
                            .bind(1, userId.toString())
                            .bind(2, userId.toString())
                    var idx = 3
                    if (statusFilter != null) q.bind(idx++, statusFilter)
                    if (tagFilter != null) {
                        q.bind(idx++, userId.toString())
                        q.bind(idx++, tagFilter)
                    }
                    if (ratingGte != null) q.bind(idx++, ratingGte)
                    if (formatFilter != null) q.bind(idx++, formatFilter.uppercase())
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
        libId: String? = null,
        statusFilter: String? = null,
        minRating: Int? = null,
    ): BookListDto {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val offset = (safePage - 1) * safePageSize

        // Normalize query: produces hiragana/katakana/half-width variants for CJK support
        val likePatterns = SearchNormalizer.variants(query).map { "%${it.lowercase()}%" }
        // One OR-group per variant: (title LIKE ? OR author LIKE ? OR desc LIKE ?)
        val variantClauses =
            likePatterns.joinToString(" OR ") {
                "(LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.description) LIKE ?)"
            }
        // Params: 3 bindings per variant (title, author, description)
        val searchParams = likePatterns.flatMap { listOf(it, it, it) }

        // FTS: get matching book IDs to OR into the metadata search
        val ftsIds: Set<String> =
            if (ftsService?.isActive() == true && query.isNotBlank()) {
                ftsService.search(query).map { it.bookId }.toSet()
            } else {
                emptySet()
            }
        val ftsClause =
            if (ftsIds.isNotEmpty()) {
                " OR b.id IN (${ftsIds.joinToString(",") { "?" }})"
            } else {
                ""
            }
        val searchClause = "AND ($variantClauses$ftsClause)"

        val libClause = if (libId != null) " AND l.id = ?" else ""
        val statusClause = if (statusFilter != null) " AND bs.status = ?" else ""
        val ratingClause = if (minRating != null) " AND br.rating >= ?" else ""

        val books =
            jdbi.withHandle<List<BookDto>, Exception> { handle ->
                val q =
                    handle
                        .createQuery(
                            """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ?
                  $searchClause
                  $libClause$statusClause$ratingClause
                ORDER BY b.title LIMIT ? OFFSET ?
                """,
                        ).bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, userId.toString())
                var idx = 3
                for (p in searchParams) q.bind(idx++, p)
                for (id in ftsIds) q.bind(idx++, id)
                if (libId != null) q.bind(idx++, libId)
                if (statusFilter != null) q.bind(idx++, statusFilter)
                if (minRating != null) q.bind(idx++, minRating)
                q.bind(idx++, safePageSize)
                q.bind(idx, offset)
                q.map { row -> mapBook(row) }.list()
            }

        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val authorMap = fetchAuthorsForBooks(books.map { it.id })
        val categoryMap = fetchCategoriesForBooks(userId, books.map { it.id })
        val moodMap = fetchMoodsForBooks(userId, books.map { it.id })
        val enriched =
            books.map {
                it.copy(
                    tags = tagMap[it.id] ?: emptyList(),
                    authors = authorMap[it.id] ?: emptyList(),
                    categories = categoryMap[it.id] ?: emptyList(),
                    moods = moodMap[it.id] ?: emptyList(),
                )
            }

        // Attach FTS content snippets if available
        val ftsSnippets: Map<String, String> =
            if (ftsService?.isActive() == true && query.isNotBlank()) {
                ftsService.search(query, enriched.map { it.id }.toSet()).associate { it.bookId to it.snippet }
            } else {
                emptyMap()
            }
        val withSnippets =
            if (ftsSnippets.isEmpty()) {
                enriched
            } else {
                enriched.map { b ->
                    if (ftsSnippets.containsKey(b.id)) b.copy(contentSnippet = ftsSnippets[b.id]) else b
                }
            }

        val total =
            jdbi.withHandle<Int, Exception> { handle ->
                val q =
                    handle
                        .createQuery(
                            """
                SELECT COUNT(*) FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ?
                  $searchClause
                  $libClause$statusClause$ratingClause
                """,
                        ).bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, userId.toString())
                var idx = 3
                for (p in searchParams) q.bind(idx++, p)
                for (id in ftsIds) q.bind(idx++, id)
                if (libId != null) q.bind(idx++, libId)
                if (statusFilter != null) q.bind(idx++, statusFilter)
                if (minRating != null) q.bind(idx++, minRating)
                q.mapTo(java.lang.Integer::class.java).first()?.toInt() ?: 0
            }

        return BookListDto(withSnippets, total, safePage, safePageSize)
    }

    fun getBook(
        userId: UUID,
        bookId: UUID,
    ): BookDto? {
        val book =
            jdbi.withHandle<BookDto?, Exception> { handle ->
                handle
                    .createQuery(
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
                    ).bind("userId", userId.toString())
                    .bind("bookId", bookId.toString())
                    .map { row ->
                        val book = mapBook(row)
                        val currentPage =
                            try {
                                row.getColumn("rp_current_page", java.lang.Integer::class.java)?.toInt()
                            } catch (_: Exception) {
                                null
                            }
                        if (currentPage != null) {
                            book.copy(
                                progress =
                                    ReadingProgressDto(
                                        currentPage = currentPage,
                                        totalPages =
                                            try {
                                                row.getColumn("rp_total_pages", java.lang.Integer::class.java)?.toInt()
                                            } catch (_: Exception) {
                                                null
                                            },
                                        percentage =
                                            try {
                                                row.getColumn("rp_percentage", java.lang.Double::class.java)?.toDouble()
                                            } catch (_: Exception) {
                                                null
                                            },
                                        lastReadAt =
                                            try {
                                                row.getColumn("rp_last_read_at", String::class.java) ?: ""
                                            } catch (_: Exception) {
                                                ""
                                            },
                                    ),
                            )
                        } else {
                            book
                        }
                    }.firstOrNull()
            }
        val tags = if (book != null) fetchTagsForBooks(userId, listOf(book.id))[book.id] ?: emptyList() else emptyList()
        val authors = if (book != null) fetchAuthorsForBooks(listOf(book.id))[book.id] ?: emptyList() else emptyList()
        val categories = if (book != null) fetchCategoriesForBooks(userId, listOf(book.id))[book.id] ?: emptyList() else emptyList()
        val moods = if (book != null) fetchMoodsForBooks(userId, listOf(book.id))[book.id] ?: emptyList() else emptyList()
        val lockedFields =
            if (book != null) {
                metadataLockService
                    ?.getLockedFields(
                        UUID.fromString(book.id),
                    )?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        return book?.copy(tags = tags, authors = authors, categories = categories, moods = moods, lockedFields = lockedFields)
    }

    fun getRecentBooks(
        userId: UUID,
        limit: Int = 10,
    ): List<BookDto> =
        jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT b.* FROM reading_progress rp
                INNER JOIN books b ON rp.book_id = b.id
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE rp.user_id = ?
                ORDER BY rp.last_read_at DESC LIMIT ?
            """,
                ).bind(0, userId.toString())
                .bind(1, limit)
                .map { row -> mapBook(row) }
                .list()
        }

    fun createBook(
        userId: UUID,
        request: CreateBookRequest,
    ): Result<BookDto> {
        val libId = UUID.fromString(request.libraryId)

        jdbi.withHandle<String?, Exception> { handle ->
            handle
                .createQuery("SELECT id FROM libraries WHERE user_id = ? AND id = ?")
                .bind(0, userId.toString())
                .bind(1, libId.toString())
                .mapTo(String::class.java)
                .firstOrNull()
        } ?: return Result.failure(IllegalArgumentException("Library not found"))

        val now = Instant.now()
        val bookId = UUID.randomUUID()

        val format = BookFormat.fromString(request.bookFormat)
        val isPhysical = format == BookFormat.PHYSICAL

        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO books (id, library_id, title, author, description,
                    file_path, file_size, book_format, added_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ).bind(0, bookId.toString())
                .bind(1, libId.toString())
                .bind(2, request.title)
                .bind(3, request.author)
                .bind(4, request.description)
                .bind(5, if (isPhysical) null else "")
                .bind(6, if (isPhysical) null else 0L)
                .bind(7, format.name)
                .bind(8, now.toString())
                .bind(9, now.toString())
                .execute()
        }

        logger.info("Book created: ${request.title} (format=${format.name})")

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
                bookFormat = format.name,
            ),
        )
    }

    /** Creates a book record for a file imported from the BookDrop folder. Library ownership is pre-verified by the caller. */
    fun createBookFromDrop(
        userId: UUID,
        bookId: UUID,
        title: String,
        libraryId: String,
        filePath: String,
        fileSize: Long,
    ) {
        val now = Instant.now()
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """INSERT INTO books (id, library_id, title, file_path, file_size, added_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                ).bind(0, bookId.toString())
                .bind(1, libraryId)
                .bind(2, title)
                .bind(3, filePath)
                .bind(4, fileSize)
                .bind(5, now.toString())
                .bind(6, now.toString())
                .execute()
        }
        logger.info("BookDrop import: $title ($bookId)")
    }

    fun updateBook(
        userId: UUID,
        bookId: UUID,
        request: UpdateBookRequest,
    ): BookDto? {
        val book = getBook(userId, bookId) ?: return null

        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """UPDATE books SET title = ?, author = ?, description = ?,
                   series = ?, series_index = ?,
                   isbn = ?, publisher = ?, published_date = ?, page_count = ?,
                   subtitle = ?, language = ?, content_rating = ?, age_rating = ?,
                   goodreads_id = ?, hardcover_id = ?, comicvine_id = ?,
                   openlibrary_id = ?, google_books_id = ?, amazon_id = ?, audible_id = ?,
                   updated_at = ? WHERE id = ?""",
                ).bind(0, request.title)
                .bind(1, request.author)
                .bind(2, request.description)
                .bind(3, request.series)
                .bind(4, request.seriesIndex)
                .bind(5, request.isbn)
                .bind(6, request.publisher)
                .bind(7, request.publishedDate)
                .bind(8, request.pageCount)
                .bind(9, request.subtitle)
                .bind(10, request.language)
                .bind(11, request.contentRating)
                .bind(12, request.ageRating)
                .bind(13, request.goodreadsId)
                .bind(14, request.hardcoverId)
                .bind(15, request.comicvineId)
                .bind(16, request.openlibraryId)
                .bind(17, request.googleBooksId)
                .bind(18, request.amazonId)
                .bind(19, request.audibleId)
                .bind(20, Instant.now().toString())
                .bind(21, bookId.toString())
                .execute()
        }

        logger.info("Book updated: ${request.title}")
        return book.copy(
            title = request.title,
            author = request.author,
            description = request.description,
            series = request.series,
            seriesIndex = request.seriesIndex,
            isbn = request.isbn,
            publisher = request.publisher,
            publishedDate = request.publishedDate,
            pageCount = request.pageCount,
            subtitle = request.subtitle,
            language = request.language,
            contentRating = request.contentRating,
            ageRating = request.ageRating,
            goodreadsId = request.goodreadsId,
            hardcoverId = request.hardcoverId,
            comicvineId = request.comicvineId,
            openlibraryId = request.openlibraryId,
            googleBooksId = request.googleBooksId,
            amazonId = request.amazonId,
            audibleId = request.audibleId,
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
            handle
                .createUpdate(
                    """UPDATE books SET title = ?, author = ?, description = ?,
                   isbn = ?, publisher = ?, published_date = ?, updated_at = ?
                   WHERE id = ?""",
                ).bind(0, newTitle)
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

    fun updateSeries(
        userId: UUID,
        bookId: UUID,
        series: String,
        seriesIndex: Float?,
    ) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET series = ?, series_index = ?, updated_at = ? WHERE id = ?")
                .bind(0, series)
                .bind(1, seriesIndex)
                .bind(2, Instant.now().toString())
                .bind(3, bookId.toString())
                .execute()
        }
    }

    /** Converts a year-only string like "1984" to "1984-01-01" for DATE columns. */
    private fun normalizeDate(date: String?): String? = if (date != null && date.matches(Regex("\\d{4}"))) "$date-01-01" else date

    /**
     * Moves a book to a different library, both of which must belong to [userId].
     * Returns the updated book, or null if the book or target library is not found.
     */
    fun moveBook(
        userId: UUID,
        bookId: UUID,
        targetLibraryId: UUID,
    ): BookDto? {
        val book = getBook(userId, bookId) ?: return null
        // Verify the target library belongs to this user
        val targetExists =
            jdbi.withHandle<Boolean, Exception> { handle ->
                handle
                    .createQuery("SELECT COUNT(*) FROM libraries WHERE id = ? AND user_id = ?")
                    .bind(0, targetLibraryId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.java)
                    .first() > 0
            }
        if (!targetExists) return null
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE books SET library_id = ?, updated_at = ? WHERE id = ?")
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
    ): String? =
        jdbi.withHandle<String?, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT b.file_path FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE b.id = ? AND l.user_id = ?
                """,
                ).bind(0, bookId.toString())
                .bind(1, userId.toString())
                .mapTo(String::class.java)
                .firstOrNull()
        }

    fun updateFileInfo(
        userId: UUID,
        bookId: UUID,
        filePath: String,
        fileSize: Long,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        """
                UPDATE books SET file_path = ?, file_size = ?, updated_at = ?
                WHERE id = ?
                AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)
                """,
                    ).bind(0, filePath)
                    .bind(1, fileSize)
                    .bind(
                        2,
                        java.time.Instant
                            .now()
                            .toString(),
                    ).bind(3, bookId.toString())
                    .bind(4, userId.toString())
                    .execute()
            }
        return updated > 0
    }

    fun updateCoverPath(
        userId: UUID,
        bookId: UUID,
        coverFilename: String,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        """
                UPDATE books SET cover_path = ?, updated_at = ?
                WHERE id = ?
                AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)
                """,
                    ).bind(0, coverFilename)
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
                handle
                    .createQuery("SELECT * FROM reading_progress WHERE user_id = ? AND book_id = ?")
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
                handle
                    .createUpdate(
                        "UPDATE reading_progress SET current_page = ?, percentage = ?, last_read_at = ? WHERE user_id = ? AND book_id = ?",
                    ).bind(0, request.currentPage)
                    .bind(1, percentage)
                    .bind(2, now.toString())
                    .bind(3, userId.toString())
                    .bind(4, bookId.toString())
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate(
                        """
                    INSERT INTO reading_progress
                        (id, user_id, book_id, current_page, total_pages, percentage, last_read_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    ).bind(0, UUID.randomUUID().toString())
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

        // Skip page-based analytics and session recording for multi-file audiobooks.
        // Their progress is packed as (trackIndex * 1_000_000 + offsetSeconds), not page numbers.
        val isAudiobook = hasBookFiles(userId, bookId)
        if (!isAudiobook) {
            val delta = maxOf(0, request.currentPage - previousPage)
            analyticsService?.recordProgress(userId, bookId, delta)
            if (delta > 0) {
                readingSessionService?.recordSession(userId, bookId, previousPage, request.currentPage, delta)
            }
        }

        logger.info("Progress updated for book ${book.title}: page ${request.currentPage}")
        return ReadingProgressDto(request.currentPage, totalPages.takeIf { it > 0 }, percentage, now.toString())
    }

    fun getRecentlyFinishedBooks(
        userId: UUID,
        limit: Int = 6,
    ): List<BookDto> =
        jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT b.* FROM book_status bs
                INNER JOIN books b ON bs.book_id = b.id
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE bs.user_id = ? AND bs.status = 'FINISHED'
                ORDER BY bs.updated_at DESC
                LIMIT ?
                """,
                ).bind(0, userId.toString())
                .bind(1, limit)
                .map { row -> mapBook(row) }
                .list()
        }

    fun getRecentlyAddedBooks(
        userId: UUID,
        limit: Int = 6,
    ): List<BookDto> =
        jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT b.* FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ?
                ORDER BY b.added_at DESC, b.title
                LIMIT ?
                """,
                ).bind(0, userId.toString())
                .bind(1, limit)
                .map { row -> mapBook(row) }
                .list()
        }

    /** Returns all distinct authors for the user, with book count, status counts, and a cover. */
    fun getAuthors(userId: UUID): List<AuthorDto> =
        jdbi.withHandle<List<AuthorDto>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT b.author,
                       COUNT(*) AS book_count,
                       MIN(b.cover_path) AS cover_path,
                       COUNT(CASE WHEN bs.status = 'READING'  THEN 1 END) AS reading_count,
                       COUNT(CASE WHEN bs.status = 'FINISHED' THEN 1 END) AS finished_count
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                WHERE l.user_id = ? AND b.author IS NOT NULL AND b.author <> ''
                GROUP BY b.author
                ORDER BY b.author ASC
                """,
                ).bind(0, userId.toString())
                .bind(1, userId.toString())
                .map { row ->
                    AuthorDto(
                        name = row.getColumn("author", String::class.java),
                        bookCount = row.getColumn("book_count", java.lang.Integer::class.java)?.toInt() ?: 0,
                        coverUrl = row.getColumn("cover_path", String::class.java)?.let { "/covers/$it" },
                        readingCount = row.getColumn("reading_count", java.lang.Integer::class.java)?.toInt() ?: 0,
                        finishedCount = row.getColumn("finished_count", java.lang.Integer::class.java)?.toInt() ?: 0,
                    )
                }.list()
        }

    /** Returns all books by a named author for the user, sorted by series then title. */
    fun getBooksByAuthor(
        userId: UUID,
        author: String,
    ): List<BookDto> {
        val books =
            jdbi.withHandle<List<BookDto>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ? AND b.author = ?
                ORDER BY b.series ASC NULLS LAST, b.series_index ASC NULLS LAST, b.title ASC
                """,
                    ).bind(0, userId.toString())
                    .bind(1, userId.toString())
                    .bind(2, userId.toString())
                    .bind(3, author)
                    .map { row -> mapBook(row) }
                    .list()
            }
        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val authorMap = fetchAuthorsForBooks(books.map { it.id })
        val categoryMap = fetchCategoriesForBooks(userId, books.map { it.id })
        val moodMap = fetchMoodsForBooks(userId, books.map { it.id })
        return books.map {
            it.copy(
                tags = tagMap[it.id] ?: emptyList(),
                authors = authorMap[it.id] ?: emptyList(),
                categories = categoryMap[it.id] ?: emptyList(),
                moods = moodMap[it.id] ?: emptyList(),
            )
        }
    }

    /** Returns all distinct series that belong to the user, with book count and a cover. */
    fun getSeries(userId: UUID): List<SeriesDto> =
        jdbi.withHandle<List<SeriesDto>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT b.series, COUNT(*) AS book_count, MIN(b.cover_path) AS cover_path
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ? AND b.series IS NOT NULL AND b.series <> ''
                GROUP BY b.series
                ORDER BY b.series ASC
                """,
                ).bind(0, userId.toString())
                .map { row ->
                    SeriesDto(
                        name = row.getColumn("series", String::class.java),
                        bookCount = row.getColumn("book_count", java.lang.Integer::class.java)?.toInt() ?: 0,
                        coverUrl = row.getColumn("cover_path", String::class.java)?.let { "/covers/$it" },
                    )
                }.list()
        }

    /** Returns all books in a named series for the user, sorted by series_index then title. */
    fun getBooksBySeries(
        userId: UUID,
        series: String,
    ): List<BookDto> {
        val books =
            jdbi.withHandle<List<BookDto>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ? AND b.series = ?
                ORDER BY b.series_index ASC NULLS LAST, b.title ASC
                """,
                    ).bind(0, userId.toString())
                    .bind(1, userId.toString())
                    .bind(2, userId.toString())
                    .bind(3, series)
                    .map { row -> mapBook(row) }
                    .list()
            }
        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val authorMap = fetchAuthorsForBooks(books.map { it.id })
        val categoryMap = fetchCategoriesForBooks(userId, books.map { it.id })
        val moodMap = fetchMoodsForBooks(userId, books.map { it.id })
        return books.map {
            it.copy(
                tags = tagMap[it.id] ?: emptyList(),
                authors = authorMap[it.id] ?: emptyList(),
                categories = categoryMap[it.id] ?: emptyList(),
                moods = moodMap[it.id] ?: emptyList(),
            )
        }
    }

    private fun mapBook(row: RowView): BookDto {
        val pageCount: Int? =
            try {
                row.getColumn("page_count", java.lang.Integer::class.java)?.toInt()
            } catch (e: Exception) {
                null
            }
        val status: String? =
            try {
                row.getColumn("book_status_value", String::class.java)
            } catch (_: Exception) {
                null
            }
        val rating: Int? =
            try {
                row.getColumn("book_rating_value", java.lang.Integer::class.java)?.toInt()
            } catch (_: Exception) {
                null
            }
        return BookDto(
            id = row.getColumn("id", String::class.java),
            libraryId = row.getColumn("library_id", String::class.java) ?: "",
            title = row.getColumn("title", String::class.java),
            author = row.getColumn("author", String::class.java),
            description = row.getColumn("description", String::class.java),
            coverUrl = row.getColumn("cover_path", String::class.java)?.let { "/covers/$it" },
            pageCount = pageCount,
            fileSize = row.getColumn("file_size", java.lang.Long::class.java)?.toLong() ?: 0L,
            filePath =
                try {
                    row.getColumn("file_path", String::class.java)?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                },
            addedAt = row.getColumn("added_at", String::class.java),
            progress = null,
            status = status,
            rating = rating,
            isbn =
                try {
                    row.getColumn("isbn", String::class.java)
                } catch (_: Exception) {
                    null
                },
            publisher =
                try {
                    row.getColumn("publisher", String::class.java)
                } catch (_: Exception) {
                    null
                },
            publishedDate =
                try {
                    row.getColumn("published_date", String::class.java)
                } catch (_: Exception) {
                    null
                },
            series =
                try {
                    row.getColumn("series", String::class.java)
                } catch (_: Exception) {
                    null
                },
            seriesIndex =
                try {
                    row.getColumn("series_index", java.lang.Double::class.java)?.toDouble()
                } catch (_: Exception) {
                    null
                },
            readingDirection =
                try {
                    row.getColumn("reading_direction", String::class.java)
                } catch (_: Exception) {
                    null
                },
            subtitle =
                try {
                    row.getColumn("subtitle", String::class.java)
                } catch (_: Exception) {
                    null
                },
            language =
                try {
                    row.getColumn("language", String::class.java)
                } catch (_: Exception) {
                    null
                },
            contentRating =
                try {
                    row.getColumn("content_rating", String::class.java)
                } catch (_: Exception) {
                    null
                },
            ageRating =
                try {
                    row.getColumn("age_rating", String::class.java)
                } catch (_: Exception) {
                    null
                },
            goodreadsId =
                try {
                    row.getColumn("goodreads_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            hardcoverId =
                try {
                    row.getColumn("hardcover_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            comicvineId =
                try {
                    row.getColumn("comicvine_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            openlibraryId =
                try {
                    row.getColumn("openlibrary_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            googleBooksId =
                try {
                    row.getColumn("google_books_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            amazonId =
                try {
                    row.getColumn("amazon_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            audibleId =
                try {
                    row.getColumn("audible_id", String::class.java)
                } catch (_: Exception) {
                    null
                },
            bookFormat =
                try {
                    row.getColumn("book_format", String::class.java) ?: "EBOOK"
                } catch (_: Exception) {
                    "EBOOK"
                },
            communityRating =
                try {
                    row.getColumn("community_rating", java.lang.Double::class.java)?.toDouble()
                } catch (
                    _: Exception,
                ) {
                    null
                },
            communityRatingCount =
                try {
                    row.getColumn("community_rating_count", java.lang.Integer::class.java)?.toInt()
                } catch (
                    _: Exception,
                ) {
                    null
                },
            communityRatingSource =
                try {
                    row.getColumn("community_rating_source", String::class.java)
                } catch (_: Exception) {
                    null
                },
        )
    }

    private fun fetchMoodsForBooks(
        userId: UUID,
        bookIds: List<String>,
    ): Map<String, List<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return jdbi.withHandle<Map<String, List<String>>, Exception> { handle ->
            val placeholders = bookIds.joinToString(",") { "?" }
            val q =
                handle.createQuery(
                    "SELECT book_id, mood FROM book_moods WHERE user_id = ? AND book_id IN ($placeholders) ORDER BY mood",
                )
            q.bind(0, userId.toString())
            bookIds.forEachIndexed { i, id -> q.bind(i + 1, id) }
            q
                .map { row ->
                    Pair(row.getColumn("book_id", String::class.java), row.getColumn("mood", String::class.java))
                }.list()
                .groupBy({ it.first }, { it.second })
        }
    }

    fun setMoods(
        userId: UUID,
        bookId: UUID,
        moods: List<String>,
    ): Boolean {
        val exists =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery(
                        "SELECT COUNT(*) FROM books b JOIN libraries l ON b.library_id = l.id WHERE b.id = ? AND l.user_id = ?",
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.java)
                    .firstOrNull()!! > 0
            }
        if (!exists) return false
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM book_moods WHERE user_id = ? AND book_id = ?")
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .execute()
            val cleanMoods = moods.filter { it.isNotBlank() }
            if (cleanMoods.isNotEmpty()) {
                val batch =
                    h.prepareBatch(
                        "INSERT INTO book_moods (id, user_id, book_id, mood) VALUES (?, ?, ?, ?)",
                    )
                cleanMoods.forEach { mood ->
                    batch
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, bookId.toString())
                        .bind(3, mood)
                        .add()
                }
                batch.execute()
            }
        }
        return true
    }

    fun updateExternalIds(
        userId: UUID,
        bookId: UUID,
        goodreadsId: String?,
        hardcoverId: String?,
        comicvineId: String?,
        openlibraryId: String?,
        googleBooksId: String?,
        amazonId: String?,
        audibleId: String?,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate(
                        """UPDATE books SET goodreads_id=?, hardcover_id=?, comicvine_id=?,
                   openlibrary_id=?, google_books_id=?, amazon_id=?, audible_id=?
                   WHERE id=? AND library_id IN (SELECT id FROM libraries WHERE user_id=?)""",
                    ).bind(0, goodreadsId)
                    .bind(1, hardcoverId)
                    .bind(2, comicvineId)
                    .bind(3, openlibraryId)
                    .bind(4, googleBooksId)
                    .bind(5, amazonId)
                    .bind(6, audibleId)
                    .bind(7, bookId.toString())
                    .bind(8, userId.toString())
                    .execute()
            }
        return updated > 0
    }

    fun updateExtendedMetadata(
        userId: UUID,
        bookId: UUID,
        subtitle: String?,
        language: String?,
        contentRating: String?,
        ageRating: String?,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate(
                        """UPDATE books SET subtitle = ?, language = ?, content_rating = ?, age_rating = ?
                   WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)""",
                    ).bind(0, subtitle)
                    .bind(1, language)
                    .bind(2, contentRating)
                    .bind(3, ageRating)
                    .bind(4, bookId.toString())
                    .bind(5, userId.toString())
                    .execute()
            }
        return updated > 0
    }

    private fun fetchCategoriesForBooks(
        userId: UUID,
        bookIds: List<String>,
    ): Map<String, List<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return jdbi.withHandle<Map<String, List<String>>, Exception> { handle ->
            val placeholders = bookIds.joinToString(",") { "?" }
            val q =
                handle.createQuery(
                    "SELECT book_id, category FROM book_categories WHERE user_id = ? AND book_id IN ($placeholders) ORDER BY category",
                )
            q.bind(0, userId.toString())
            bookIds.forEachIndexed { i, id -> q.bind(i + 1, id) }
            q
                .map { row ->
                    Pair(row.getColumn("book_id", String::class.java), row.getColumn("category", String::class.java))
                }.list()
                .groupBy({ it.first }, { it.second })
        }
    }

    fun setCategories(
        userId: UUID,
        bookId: UUID,
        categories: List<String>,
    ): Boolean {
        val exists =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery(
                        "SELECT COUNT(*) FROM books b JOIN libraries l ON b.library_id = l.id WHERE b.id = ? AND l.user_id = ?",
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.java)
                    .firstOrNull()!! > 0
            }
        if (!exists) return false
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM book_categories WHERE user_id = ? AND book_id = ?")
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .execute()
            val cleanCategories = categories.filter { it.isNotBlank() }
            if (cleanCategories.isNotEmpty()) {
                val batch =
                    h.prepareBatch(
                        "INSERT INTO book_categories (id, user_id, book_id, category) VALUES (?, ?, ?, ?)",
                    )
                cleanCategories.forEach { cat ->
                    batch
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, bookId.toString())
                        .bind(3, cat)
                        .add()
                }
                batch.execute()
            }
        }
        return true
    }

    private fun fetchAuthorsForBooks(bookIds: List<String>): Map<String, List<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return jdbi.withHandle<Map<String, List<String>>, Exception> { handle ->
            val placeholders = bookIds.joinToString(",") { "?" }
            val q =
                handle.createQuery(
                    "SELECT book_id, author_name FROM book_authors WHERE book_id IN ($placeholders) ORDER BY author_order, author_name",
                )
            bookIds.forEachIndexed { i, id -> q.bind(i, id) }
            q
                .map { row ->
                    Pair(
                        row.getColumn("book_id", String::class.java),
                        row.getColumn("author_name", String::class.java),
                    )
                }.list()
                .groupBy({ it.first }, { it.second })
        }
    }

    fun setAuthors(
        userId: UUID,
        bookId: UUID,
        authors: List<String>,
    ): Boolean {
        // Verify the book belongs to this user
        val exists =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery(
                        "SELECT COUNT(*) FROM books b JOIN libraries l ON b.library_id = l.id WHERE b.id = ? AND l.user_id = ?",
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.java)
                    .firstOrNull()!! > 0
            }
        if (!exists) return false

        jdbi.useHandle<Exception> { h ->
            h.createUpdate("DELETE FROM book_authors WHERE book_id = ?").bind(0, bookId.toString()).execute()
            if (authors.isNotEmpty()) {
                val batch =
                    h.prepareBatch(
                        "INSERT INTO book_authors (id, book_id, author_name, author_order) VALUES (?, ?, ?, ?)",
                    )
                authors.forEachIndexed { idx, name ->
                    batch
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, bookId.toString())
                        .bind(2, name)
                        .bind(3, idx)
                        .add()
                }
                batch.execute()
            }
            // Also sync the legacy author column with the primary author
            val primary = authors.firstOrNull()
            h
                .createUpdate("UPDATE books SET author = ? WHERE id = ?")
                .bind(0, primary)
                .bind(1, bookId.toString())
                .execute()
        }
        return true
    }

    private fun fetchTagsForBooks(
        userId: UUID,
        bookIds: List<String>,
    ): Map<String, List<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return jdbi.withHandle<Map<String, List<String>>, Exception> { handle ->
            val placeholders = bookIds.joinToString(",") { "?" }
            val q =
                handle.createQuery(
                    "SELECT book_id, tag FROM book_tags WHERE user_id = ? AND book_id IN ($placeholders) ORDER BY tag",
                )
            q.bind(0, userId.toString())
            bookIds.forEachIndexed { i, id -> q.bind(i + 1, id) }
            q
                .map { row ->
                    val bookId = row.getColumn("book_id", String::class.java)
                    val tag = row.getColumn("tag", String::class.java)
                    Pair(bookId, tag)
                }.list()
                .groupBy({ it.first }, { it.second })
        }
    }

    /** Returns books for a user matching a shelf rule. Used by MagicShelfService. */
    fun getBooksForShelf(
        userId: UUID,
        statusFilter: String? = null,
        tagFilter: String? = null,
        ratingGte: Int? = null,
        limit: Int = 200,
    ): List<BookDto> {
        val statusClause = if (statusFilter != null) " AND bs.status = ?" else ""
        val tagClause =
            if (tagFilter != null) {
                " AND EXISTS (SELECT 1 FROM book_tags bt WHERE bt.book_id = b.id AND bt.user_id = ? AND bt.tag = ?)"
            } else {
                ""
            }
        val ratingGteClause = if (ratingGte != null) " AND br.rating >= ?" else ""

        val books =
            jdbi.withHandle<List<BookDto>, Exception> { handle ->
                val q =
                    handle.createQuery(
                        """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ?${statusClause}${tagClause}$ratingGteClause
                ORDER BY b.title
                LIMIT ?
                """,
                    )
                q.bind(0, userId.toString())
                q.bind(1, userId.toString())
                q.bind(2, userId.toString())
                var idx = 3
                if (statusFilter != null) q.bind(idx++, statusFilter)
                if (tagFilter != null) {
                    q.bind(idx++, userId.toString())
                    q.bind(idx++, tagFilter)
                }
                if (ratingGte != null) q.bind(idx++, ratingGte)
                q.bind(idx++, limit)
                q.map { row -> mapBook(row) }.list()
            }
        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val authorMap = fetchAuthorsForBooks(books.map { it.id })
        val categoryMap = fetchCategoriesForBooks(userId, books.map { it.id })
        val moodMap = fetchMoodsForBooks(userId, books.map { it.id })
        return books.map {
            it.copy(
                tags = tagMap[it.id] ?: emptyList(),
                authors = authorMap[it.id] ?: emptyList(),
                categories = categoryMap[it.id] ?: emptyList(),
                moods = moodMap[it.id] ?: emptyList(),
            )
        }
    }

    fun setTags(
        userId: UUID,
        bookId: UUID,
        tags: List<String>,
    ) {
        val cleanTags =
            tags
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && it.length <= 50 }
                .distinct()
                .take(10)
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM book_tags WHERE user_id = ? AND book_id = ?")
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .execute()
            if (cleanTags.isNotEmpty()) {
                val batch =
                    handle.prepareBatch(
                        "INSERT INTO book_tags (id, user_id, book_id, tag) VALUES (?, ?, ?, ?)",
                    )
                for (tag in cleanTags) {
                    batch
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, bookId.toString())
                        .bind(3, tag)
                        .add()
                }
                batch.execute()
            }
        }
        logger.info("Tags set for book $bookId: $cleanTags")
    }

    fun getUserTags(userId: UUID): List<String> =
        jdbi.withHandle<List<String>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT DISTINCT tag FROM book_tags WHERE user_id = ? ORDER BY tag",
                ).bind(0, userId.toString())
                .mapTo(String::class.java)
                .list()
        }

    /** Returns all distinct tags for the user with their book counts, sorted alphabetically. */
    fun getTagsWithCounts(userId: UUID): List<TagDto> =
        jdbi.withHandle<List<TagDto>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT bt.tag, COUNT(*) AS book_count
                FROM book_tags bt
                JOIN books b ON bt.book_id = b.id
                JOIN libraries l ON b.library_id = l.id
                WHERE bt.user_id = ? AND l.user_id = ?
                GROUP BY bt.tag
                ORDER BY bt.tag ASC
                """,
                ).bind(0, userId.toString())
                .bind(1, userId.toString())
                .map { row ->
                    TagDto(
                        name = row.getColumn("tag", String::class.java),
                        bookCount = row.getColumn("book_count", java.lang.Integer::class.java)?.toInt() ?: 0,
                    )
                }.list()
        }

    /** Returns all books tagged with the given tag for the user, sorted by title. */
    fun getBooksByTag(
        userId: UUID,
        tag: String,
    ): List<BookDto> {
        val books =
            jdbi.withHandle<List<BookDto>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.*, bs.status AS book_status_value, br.rating AS book_rating_value
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                JOIN book_tags bt ON bt.book_id = b.id AND bt.user_id = ?
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                LEFT JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ?
                WHERE l.user_id = ? AND bt.tag = ?
                ORDER BY b.title ASC
                """,
                    ).bind(0, userId.toString())
                    .bind(1, userId.toString())
                    .bind(2, userId.toString())
                    .bind(3, userId.toString())
                    .bind(4, tag)
                    .map { row -> mapBook(row) }
                    .list()
            }
        val tagMap = fetchTagsForBooks(userId, books.map { it.id })
        val authorMap = fetchAuthorsForBooks(books.map { it.id })
        val categoryMap = fetchCategoriesForBooks(userId, books.map { it.id })
        val moodMap = fetchMoodsForBooks(userId, books.map { it.id })
        return books.map {
            it.copy(
                tags = tagMap[it.id] ?: emptyList(),
                authors = authorMap[it.id] ?: emptyList(),
                categories = categoryMap[it.id] ?: emptyList(),
                moods = moodMap[it.id] ?: emptyList(),
            )
        }
    }

    fun setStatus(
        userId: UUID,
        bookId: UUID,
        status: ReadStatus?,
    ) {
        val now = Instant.now().toString()
        val existing =
            jdbi.withHandle<String?, Exception> { handle ->
                handle
                    .createQuery("SELECT id FROM book_status WHERE user_id = ? AND book_id = ?")
                    .bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .mapTo(String::class.java)
                    .firstOrNull()
            }
        if (status == null) {
            if (existing != null) {
                jdbi.useHandle<Exception> { handle ->
                    handle
                        .createUpdate("DELETE FROM book_status WHERE user_id = ? AND book_id = ?")
                        .bind(0, userId.toString())
                        .bind(1, bookId.toString())
                        .execute()
                }
            }
            return
        }
        if (existing != null) {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate("UPDATE book_status SET status = ?, updated_at = ? WHERE user_id = ? AND book_id = ?")
                    .bind(0, status.name)
                    .bind(1, now)
                    .bind(2, userId.toString())
                    .bind(3, bookId.toString())
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate("INSERT INTO book_status (id, user_id, book_id, status, updated_at) VALUES (?, ?, ?, ?, ?)")
                    .bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, status.name)
                    .bind(4, now)
                    .execute()
            }
        }
        logger.info("Book status set to ${status.name} for book $bookId")
    }

    fun countByStatus(
        userId: UUID,
        status: ReadStatus,
    ): Int =
        jdbi.withHandle<Int, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT COUNT(*) FROM book_status WHERE user_id = ? AND status = ?",
                ).bind(0, userId.toString())
                .bind(1, status.name)
                .mapTo(java.lang.Integer::class.java)
                .first()
                ?.toInt() ?: 0
        }

    fun countFinishedThisYear(
        userId: UUID,
        year: Int,
    ): Int =
        jdbi.withHandle<Int, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT COUNT(*) FROM book_status
                WHERE user_id = ? AND status = 'FINISHED'
                  AND SUBSTRING(CAST(updated_at AS VARCHAR), 1, 4) = ?
                """,
                ).bind(0, userId.toString())
                .bind(1, year.toString())
                .mapTo(java.lang.Integer::class.java)
                .first()
                ?.toInt() ?: 0
        }

    fun setRating(
        userId: UUID,
        bookId: UUID,
        rating: Int?,
    ) {
        val existing =
            jdbi.withHandle<String?, Exception> { handle ->
                handle
                    .createQuery("SELECT id FROM book_ratings WHERE user_id = ? AND book_id = ?")
                    .bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .mapTo(String::class.java)
                    .firstOrNull()
            }
        if (rating == null || rating !in 1..5) {
            if (existing != null) {
                jdbi.useHandle<Exception> { handle ->
                    handle
                        .createUpdate("DELETE FROM book_ratings WHERE user_id = ? AND book_id = ?")
                        .bind(0, userId.toString())
                        .bind(1, bookId.toString())
                        .execute()
                }
            }
            return
        }
        val now = Instant.now().toString()
        if (existing != null) {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate("UPDATE book_ratings SET rating = ?, updated_at = ? WHERE user_id = ? AND book_id = ?")
                    .bind(0, rating)
                    .bind(1, now)
                    .bind(2, userId.toString())
                    .bind(3, bookId.toString())
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate("INSERT INTO book_ratings (id, user_id, book_id, rating, updated_at) VALUES (?, ?, ?, ?, ?)")
                    .bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, rating)
                    .bind(4, now)
                    .execute()
            }
        }
        logger.info("Book rating set to $rating for book $bookId")
    }

    // ── Bulk operations ───────────────────────────────────────────────────────

    fun bulkMove(
        userId: UUID,
        bookIds: List<UUID>,
        targetLibraryId: UUID,
    ): Int {
        val targetExists =
            jdbi.withHandle<Boolean, Exception> { handle ->
                handle
                    .createQuery("SELECT COUNT(*) FROM libraries WHERE id = ? AND user_id = ?")
                    .bind(0, targetLibraryId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.java)
                    .first() > 0
            }
        if (!targetExists) return 0
        return jdbi.withHandle<Int, Exception> { handle ->
            val now =
                java.time.Instant
                    .now()
                    .toString()
            bookIds.count { bookId ->
                handle
                    .createUpdate(
                        """UPDATE books SET library_id = ?, updated_at = ?
                       WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)""",
                    ).bind(0, targetLibraryId.toString())
                    .bind(1, now)
                    .bind(2, bookId.toString())
                    .bind(3, userId.toString())
                    .execute() > 0
            }
        }
    }

    fun bulkDelete(
        userId: UUID,
        bookIds: List<UUID>,
    ): Int =
        jdbi.withHandle<Int, Exception> { handle ->
            bookIds.count { bookId ->
                handle
                    .createUpdate(
                        "DELETE FROM books WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)",
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .execute() > 0
            }
        }

    fun bulkTag(
        userId: UUID,
        bookIds: List<UUID>,
        tags: List<String>,
    ): Int =
        jdbi.withHandle<Int, Exception> { handle ->
            bookIds.count { bookId ->
                val owned =
                    handle
                        .createQuery(
                            """SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                       WHERE b.id = ? AND l.user_id = ?""",
                        ).bind(0, bookId.toString())
                        .bind(1, userId.toString())
                        .mapTo(Int::class.java)
                        .first() > 0
                if (!owned) return@count false
                handle
                    .createUpdate("DELETE FROM book_tags WHERE book_id = ?")
                    .bind(0, bookId.toString())
                    .execute()
                if (tags.isNotEmpty()) {
                    val batch =
                        handle.prepareBatch(
                            "INSERT INTO book_tags (id, user_id, book_id, tag) VALUES (?, ?, ?, ?)",
                        )
                    tags.forEach { tag ->
                        batch
                            .bind(0, UUID.randomUUID().toString())
                            .bind(1, userId.toString())
                            .bind(2, bookId.toString())
                            .bind(3, tag)
                            .add()
                    }
                    batch.execute()
                }
                true
            }
        }

    fun bulkStatus(
        userId: UUID,
        bookIds: List<UUID>,
        status: String,
    ): Int {
        val readStatus =
            if (status.isBlank() || status == "NONE") {
                null
            } else {
                runCatching { ReadStatus.valueOf(status) }.getOrNull()
            }
        val now =
            java.time.Instant
                .now()
                .toString()
        return jdbi.withHandle<Int, Exception> { handle ->
            bookIds.count { bookId ->
                // Verify ownership
                val owned =
                    handle
                        .createQuery(
                            "SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE b.id = ? AND l.user_id = ?",
                        ).bind(0, bookId.toString())
                        .bind(1, userId.toString())
                        .mapTo(Int::class.java)
                        .first() > 0
                if (!owned) return@count false
                val existing =
                    handle
                        .createQuery(
                            "SELECT id FROM book_status WHERE user_id = ? AND book_id = ?",
                        ).bind(0, userId.toString())
                        .bind(1, bookId.toString())
                        .mapTo(String::class.java)
                        .firstOrNull()
                if (readStatus == null) {
                    if (existing != null) {
                        handle
                            .createUpdate("DELETE FROM book_status WHERE user_id = ? AND book_id = ?")
                            .bind(0, userId.toString())
                            .bind(1, bookId.toString())
                            .execute()
                    }
                } else if (existing != null) {
                    handle
                        .createUpdate("UPDATE book_status SET status = ?, updated_at = ? WHERE user_id = ? AND book_id = ?")
                        .bind(0, readStatus.name)
                        .bind(1, now)
                        .bind(2, userId.toString())
                        .bind(3, bookId.toString())
                        .execute()
                } else {
                    handle
                        .createUpdate("INSERT INTO book_status (id, user_id, book_id, status, updated_at) VALUES (?, ?, ?, ?, ?)")
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, userId.toString())
                        .bind(2, bookId.toString())
                        .bind(3, readStatus.name)
                        .bind(4, now)
                        .execute()
                }
                true
            }
        }
    }

    fun hasBookFiles(
        userId: UUID,
        bookId: UUID,
    ): Boolean =
        jdbi.withHandle<Boolean, Exception> { handle ->
            handle
                .createQuery(
                    """SELECT COUNT(*) FROM book_files bf
               INNER JOIN books b ON b.id = bf.book_id
               INNER JOIN libraries l ON l.id = b.library_id
               WHERE bf.book_id = ? AND l.user_id = ?""",
                ).bind(0, bookId.toString())
                .bind(1, userId.toString())
                .mapTo(Int::class.java)
                .one() > 0
        }

    fun getBookFiles(
        userId: UUID,
        bookId: UUID,
    ): List<BookFileDto> =
        jdbi.withHandle<List<BookFileDto>, Exception> { handle ->
            handle
                .createQuery(
                    """SELECT bf.id, bf.track_index, bf.title, bf.duration_sec, bf.file_size, bf.file_path
               FROM book_files bf
               INNER JOIN books b ON b.id = bf.book_id
               INNER JOIN libraries l ON l.id = b.library_id
               WHERE bf.book_id = ? AND l.user_id = ?
               ORDER BY bf.track_index""",
                ).bind(0, bookId.toString())
                .bind(1, userId.toString())
                .map { row ->
                    BookFileDto(
                        id = row.getColumn("id", String::class.java),
                        trackIndex = row.getColumn("track_index", java.lang.Integer::class.java)?.toInt() ?: 0,
                        title = row.getColumn("title", String::class.java),
                        durationSec = row.getColumn("duration_sec", java.lang.Integer::class.java)?.toInt(),
                        fileSize = row.getColumn("file_size", java.lang.Long::class.java)?.toLong() ?: 0L,
                        filePath = row.getColumn("file_path", String::class.java),
                    )
                }.list()
        }

    /** Batch-load book files for multiple books in a single query. Returns a map of bookId -> files. */
    fun getBookFilesForBooks(
        userId: UUID,
        bookIds: List<UUID>,
    ): Map<String, List<BookFileDto>> {
        if (bookIds.isEmpty()) return emptyMap()
        val placeholders = bookIds.joinToString(",") { "?" }
        return jdbi.withHandle<Map<String, List<BookFileDto>>, Exception> { handle ->
            val query =
                handle.createQuery(
                    """SELECT bf.id, bf.book_id, bf.track_index, bf.title, bf.duration_sec, bf.file_size, bf.file_path
                       FROM book_files bf
                       INNER JOIN books b ON b.id = bf.book_id
                       INNER JOIN libraries l ON l.id = b.library_id
                       WHERE bf.book_id IN ($placeholders) AND l.user_id = ?
                       ORDER BY bf.book_id, bf.track_index""",
                )
            bookIds.forEachIndexed { i, id -> query.bind(i, id.toString()) }
            query.bind(bookIds.size, userId.toString())
            query
                .map { row ->
                    row.getColumn("book_id", String::class.java) to
                        BookFileDto(
                            id = row.getColumn("id", String::class.java),
                            trackIndex = row.getColumn("track_index", java.lang.Integer::class.java)?.toInt() ?: 0,
                            title = row.getColumn("title", String::class.java),
                            durationSec = row.getColumn("duration_sec", java.lang.Integer::class.java)?.toInt(),
                            fileSize = row.getColumn("file_size", java.lang.Long::class.java)?.toLong() ?: 0L,
                            filePath = row.getColumn("file_path", String::class.java),
                        )
                }.list()
                .groupBy({ it.first }, { it.second })
        }
    }

    fun getBookFilePath(
        userId: UUID,
        bookId: UUID,
        trackIndex: Int,
    ): String? =
        jdbi.withHandle<String?, Exception> { handle ->
            handle
                .createQuery(
                    """SELECT bf.file_path FROM book_files bf
               INNER JOIN books b ON b.id = bf.book_id
               INNER JOIN libraries l ON l.id = b.library_id
               WHERE bf.book_id = ? AND bf.track_index = ? AND l.user_id = ?""",
                ).bind(0, bookId.toString())
                .bind(1, trackIndex)
                .bind(2, userId.toString())
                .mapTo(String::class.java)
                .firstOrNull()
        }

    fun addBookFile(
        userId: UUID,
        bookId: UUID,
        trackIndex: Int,
        title: String?,
        filePath: String,
        fileSize: Long,
        durationSec: Int? = null,
    ): Boolean {
        val fileId = UUID.randomUUID()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """INSERT INTO book_files (id, book_id, track_index, title, file_path, file_size, duration_sec, added_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                ).bind(0, fileId.toString())
                .bind(1, bookId.toString())
                .bind(2, trackIndex)
                .bind(3, title)
                .bind(4, filePath)
                .bind(5, fileSize)
                .bind(6, durationSec)
                .bind(7, now)
                .execute()
        }
        updateBookFileAggregateSize(bookId)
        return true
    }

    fun deleteBookFile(
        userId: UUID,
        bookId: UUID,
        trackIndex: Int,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        """DELETE FROM book_files WHERE book_id = ? AND track_index = ?
               AND book_id IN (SELECT b.id FROM books b
                               INNER JOIN libraries l ON l.id = b.library_id
                               WHERE l.user_id = ?)""",
                    ).bind(0, bookId.toString())
                    .bind(1, trackIndex)
                    .bind(2, userId.toString())
                    .execute()
            }
        if (deleted > 0) updateBookFileAggregateSize(bookId)
        return deleted > 0
    }

    fun updateBookFileTitle(
        userId: UUID,
        bookId: UUID,
        trackIndex: Int,
        title: String?,
    ) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """UPDATE book_files SET title = ? WHERE book_id = ? AND track_index = ?
               AND book_id IN (SELECT b.id FROM books b
                               INNER JOIN libraries l ON l.id = b.library_id
                               WHERE l.user_id = ?)""",
                ).bind(0, title)
                .bind(1, bookId.toString())
                .bind(2, trackIndex)
                .bind(3, userId.toString())
                .execute()
        }
    }

    /** Sets the reading direction for a comic/manga book (ltr or rtl). */
    fun setReadingDirection(
        userId: UUID,
        bookId: UUID,
        direction: String,
    ): Boolean {
        val normalized = if (direction.lowercase() == "rtl") "rtl" else "ltr"
        val rows =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        """UPDATE books SET reading_direction = ? WHERE id = ?
                   AND id IN (SELECT b.id FROM books b
                              INNER JOIN libraries l ON l.id = b.library_id
                              WHERE l.user_id = ?)""",
                    ).bind(0, normalized)
                    .bind(1, bookId.toString())
                    .bind(2, userId.toString())
                    .execute()
            }
        return rows > 0
    }

    /**
     * Merges [sourceId] into [targetId], moving user-specific data (reading progress, sessions,
     * bookmarks, annotations, journal, reviews, notebooks, listen sessions, tags, categories)
     * from source to target, then deletes the source book.
     *
     * If the target has no file but the source does, the source file path/size is copied to the target.
     *
     * Returns false if either book does not belong to the user.
     */
    fun mergeBooks(
        userId: UUID,
        targetId: UUID,
        sourceId: UUID,
    ): Boolean {
        val target = getBook(userId, targetId) ?: return false
        val source = getBook(userId, sourceId) ?: return false
        if (targetId == sourceId) return false

        jdbi.useHandle<Exception> { h ->
            // Move rows with a simple PK — no conflict possible
            for (table in listOf(
                "reading_sessions",
                "bookmarks",
                "book_annotations",
                "book_journal_entries",
                "book_reviews",
                "book_notebooks",
                "listen_sessions",
                "metadata_proposals",
            )) {
                h
                    .createUpdate("UPDATE $table SET book_id = ? WHERE book_id = ?")
                    .bind(0, targetId.toString())
                    .bind(1, sourceId.toString())
                    .execute()
            }

            // Tags: move non-conflicting rows, drop the rest
            h
                .createUpdate(
                    """
                DELETE FROM book_tags
                WHERE book_id = ?
                  AND tag IN (SELECT tag FROM book_tags WHERE book_id = ?)
            """,
                ).bind(0, sourceId.toString())
                .bind(1, targetId.toString())
                .execute()
            h
                .createUpdate("UPDATE book_tags SET book_id = ? WHERE book_id = ?")
                .bind(0, targetId.toString())
                .bind(1, sourceId.toString())
                .execute()

            // Categories: same pattern
            h
                .createUpdate(
                    """
                DELETE FROM book_categories
                WHERE book_id = ?
                  AND category IN (SELECT category FROM book_categories WHERE book_id = ?)
            """,
                ).bind(0, sourceId.toString())
                .bind(1, targetId.toString())
                .execute()
            h
                .createUpdate("UPDATE book_categories SET book_id = ? WHERE book_id = ?")
                .bind(0, targetId.toString())
                .bind(1, sourceId.toString())
                .execute()

            // Reading progress: move if target has none for that user
            h
                .createUpdate(
                    """
                UPDATE reading_progress SET book_id = ?
                WHERE book_id = ?
                  AND user_id NOT IN (SELECT user_id FROM reading_progress WHERE book_id = ?)
            """,
                ).bind(0, targetId.toString())
                .bind(1, sourceId.toString())
                .bind(2, targetId.toString())
                .execute()
            h.createUpdate("DELETE FROM reading_progress WHERE book_id = ?").bind(0, sourceId.toString()).execute()

            // If target has no file but source does, adopt source file
            if (target.filePath == null && source.filePath != null) {
                h
                    .createUpdate("UPDATE books SET file_path = ?, file_size = ? WHERE id = ?")
                    .bind(0, source.filePath)
                    .bind(1, source.fileSize)
                    .bind(2, targetId.toString())
                    .execute()
            }

            // Book files (alternative formats): re-parent
            h
                .createUpdate("UPDATE book_files SET book_id = ? WHERE book_id = ?")
                .bind(0, targetId.toString())
                .bind(1, sourceId.toString())
                .execute()
        }

        // Delete source — CASCADE cleans remaining FK rows
        deleteBook(userId, sourceId)
        logger.info("Merged book '${source.title}' ($sourceId) into '${target.title}' ($targetId)")
        return true
    }

    fun updateBookFileAggregateSize(bookId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """UPDATE books SET file_size = (
               SELECT COALESCE(SUM(file_size), 0) FROM book_files WHERE book_id = ?
               ) WHERE id = ?""",
                ).bind(0, bookId.toString())
                .bind(1, bookId.toString())
                .execute()
        }
    }
}
