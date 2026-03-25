package org.runary.services

import org.jdbi.v3.core.Jdbi
import java.time.LocalDate
import java.util.UUID

data class ListeningHeatmapEntry(
    val date: String,
    val secondsListened: Int,
)

data class ListeningStats(
    val totalSecondsListened: Long,
    val totalBooksFinished: Int,
    val totalSessions: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val averageSecondsPerDay: Double,
    val heatmap: List<ListeningHeatmapEntry>,
    val secondsByTag: Map<String, Long>,
    val secondsByCategory: Map<String, Long>,
)

class ListeningStatsService(
    private val jdbi: Jdbi,
) {
    fun getStats(
        userId: UUID,
        days: Int = 365,
    ): ListeningStats {
        val since = LocalDate.now().minusDays(days.toLong())
        val sinceStr = since.toString()

        val totalSeconds =
            jdbi.withHandle<Long, Exception> { h ->
                h
                    .createQuery("SELECT COALESCE(SUM(seconds_listened), 0) FROM listen_sessions WHERE user_id = ?")
                    .bind(0, userId.toString())
                    .mapTo(Long::class.java)
                    .firstOrNull() ?: 0L
            }

        val totalFinished =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM book_status WHERE user_id = ? AND status = 'FINISHED'")
                    .bind(0, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .firstOrNull() ?: 0
            }

        val totalSessions =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM listen_sessions WHERE user_id = ?")
                    .bind(0, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .firstOrNull() ?: 0
            }

        val dailyRows =
            jdbi.withHandle<List<Pair<String, Int>>, Exception> { h ->
                h
                    .createQuery(
                        """SELECT SUBSTR(session_at, 1, 10) AS listen_day, SUM(seconds_listened) AS total
                   FROM listen_sessions
                   WHERE user_id = ? AND session_at >= ?
                   GROUP BY SUBSTR(session_at, 1, 10)
                   ORDER BY listen_day""",
                    ).bind(0, userId.toString())
                    .bind(1, sinceStr)
                    .map { row ->
                        Pair(
                            row.getColumn("listen_day", String::class.java),
                            (row.getColumn("total", java.lang.Long::class.java) ?: 0L).toInt(),
                        )
                    }.list()
            }

        val heatmap = dailyRows.map { ListeningHeatmapEntry(it.first, it.second) }
        val dailyMap = dailyRows.toMap()
        val (currentStreak, longestStreak) = computeStreaks(dailyMap)

        val totalRecentSeconds = dailyRows.sumOf { it.second.toLong() }
        val avgPerDay = if (days > 0) totalRecentSeconds.toDouble() / days else 0.0

        val secondsByTag =
            jdbi.withHandle<Map<String, Long>, Exception> { h ->
                h
                    .createQuery(
                        """SELECT bt.tag, SUM(ls.seconds_listened) AS total
                   FROM listen_sessions ls
                   JOIN book_tags bt ON bt.book_id = ls.book_id AND bt.user_id = ls.user_id
                   WHERE ls.user_id = ?
                   GROUP BY bt.tag
                   ORDER BY total DESC""",
                    ).bind(0, userId.toString())
                    .map { row ->
                        Pair(
                            row.getColumn("tag", String::class.java),
                            (row.getColumn("total", java.lang.Long::class.java) ?: 0L) as Long,
                        )
                    }.list()
                    .toMap()
            }

        val secondsByCategory =
            jdbi.withHandle<Map<String, Long>, Exception> { h ->
                h
                    .createQuery(
                        """SELECT bc.category, SUM(ls.seconds_listened) AS total
                   FROM listen_sessions ls
                   JOIN book_categories bc ON bc.book_id = ls.book_id AND bc.user_id = ls.user_id
                   WHERE ls.user_id = ?
                   GROUP BY bc.category
                   ORDER BY total DESC""",
                    ).bind(0, userId.toString())
                    .map { row ->
                        Pair(
                            row.getColumn("category", String::class.java),
                            (row.getColumn("total", java.lang.Long::class.java) ?: 0L) as Long,
                        )
                    }.list()
                    .toMap()
            }

        return ListeningStats(
            totalSecondsListened = totalSeconds,
            totalBooksFinished = totalFinished,
            totalSessions = totalSessions,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            averageSecondsPerDay = avgPerDay,
            heatmap = heatmap,
            secondsByTag = secondsByTag,
            secondsByCategory = secondsByCategory,
        )
    }

    private fun computeStreaks(dailyMap: Map<String, Int>): Pair<Int, Int> {
        if (dailyMap.isEmpty()) return Pair(0, 0)
        val today = LocalDate.now()
        var streak = 0
        var day = today
        while (dailyMap.containsKey(day.toString())) {
            streak++
            day = day.minusDays(1)
        }
        val current = streak
        val sortedDays = dailyMap.keys.sorted().map { LocalDate.parse(it) }
        var runStreak = 1
        var longest = 1
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] == sortedDays[i - 1].plusDays(1)) {
                runStreak++
                if (runStreak > longest) longest = runStreak
            } else {
                runStreak = 1
            }
        }
        return Pair(current, longest)
    }
}
