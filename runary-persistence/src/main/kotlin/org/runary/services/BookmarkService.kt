package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.runary.models.BookmarkDto
import org.runary.models.CreateBookmarkRequest
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.BookmarkService")

class BookmarkService(
    private val jdbi: Jdbi,
) {
    fun getBookmarks(
        userId: UUID,
        bookId: UUID,
    ): List<BookmarkDto> =
        jdbi.withHandle<List<BookmarkDto>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT * FROM bookmarks WHERE user_id = ? AND book_id = ? ORDER BY page ASC",
                ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .map { row ->
                    BookmarkDto(
                        id = row.getColumn("id", String::class.java),
                        page = row.getColumn("page", java.lang.Integer::class.java)?.toInt() ?: 0,
                        title = row.getColumn("title", String::class.java),
                        note = row.getColumn("note", String::class.java),
                        createdAt = row.getColumn("created_at", String::class.java),
                    )
                }.list()
        }

    fun createBookmark(
        userId: UUID,
        request: CreateBookmarkRequest,
    ): Result<BookmarkDto> {
        val bookId =
            try {
                UUID.fromString(request.bookId)
            } catch (e: IllegalArgumentException) {
                return Result.failure(IllegalArgumentException("Invalid book ID format"))
            }

        // Verify the book belongs to the user (via library ownership)
        val bookExists =
            jdbi.withHandle<Boolean, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT COUNT(*) FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE b.id = ? AND l.user_id = ?
                """,
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .mapTo(java.lang.Integer::class.java)
                    .first()
                    ?.toInt() ?: 0 > 0
            }

        if (!bookExists) {
            return Result.failure(IllegalArgumentException("Book not found"))
        }

        val now = Instant.now()
        val bookmarkId = UUID.randomUUID()

        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "INSERT INTO bookmarks (id, user_id, book_id, page, title, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).bind(0, bookmarkId.toString())
                .bind(1, userId.toString())
                .bind(2, bookId.toString())
                .bind(3, request.page)
                .bind(4, request.title)
                .bind(5, request.note)
                .bind(6, now.toString())
                .execute()
        }

        logger.info("Bookmark created for book $bookId at page ${request.page}")

        return Result.success(
            BookmarkDto(
                id = bookmarkId.toString(),
                page = request.page,
                title = request.title,
                note = request.note,
                createdAt = now.toString(),
            ),
        )
    }

    fun deleteBookmark(
        userId: UUID,
        bookmarkId: UUID,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        "DELETE FROM bookmarks WHERE id = ? AND user_id = ?",
                    ).bind(0, bookmarkId.toString())
                    .bind(1, userId.toString())
                    .execute()
            }

        if (deleted > 0) logger.info("Bookmark $bookmarkId deleted")
        return deleted > 0
    }
}
