package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.LocalDate
import java.util.UUID

data class ReadingGoalProgress(
    val year: Int,
    val goal: Int,
    val booksFinished: Int,
    val progressPercent: Int,
    val onTrack: Boolean,
    val monthlyPacing: List<MonthlyPacing>,
    val projectedFinish: Int,
)

data class MonthlyPacing(
    val month: Int,
    val monthName: String,
    val expected: Int,
    val actual: Int,
)

/**
 * Computes reading challenge/goal progress for a user.
 * Goals are stored as user settings: `reading.goal.{year}`.
 */
class ReadingGoalService(
    private val jdbi: Jdbi,
    private val userSettingsService: UserSettingsService,
) {
    private val monthNames =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun getProgress(
        userId: UUID,
        year: Int = LocalDate.now().year,
    ): ReadingGoalProgress {
        val goal = userSettingsService.get(userId, "reading.goal.$year")?.toIntOrNull() ?: 0
        val booksFinished = countBooksFinished(userId, year)
        val now = LocalDate.now()
        val currentMonth = if (now.year == year) now.monthValue else 12
        val dayOfYear = if (now.year == year) now.dayOfYear else 365
        val daysInYear = if (now.year == year) LocalDate.of(year, 12, 31).dayOfYear else 365

        val progressPercent = if (goal > 0) ((booksFinished * 100.0) / goal).toInt().coerceAtMost(100) else 0
        val projectedFinish =
            if (dayOfYear > 0 && booksFinished > 0) {
                ((booksFinished.toDouble() / dayOfYear) * daysInYear).toInt()
            } else {
                0
            }
        val expectedByNow = if (goal > 0) ((goal.toDouble() / 12) * currentMonth).toInt() else 0
        val onTrack = booksFinished >= expectedByNow

        val monthlyPacing = buildMonthlyPacing(userId, year, goal, currentMonth)

        return ReadingGoalProgress(
            year = year,
            goal = goal,
            booksFinished = booksFinished,
            progressPercent = progressPercent,
            onTrack = onTrack,
            monthlyPacing = monthlyPacing,
            projectedFinish = projectedFinish,
        )
    }

    fun setGoal(
        userId: UUID,
        year: Int,
        goal: Int,
    ) {
        userSettingsService.set(userId, "reading.goal.$year", goal.coerceIn(0, 1000).toString())
    }

    private fun countBooksFinished(
        userId: UUID,
        year: Int,
    ): Int =
        jdbi.withHandle<Int, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT COUNT(*) FROM book_status bs
                WHERE bs.user_id = ? AND bs.status = 'FINISHED'
                  AND bs.updated_at >= ? AND bs.updated_at < ?
                """,
                ).bind(0, userId.toString())
                .bind(1, "$year-01-01")
                .bind(2, "${year + 1}-01-01")
                .mapTo(Int::class.java)
                .one()
        }

    private fun buildMonthlyPacing(
        userId: UUID,
        year: Int,
        goal: Int,
        upToMonth: Int,
    ): List<MonthlyPacing> {
        val monthlyFinished =
            jdbi.withHandle<Map<Int, Int>, Exception> { h ->
                h
                    .createQuery(
                        """
                    SELECT CAST(SUBSTRING(bs.updated_at, 6, 2) AS INT) AS month, COUNT(*) AS cnt
                    FROM book_status bs
                    WHERE bs.user_id = ? AND bs.status = 'FINISHED'
                      AND bs.updated_at >= ? AND bs.updated_at < ?
                    GROUP BY month
                    """,
                    ).bind(0, userId.toString())
                    .bind(1, "$year-01-01")
                    .bind(2, "${year + 1}-01-01")
                    .map { row ->
                        (row.getColumn("month", Int::class.java) ?: 0) to
                            (row.getColumn("cnt", Int::class.java) ?: 0)
                    }.associate { it }
            }

        val cumulativeExpected = if (goal > 0) goal.toDouble() / 12 else 0.0
        var cumulativeActual = 0

        return (1..upToMonth).map { m ->
            cumulativeActual += monthlyFinished[m] ?: 0
            MonthlyPacing(
                month = m,
                monthName = monthNames[m - 1],
                expected = (cumulativeExpected * m).toInt(),
                actual = cumulativeActual,
            )
        }
    }
}
