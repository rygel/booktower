package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

data class DailyPageCount(val date: String, val pages: Int, val barHeightPct: Int)

data class AnalyticsSummary(
    val enabled: Boolean,
    val streak: Int,
    val totalPages: Int,
    val booksFinished: Int,
    val pagesLast30Days: List<DailyPageCount>,
)

private val logger = LoggerFactory.getLogger("booktower.AnalyticsService")

class AnalyticsService(private val jdbi: Jdbi, private val userSettingsService: UserSettingsService) {

    fun isEnabled(userId: UUID): Boolean {
        return userSettingsService.get(userId, "analytics.enabled") == "true"
    }

    fun recordProgress(userId: UUID, bookId: UUID, pagesRead: Int) {
        if (pagesRead <= 0) return
        if (!isEnabled(userId)) return

        val today = LocalDate.now().toString()

        val existingId = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery(
                "SELECT id FROM reading_daily WHERE user_id = ? AND book_id = ? AND date = ?",
            )
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .bind(2, today)
                .mapTo(String::class.java)
                .firstOrNull()
        }

        if (existingId != null) {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate(
                    "UPDATE reading_daily SET pages_read = pages_read + ? WHERE user_id = ? AND book_id = ? AND date = ?",
                )
                    .bind(0, pagesRead)
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, today)
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate(
                    "INSERT INTO reading_daily (id, user_id, book_id, date, pages_read) VALUES (?, ?, ?, ?, ?)",
                )
                    .bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, bookId.toString())
                    .bind(3, today)
                    .bind(4, pagesRead)
                    .execute()
            }
        }

        logger.debug("Recorded $pagesRead pages for user $userId book $bookId on $today")
    }

    fun getSummary(userId: UUID): AnalyticsSummary {
        val enabled = isEnabled(userId)

        val streak = computeStreak(userId)
        val totalPages = computeTotalPages(userId)
        val booksFinished = computeBooksFinished(userId)
        val pagesLast30Days = computePagesLast30Days(userId)

        return AnalyticsSummary(
            enabled = enabled,
            streak = streak,
            totalPages = totalPages,
            booksFinished = booksFinished,
            pagesLast30Days = pagesLast30Days,
        )
    }

    private fun computeStreak(userId: UUID): Int {
        val dates = jdbi.withHandle<List<LocalDate>, Exception> { handle ->
            handle.createQuery(
                "SELECT DISTINCT date FROM reading_daily WHERE user_id = ? AND pages_read > 0 ORDER BY date DESC",
            )
                .bind(0, userId.toString())
                .mapTo(String::class.java)
                .list()
                .map { LocalDate.parse(it.take(10)) }
        }

        if (dates.isEmpty()) return 0

        val today = LocalDate.now()
        if (dates[0] < today.minusDays(1)) return 0

        var streak = 0
        var expected = dates[0]
        for (date in dates) {
            if (date == expected) {
                streak++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    private fun computeTotalPages(userId: UUID): Int {
        return jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery(
                "SELECT COALESCE(SUM(pages_read), 0) FROM reading_daily WHERE user_id = ?",
            )
                .bind(0, userId.toString())
                .mapTo(java.lang.Long::class.java)
                .first()
                ?.toInt() ?: 0
        }
    }

    private fun computeBooksFinished(userId: UUID): Int {
        return jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery(
                """
                SELECT COUNT(*) FROM reading_progress rp
                INNER JOIN books b ON rp.book_id = b.id
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE rp.user_id = ? AND rp.percentage >= 99 AND l.user_id = ?
                """,
            )
                .bind(0, userId.toString())
                .bind(1, userId.toString())
                .mapTo(java.lang.Integer::class.java)
                .first()
                ?.toInt() ?: 0
        }
    }

    private fun computePagesLast30Days(userId: UUID): List<DailyPageCount> {
        val today = LocalDate.now()
        val cutoff = today.minusDays(29).toString()

        val rows = jdbi.withHandle<Map<String, Int>, Exception> { handle ->
            handle.createQuery(
                "SELECT date, SUM(pages_read) as total FROM reading_daily WHERE user_id = ? AND date >= ? GROUP BY date",
            )
                .bind(0, userId.toString())
                .bind(1, cutoff)
                .map { row ->
                    val date = row.getColumn("date", String::class.java)
                    val total = row.getColumn("total", java.lang.Long::class.java)?.toInt() ?: 0
                    date to total
                }
                .list()
                .toMap()
        }

        val entries = (0..29).map { daysAgo ->
            val date = today.minusDays((29 - daysAgo).toLong())
            val pages = rows[date.toString()] ?: 0
            date.toString() to pages
        }

        val maxPages = entries.maxOfOrNull { it.second } ?: 0

        return entries.map { (date, pages) ->
            val barHeightPct = if (maxPages > 0 && pages > 0) maxOf(2, pages * 100 / maxPages) else 0
            DailyPageCount(date, pages, barHeightPct)
        }
    }
}
