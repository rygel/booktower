package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.LocalDate
import java.util.UUID

data class ReadingHeatmapEntry(
    val date: String,
    val pagesRead: Int,
)

data class ReadingStats(
    val totalPagesRead: Long,
    val totalBooksFinished: Int,
    val totalSessions: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val averagePagesPerDay: Double,
    val heatmap: List<ReadingHeatmapEntry>,
    val pagesByTag: Map<String, Long>,
    val pagesByCategory: Map<String, Long>,
)

class ReadingStatsService(
    private val jdbi: Jdbi,
) {
    fun getStats(
        userId: UUID,
        days: Int = 365,
    ): ReadingStats {
        val since = LocalDate.now().minusDays(days.toLong())
        val sinceStr = since.toString()
        val uid = userId.toString()

        // Single connection for all queries — avoids 5 separate pool checkouts
        return jdbi.withHandle<ReadingStats, Exception> { h ->
            val totalPages =
                h
                    .createQuery("SELECT COALESCE(SUM(pages_read), 0) FROM reading_sessions WHERE user_id = ?")
                    .bind(0, uid)
                    .mapTo(Long::class.java)
                    .firstOrNull() ?: 0L

            val totalFinished =
                h
                    .createQuery("SELECT COUNT(*) FROM book_status WHERE user_id = ? AND status = 'FINISHED'")
                    .bind(0, uid)
                    .mapTo(Int::class.java)
                    .firstOrNull() ?: 0

            val totalSessions =
                h
                    .createQuery("SELECT COUNT(*) FROM reading_sessions WHERE user_id = ?")
                    .bind(0, uid)
                    .mapTo(Int::class.java)
                    .firstOrNull() ?: 0

            val dailyRows =
                h
                    .createQuery(
                        """SELECT SUBSTR(session_at, 1, 10) AS read_day, SUM(pages_read) AS total
                       FROM reading_sessions
                       WHERE user_id = ? AND session_at >= ?
                       GROUP BY SUBSTR(session_at, 1, 10)
                       ORDER BY read_day""",
                    ).bind(0, uid)
                    .bind(1, sinceStr)
                    .map { row ->
                        val day = row.getColumn("read_day", String::class.java)
                        val total = row.getColumn("total", java.lang.Long::class.java)?.toInt() ?: 0
                        Pair(day, total)
                    }.list()

            val heatmap = dailyRows.map { ReadingHeatmapEntry(it.first, it.second) }
            val dailyMap = dailyRows.toMap()
            val (currentStreak, longestStreak) = computeStreaks(dailyMap)

            val totalRecentPages = dailyRows.sumOf { it.second.toLong() }
            val avgPerDay = if (dailyRows.isNotEmpty()) totalRecentPages.toDouble() / days else 0.0

            val pagesByTag =
                h
                    .createQuery(
                        """SELECT bt.tag, SUM(rs.pages_read) AS total
                       FROM reading_sessions rs
                       JOIN book_tags bt ON bt.book_id = rs.book_id AND bt.user_id = rs.user_id
                       WHERE rs.user_id = ?
                       GROUP BY bt.tag
                       ORDER BY total DESC""",
                    ).bind(0, uid)
                    .map { row ->
                        Pair(
                            row.getColumn("tag", String::class.java),
                            (row.getColumn("total", java.lang.Long::class.java) ?: 0L) as Long,
                        )
                    }.list()
                    .toMap()

            val pagesByCategory =
                h
                    .createQuery(
                        """SELECT bc.category, SUM(rs.pages_read) AS total
                       FROM reading_sessions rs
                       JOIN book_categories bc ON bc.book_id = rs.book_id AND bc.user_id = rs.user_id
                       WHERE rs.user_id = ?
                       GROUP BY bc.category
                       ORDER BY total DESC""",
                    ).bind(0, uid)
                    .map { row ->
                        Pair(
                            row.getColumn("category", String::class.java),
                            (row.getColumn("total", java.lang.Long::class.java) ?: 0L) as Long,
                        )
                    }.list()
                    .toMap()

            ReadingStats(
                totalPagesRead = totalPages,
                totalBooksFinished = totalFinished,
                totalSessions = totalSessions,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                averagePagesPerDay = avgPerDay,
                heatmap = heatmap,
                pagesByTag = pagesByTag,
                pagesByCategory = pagesByCategory,
            )
        }
    }

    private fun computeStreaks(dailyMap: Map<String, Int>): Pair<Int, Int> {
        if (dailyMap.isEmpty()) return Pair(0, 0)

        val today = LocalDate.now()
        var current = 0
        var longest = 0
        var streak = 0

        // Walk back from today
        var day = today
        while (dailyMap.containsKey(day.toString())) {
            streak++
            day = day.minusDays(1)
        }
        current = streak

        // Compute longest streak from sorted days
        val sortedDays = dailyMap.keys.sorted().map { LocalDate.parse(it) }
        var runStreak = 1
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] == sortedDays[i - 1].plusDays(1)) {
                runStreak++
                if (runStreak > longest) longest = runStreak
            } else {
                if (runStreak > longest) longest = runStreak
                runStreak = 1
            }
        }
        if (runStreak > longest) longest = runStreak

        return Pair(current, longest)
    }
}
