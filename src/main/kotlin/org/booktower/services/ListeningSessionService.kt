package org.booktower.services

import org.booktower.models.ListenProgressDto
import org.booktower.models.ListenSessionDto
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.ListeningSessionService")

class ListeningSessionService(
    private val jdbi: Jdbi,
) {
    /** Records a listening session and updates progress. */
    fun recordSession(
        userId: UUID,
        bookId: UUID,
        startPosSec: Int,
        endPosSec: Int,
        totalSec: Int?,
    ) {
        val secondsListened = maxOf(0, endPosSec - startPosSec)
        if (secondsListened <= 0) return
        val now = Instant.now().toString()
        try {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate(
                        """INSERT INTO listen_sessions
                       (id, user_id, book_id, start_pos_sec, end_pos_sec, seconds_listened, session_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    ).bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, startPosSec)
                    .bind(4, endPosSec)
                    .bind(5, secondsListened)
                    .bind(6, now)
                    .execute()
            }
        } catch (e: Exception) {
            logger.warn("Failed to record listen session for book $bookId: ${e.message}")
        }
        updateProgress(userId, bookId, endPosSec, totalSec)
    }

    /** Updates the playback position without creating a session (e.g. periodic position saves). */
    fun updateProgress(
        userId: UUID,
        bookId: UUID,
        positionSec: Int,
        totalSec: Int?,
    ) {
        val now = Instant.now().toString()
        val existing =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM listen_progress WHERE user_id = ? AND book_id = ?")
                    .bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .mapTo(Int::class.java)
                    .firstOrNull() ?: 0
            }
        if (existing > 0) {
            jdbi.useHandle<Exception> { h ->
                val q =
                    if (totalSec != null) {
                        h
                            .createUpdate(
                                "UPDATE listen_progress SET position_sec = ?, total_sec = ?, updated_at = ? WHERE user_id = ? AND book_id = ?",
                            ).bind(0, positionSec)
                            .bind(1, totalSec)
                            .bind(2, now)
                            .bind(3, userId.toString())
                            .bind(4, bookId.toString())
                    } else {
                        h
                            .createUpdate(
                                "UPDATE listen_progress SET position_sec = ?, updated_at = ? WHERE user_id = ? AND book_id = ?",
                            ).bind(0, positionSec)
                            .bind(1, now)
                            .bind(2, userId.toString())
                            .bind(3, bookId.toString())
                    }
                q.execute()
            }
        } else {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        "INSERT INTO listen_progress (user_id, book_id, position_sec, total_sec, updated_at) VALUES (?, ?, ?, ?, ?)",
                    ).bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .bind(2, positionSec)
                    .bind(3, totalSec)
                    .bind(4, now)
                    .execute()
            }
        }
    }

    fun getProgress(
        userId: UUID,
        bookId: UUID,
    ): ListenProgressDto? =
        jdbi.withHandle<ListenProgressDto?, Exception> { h ->
            h
                .createQuery(
                    "SELECT book_id, position_sec, total_sec, updated_at FROM listen_progress WHERE user_id = ? AND book_id = ?",
                ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .map { row ->
                    ListenProgressDto(
                        bookId = row.getColumn("book_id", String::class.java),
                        positionSec = row.getColumn("position_sec", java.lang.Integer::class.java)?.toInt() ?: 0,
                        totalSec = row.getColumn("total_sec", java.lang.Integer::class.java)?.toInt(),
                        updatedAt = row.getColumn("updated_at", String::class.java),
                    )
                }.firstOrNull()
        }

    fun getRecentSessions(
        userId: UUID,
        limit: Int = 20,
    ): List<ListenSessionDto> =
        jdbi.withHandle<List<ListenSessionDto>, Exception> { h ->
            h
                .createQuery(
                    """SELECT ls.id, ls.book_id, b.title AS book_title,
                          ls.start_pos_sec, ls.end_pos_sec, ls.seconds_listened, ls.session_at
                   FROM listen_sessions ls
                   JOIN books b ON ls.book_id = b.id
                   WHERE ls.user_id = ?
                   ORDER BY ls.session_at DESC
                   LIMIT ?""",
                ).bind(0, userId.toString())
                .bind(1, limit)
                .map { row ->
                    ListenSessionDto(
                        id = row.getColumn("id", String::class.java),
                        bookId = row.getColumn("book_id", String::class.java),
                        bookTitle = row.getColumn("book_title", String::class.java),
                        startPosSec = row.getColumn("start_pos_sec", java.lang.Integer::class.java)?.toInt() ?: 0,
                        endPosSec = row.getColumn("end_pos_sec", java.lang.Integer::class.java)?.toInt() ?: 0,
                        secondsListened = row.getColumn("seconds_listened", java.lang.Integer::class.java)?.toInt() ?: 0,
                        sessionAt = row.getColumn("session_at", String::class.java),
                    )
                }.list()
        }
}
