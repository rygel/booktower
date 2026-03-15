package org.booktower.services

import org.booktower.models.ReadingSessionDto
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.ReadingSessionService")

class ReadingSessionService(private val jdbi: Jdbi) {

    /** Called from BookService.updateProgress whenever at least 1 page was read. */
    fun recordSession(userId: UUID, bookId: UUID, startPage: Int, endPage: Int, pagesRead: Int) {
        if (pagesRead <= 0) return
        try {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate(
                    """
                    INSERT INTO reading_sessions (id, user_id, book_id, start_page, end_page, pages_read, session_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                )
                    .bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, startPage)
                    .bind(4, endPage)
                    .bind(5, pagesRead)
                    .bind(6, Instant.now().toString())
                    .execute()
            }
        } catch (e: Exception) {
            logger.warn("Failed to record reading session for book $bookId: ${e.message}")
        }
    }

    /** Returns the most recent sessions across all books for a user. */
    fun getRecentSessions(userId: UUID, limit: Int = 20): List<ReadingSessionDto> =
        jdbi.withHandle<List<ReadingSessionDto>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT rs.id, rs.book_id, b.title AS book_title,
                       rs.start_page, rs.end_page, rs.pages_read, rs.session_at
                FROM reading_sessions rs
                JOIN books b ON rs.book_id = b.id
                WHERE rs.user_id = ?
                ORDER BY rs.session_at DESC
                LIMIT ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, limit)
                .map { row ->
                    ReadingSessionDto(
                        id = row.getColumn("id", String::class.java),
                        bookId = row.getColumn("book_id", String::class.java),
                        bookTitle = row.getColumn("book_title", String::class.java),
                        startPage = row.getColumn("start_page", java.lang.Integer::class.java)?.toInt() ?: 0,
                        endPage = row.getColumn("end_page", java.lang.Integer::class.java)?.toInt() ?: 0,
                        pagesRead = row.getColumn("pages_read", java.lang.Integer::class.java)?.toInt() ?: 0,
                        sessionAt = row.getColumn("session_at", String::class.java),
                    )
                }.list()
        }

    /** Returns sessions for a specific book, newest first. */
    fun getSessionsForBook(userId: UUID, bookId: UUID, limit: Int = 20): List<ReadingSessionDto> =
        jdbi.withHandle<List<ReadingSessionDto>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT rs.id, rs.book_id, b.title AS book_title,
                       rs.start_page, rs.end_page, rs.pages_read, rs.session_at
                FROM reading_sessions rs
                JOIN books b ON rs.book_id = b.id
                WHERE rs.user_id = ? AND rs.book_id = ?
                ORDER BY rs.session_at DESC
                LIMIT ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .bind(2, limit)
                .map { row ->
                    ReadingSessionDto(
                        id = row.getColumn("id", String::class.java),
                        bookId = row.getColumn("book_id", String::class.java),
                        bookTitle = row.getColumn("book_title", String::class.java),
                        startPage = row.getColumn("start_page", java.lang.Integer::class.java)?.toInt() ?: 0,
                        endPage = row.getColumn("end_page", java.lang.Integer::class.java)?.toInt() ?: 0,
                        pagesRead = row.getColumn("pages_read", java.lang.Integer::class.java)?.toInt() ?: 0,
                        sessionAt = row.getColumn("session_at", String::class.java),
                    )
                }.list()
        }
}
