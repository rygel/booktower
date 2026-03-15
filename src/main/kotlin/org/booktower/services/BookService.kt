package org.booktower.services

import org.booktower.config.StorageConfig
import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.BookService")

class BookService(private val jdbi: Jdbi, private val storageConfig: StorageConfig) {
    fun getBooks(
        userId: UUID,
        libraryId: String?,
        page: Int = 1,
        pageSize: Int = 20,
    ): BookListDto {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 100)
        val offset = (safePage - 1) * safePageSize

        val books =
            if (libraryId != null) {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    handle.createQuery(
                        """
                        SELECT b.* FROM books b
                        INNER JOIN libraries l ON b.library_id = l.id
                        WHERE b.library_id = ? AND l.user_id = ?
                        ORDER BY b.title LIMIT ? OFFSET ?
                        """,
                    )
                        .bind(0, libraryId)
                        .bind(1, userId.toString())
                        .bind(2, safePageSize)
                        .bind(3, offset)
                        .map { row -> mapBook(row) }.list()
                }
            } else {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    handle.createQuery(
                        """
                    SELECT b.* FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ?
                    ORDER BY b.title LIMIT ? OFFSET ?
                """,
                    )
                        .bind(0, userId.toString())
                        .bind(1, safePageSize)
                        .bind(2, offset)
                        .map { row -> mapBook(row) }.list()
                }
            }

        val total =
            if (libraryId != null) {
                jdbi.withHandle<Int, Exception> { handle ->
                    handle.createQuery(
                        "SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE b.library_id = ? AND l.user_id = ?",
                    )
                        .bind(0, libraryId)
                        .bind(1, userId.toString())
                        .mapTo(Int::class.java).first() ?: 0
                }
            } else {
                jdbi.withHandle<Int, Exception> { handle ->
                    handle.createQuery("SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ?")
                        .bind(0, userId.toString())
                        .mapTo(Int::class.java).first() ?: 0
                }
            }

        return BookListDto(books, total, safePage, safePageSize)
    }

    fun searchBooks(
        userId: UUID,
        query: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): BookListDto {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 100)
        val offset = (safePage - 1) * safePageSize
        val likeQuery = "%${query.trim().lowercase()}%"

        val books = jdbi.withHandle<List<BookDto>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.* FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ?
                  AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.description) LIKE ?)
                ORDER BY b.title LIMIT ? OFFSET ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, likeQuery)
                .bind(2, likeQuery)
                .bind(3, likeQuery)
                .bind(4, safePageSize)
                .bind(5, offset)
                .map { row -> mapBook(row) }.list()
        }

        val total = jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery(
                """
                SELECT COUNT(*) FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ?
                  AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.description) LIKE ?)
                """,
            )
                .bind(0, userId.toString())
                .bind(1, likeQuery)
                .bind(2, likeQuery)
                .bind(3, likeQuery)
                .mapTo(java.lang.Integer::class.java).first()?.toInt() ?: 0
        }

        return BookListDto(books, total, safePage, safePageSize)
    }

    fun getBook(
        userId: UUID,
        bookId: UUID,
    ): BookDto? {
        return jdbi.withHandle<BookDto?, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.*, rp.current_page AS rp_current_page, rp.total_pages AS rp_total_pages,
                       rp.percentage AS rp_percentage, rp.last_read_at AS rp_last_read_at
                FROM books b
                JOIN libraries l ON b.library_id = l.id
                LEFT JOIN reading_progress rp ON rp.book_id = b.id AND rp.user_id = :userId
                WHERE b.id = :bookId AND l.user_id = :userId
                """,
            )
                .bind("userId", userId.toString())
                .bind("bookId", bookId.toString())
                .map { row ->
                    val book = mapBook(row)
                    val currentPage = try { row.getColumn("rp_current_page", java.lang.Integer::class.java)?.toInt() } catch (_: Exception) { null }
                    if (currentPage != null) {
                        book.copy(progress = ReadingProgressDto(
                            currentPage = currentPage,
                            totalPages = try { row.getColumn("rp_total_pages", java.lang.Integer::class.java)?.toInt() } catch (_: Exception) { null },
                            percentage = try { row.getColumn("rp_percentage", java.lang.Double::class.java)?.toDouble() } catch (_: Exception) { null },
                            lastReadAt = try { row.getColumn("rp_last_read_at", String::class.java) ?: "" } catch (_: Exception) { "" },
                        ))
                    } else book
                }.firstOrNull()
        }
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
                "INSERT INTO books (id, library_id, title, author, description, file_path, file_size, added_at, updated_at) VALUES (?, ?, ?, ?, ?, '', 0, ?, ?)",
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
                "UPDATE books SET title = ?, author = ?, description = ?, updated_at = ? WHERE id = ?",
            )
                .bind(0, request.title)
                .bind(1, request.author)
                .bind(2, request.description)
                .bind(3, Instant.now().toString())
                .bind(4, bookId.toString())
                .execute()
        }

        logger.info("Book updated: ${request.title}")
        return book.copy(title = request.title, author = request.author, description = request.description)
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

    fun updateProgress(
        userId: UUID,
        bookId: UUID,
        request: UpdateProgressRequest,
    ): ReadingProgressDto? {
        val book = getBook(userId, bookId) ?: return null
        val now = Instant.now()
        val totalPages = book.pageCount ?: 0
        val percentage = if (totalPages > 0) (request.currentPage.toDouble() / totalPages) * 100 else null

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
                    "INSERT INTO reading_progress (id, user_id, book_id, current_page, total_pages, percentage, last_read_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
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

        logger.info("Progress updated for book ${book.title}: page ${request.currentPage}")
        return ReadingProgressDto(request.currentPage, totalPages.takeIf { it > 0 }, percentage, now.toString())
    }

    private fun mapBook(row: RowView): BookDto {
        val pageCount: Int? = try {
            row.getColumn("page_count", java.lang.Integer::class.java)?.toInt()
        } catch (e: Exception) {
            null
        }
        return BookDto(
            id = row.getColumn("id", String::class.java),
            title = row.getColumn("title", String::class.java),
            author = row.getColumn("author", String::class.java),
            description = row.getColumn("description", String::class.java),
            coverUrl = row.getColumn("cover_path", String::class.java)?.let { "/covers/$it" },
            pageCount = pageCount,
            fileSize = row.getColumn("file_size", java.lang.Long::class.java)?.toLong() ?: 0L,
            addedAt = row.getColumn("added_at", String::class.java),
            progress = null,
        )
    }
}
