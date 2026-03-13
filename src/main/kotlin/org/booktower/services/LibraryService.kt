package org.booktower.services

import org.booktower.config.StorageConfig
import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.LibraryService")

class LibraryService(
    private val jdbi: Jdbi,
    private val storageConfig: StorageConfig,
) {
    fun getLibraries(userId: UUID): List<LibraryDto> {
        return jdbi.withHandle<List<LibraryDto>, Exception> { handle ->
            handle.createQuery("SELECT * FROM libraries WHERE user_id = ? ORDER BY name")
                .bind(0, userId.toString())
                .map { row -> mapLibrary(handle, row) }
                .list()
        }
    }

    fun getLibrary(
        userId: UUID,
        libraryId: UUID,
    ): LibraryDto? {
        return jdbi.withHandle<LibraryDto?, Exception> { handle ->
            handle.createQuery("SELECT * FROM libraries WHERE user_id = ? AND id = ?")
                .bind(0, userId.toString())
                .bind(1, libraryId.toString())
                .map { row -> mapLibrary(handle, row) }
                .firstOrNull()
        }
    }

    private fun mapLibrary(
        handle: org.jdbi.v3.core.Handle,
        row: RowView,
    ): LibraryDto {
        val libId = UUID.fromString(row.getColumn("id", String::class.java))
        val count =
            handle.createQuery("SELECT COUNT(*) FROM books WHERE library_id = ?")
                .bind(0, libId.toString())
                .mapTo(Int::class.java)
                .first() ?: 0

        return LibraryDto(
            id = libId.toString(),
            name = row.getColumn("name", String::class.java),
            path = row.getColumn("path", String::class.java),
            bookCount = count,
            createdAt = row.getColumn("created_at", String::class.java),
        )
    }

    fun createLibrary(
        userId: UUID,
        request: CreateLibraryRequest,
    ): LibraryDto {
        val now = Instant.now()
        val libId = UUID.randomUUID()

        val dir = File(request.path)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create library directory: ${request.path}")
        }

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("INSERT INTO libraries (id, user_id, name, path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
                .bind(0, libId.toString())
                .bind(1, userId.toString())
                .bind(2, request.name)
                .bind(3, request.path)
                .bind(4, now.toString())
                .bind(5, now.toString())
                .execute()
        }

        logger.info("Library created: ${request.name}")

        return LibraryDto(libId.toString(), request.name, request.path, 0, now.toString())
    }

    fun deleteLibrary(
        userId: UUID,
        libraryId: UUID,
    ): Boolean {
        val lib = getLibrary(userId, libraryId) ?: return false

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM libraries WHERE id = ?").bind(0, libraryId.toString()).execute()
        }

        logger.info("Library deleted: ${lib.name}")
        return true
    }
}

class BookService(private val jdbi: Jdbi, private val storageConfig: StorageConfig) {
    fun getBooks(
        userId: UUID,
        libraryId: String?,
        page: Int = 1,
        pageSize: Int = 20,
    ): BookListDto {
        val offset = (page - 1) * pageSize

        val books =
            if (libraryId != null) {
                jdbi.withHandle<List<BookDto>, Exception> { handle ->
                    handle.createQuery("SELECT * FROM books WHERE library_id = ? ORDER BY title LIMIT ? OFFSET ?")
                        .bind(0, libraryId)
                        .bind(1, pageSize)
                        .bind(2, offset)
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
                        .bind(1, pageSize)
                        .bind(2, offset)
                        .map { row -> mapBook(row) }.list()
                }
            }

        val total =
            if (libraryId != null) {
                jdbi.withHandle<Int, Exception> { handle ->
                    handle.createQuery("SELECT COUNT(*) FROM books WHERE library_id = ?")
                        .bind(0, libraryId)
                        .mapTo(Int::class.java).first() ?: 0
                }
            } else {
                jdbi.withHandle<Int, Exception> { handle ->
                    handle.createQuery("SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ?")
                        .bind(0, userId.toString())
                        .mapTo(Int::class.java).first() ?: 0
                }
            }

        return BookListDto(books, total, page, pageSize)
    }

    fun getBook(
        userId: UUID,
        bookId: UUID,
    ): BookDto? {
        return jdbi.withHandle<BookDto?, Exception> { handle ->
            handle.createQuery("SELECT * FROM books WHERE id = ?")
                .bind(0, bookId.toString())
                .map { row -> mapBook(row) }.firstOrNull()
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

        val exists =
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
                            currentPage = row.getColumn("current_page", Int::class.java),
                            totalPages =
                                row.getColumn(
                                    "total_pages",
                                    Int::class.java,
                                ).takeIf { row.getColumn("total_pages", Any::class.java) != null },
                            percentage =
                                row.getColumn(
                                    "percentage",
                                    Double::class.java,
                                ).takeIf { row.getColumn("percentage", Any::class.java) != null },
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
        val pageCountVal: Any? = row.getColumn("page_count", Any::class.java)
        return BookDto(
            id = row.getColumn("id", String::class.java),
            title = row.getColumn("title", String::class.java),
            author = row.getColumn("author", String::class.java),
            description = row.getColumn("description", String::class.java),
            coverUrl = row.getColumn("cover_path", String::class.java)?.let { "/covers/$it" },
            pageCount = if (pageCountVal != null) row.getColumn("page_count", Int::class.java) else null,
            fileSize = row.getColumn("file_size", Long::class.java),
            addedAt = row.getColumn("added_at", String::class.java),
            progress = null,
        )
    }
}
