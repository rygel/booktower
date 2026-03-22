package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

data class TimelineEntry(
    val date: String,
    val bookId: String,
    val bookTitle: String,
    val bookAuthor: String?,
    val coverUrl: String?,
    val eventType: String,
    val detail: String?,
)

/**
 * Builds a reading history timeline combining:
 * - Books finished (status set to FINISHED)
 * - Reading sessions (pages read per day)
 * - Books added to library
 */
class ReadingTimelineService(
    private val jdbi: Jdbi,
) {
    /**
     * Returns a chronological timeline of reading activity for [userId],
     * limited to the last [days] days, most recent first.
     */
    fun getTimeline(
        userId: UUID,
        days: Int = 90,
        limit: Int = 50,
    ): List<TimelineEntry> {
        val uid = userId.toString()
        return jdbi.withHandle<List<TimelineEntry>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT * FROM (
                    -- Books finished
                    SELECT bs.updated_at AS event_date,
                           b.id AS book_id, b.title AS book_title, b.author AS book_author,
                           b.cover_url,
                           'finished' AS event_type,
                           NULL AS detail
                    FROM book_status bs
                    INNER JOIN books b ON bs.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE bs.user_id = ? AND l.user_id = ? AND bs.status = 'FINISHED'
                      AND bs.updated_at >= ?

                    UNION ALL

                    -- Reading sessions (aggregated per day per book)
                    SELECT SUBSTRING(rs.session_at, 1, 10) AS event_date,
                           b.id AS book_id, b.title AS book_title, b.author AS book_author,
                           b.cover_url,
                           'reading' AS event_type,
                           CAST(SUM(rs.pages_read) AS VARCHAR(20)) AS detail
                    FROM reading_sessions rs
                    INNER JOIN books b ON rs.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE rs.user_id = ? AND l.user_id = ?
                      AND rs.session_at >= ?
                    GROUP BY SUBSTRING(rs.session_at, 1, 10), b.id, b.title, b.author, b.cover_url

                    UNION ALL

                    -- Books added
                    SELECT b.added_at AS event_date,
                           b.id AS book_id, b.title AS book_title, b.author AS book_author,
                           b.cover_url,
                           'added' AS event_type,
                           NULL AS detail
                    FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ?
                      AND b.added_at >= ?
                ) timeline
                ORDER BY event_date DESC
                LIMIT ?
                """,
                ).bind(0, uid) // finished user_id
                .bind(1, uid) // finished library check
                .bind(2, daysAgo(days)) // finished date filter
                .bind(3, uid) // reading user_id
                .bind(4, uid) // reading library check
                .bind(5, daysAgo(days)) // reading date filter
                .bind(6, uid) // added library check
                .bind(7, daysAgo(days)) // added date filter
                .bind(8, limit)
                .map { row ->
                    TimelineEntry(
                        date = row.getColumn("event_date", String::class.java) ?: "",
                        bookId = row.getColumn("book_id", String::class.java) ?: "",
                        bookTitle = row.getColumn("book_title", String::class.java) ?: "",
                        bookAuthor = row.getColumn("book_author", String::class.java),
                        coverUrl = row.getColumn("cover_url", String::class.java),
                        eventType = row.getColumn("event_type", String::class.java) ?: "",
                        detail = row.getColumn("detail", String::class.java),
                    )
                }.list()
        }
    }

    private fun daysAgo(days: Int): String =
        java.time.Instant
            .now()
            .minus(java.time.Duration.ofDays(days.toLong()))
            .toString()
}
