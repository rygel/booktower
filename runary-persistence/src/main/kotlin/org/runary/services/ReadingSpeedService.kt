package org.runary.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

data class ReadingSpeedStats(
    val averagePagesPerHour: Double,
    val totalReadingMinutes: Long,
    val currentBookEstimate: BookTimeEstimate?,
    val recentSessions: List<SessionSpeed>,
)

data class BookTimeEstimate(
    val bookId: String,
    val title: String,
    val pagesRemaining: Int,
    val estimatedMinutes: Int,
)

data class SessionSpeed(
    val bookTitle: String,
    val pagesRead: Int,
    val durationMinutes: Int,
    val pagesPerHour: Double,
    val sessionAt: String,
)

/**
 * Calculates reading speed from session data.
 * Uses reading_sessions which track start_page, end_page, pages_read per session.
 */
class ReadingSpeedService(
    private val jdbi: Jdbi,
) {
    fun getStats(userId: UUID): ReadingSpeedStats {
        val uid = userId.toString()

        return jdbi.withHandle<ReadingSpeedStats, Exception> { h ->
            // Recent sessions with duration estimated from timestamps
            val sessions =
                h
                    .createQuery(
                        """
                    SELECT rs.pages_read, rs.session_at, b.title AS book_title
                    FROM reading_sessions rs
                    INNER JOIN books b ON rs.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE rs.user_id = ? AND l.user_id = ? AND rs.pages_read > 0
                    ORDER BY rs.session_at DESC LIMIT 50
                    """,
                    ).bind(0, uid)
                    .bind(1, uid)
                    .map { row ->
                        SessionSpeed(
                            bookTitle = row.getColumn("book_title", String::class.java) ?: "",
                            pagesRead = row.getColumn("pages_read", Int::class.javaObjectType) ?: 0,
                            durationMinutes = estimateDuration(row.getColumn("pages_read", Int::class.javaObjectType) ?: 0),
                            pagesPerHour = 0.0,
                            sessionAt = row.getColumn("session_at", String::class.java) ?: "",
                        )
                    }.list()

            // Average pages per hour (estimate: ~2 min per page for typical reading)
            val totalPages = sessions.sumOf { it.pagesRead }
            val totalMinutes = sessions.sumOf { it.durationMinutes }.toLong()
            val avgPagesPerHour = if (totalMinutes > 0) (totalPages * 60.0) / totalMinutes else 0.0

            val sessionsWithSpeed =
                sessions.map { s ->
                    val pph = if (s.durationMinutes > 0) (s.pagesRead * 60.0) / s.durationMinutes else 0.0
                    s.copy(pagesPerHour = pph)
                }

            // Current book estimate
            val currentBook =
                h
                    .createQuery(
                        """
                    SELECT b.id, b.title, b.page_count, rp.current_page
                    FROM reading_progress rp
                    INNER JOIN books b ON rp.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE rp.user_id = ? AND l.user_id = ?
                    ORDER BY rp.last_read_at DESC LIMIT 1
                    """,
                    ).bind(0, uid)
                    .bind(1, uid)
                    .map { row ->
                        val pageCount = row.getColumn("page_count", Int::class.javaObjectType) ?: 0
                        val currentPage = row.getColumn("current_page", Int::class.javaObjectType) ?: 0
                        val remaining = (pageCount - currentPage).coerceAtLeast(0)
                        val estMinutes = if (avgPagesPerHour > 0) ((remaining / avgPagesPerHour) * 60).toInt() else 0
                        BookTimeEstimate(
                            bookId = row.getColumn("id", String::class.java) ?: "",
                            title = row.getColumn("title", String::class.java) ?: "",
                            pagesRemaining = remaining,
                            estimatedMinutes = estMinutes,
                        )
                    }.firstOrNull()

            ReadingSpeedStats(
                averagePagesPerHour = avgPagesPerHour,
                totalReadingMinutes = totalMinutes,
                currentBookEstimate = currentBook,
                recentSessions = sessionsWithSpeed.take(20),
            )
        }
    }

    /** Estimate reading duration: ~2 minutes per page for typical reading. */
    private fun estimateDuration(pagesRead: Int): Int = (pagesRead * 2).coerceAtLeast(1)
}
