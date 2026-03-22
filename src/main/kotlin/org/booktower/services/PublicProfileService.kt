package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

data class PublicProfile(
    val username: String,
    val memberSince: String,
    val booksFinished: Int,
    val currentlyReading: List<PublicBookEntry>,
    val recentlyFinished: List<PublicBookEntry>,
    val favoriteAuthors: List<String>,
    val topTags: List<String>,
    val readingGoal: PublicReadingGoal?,
)

data class PublicBookEntry(
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val finishedAt: String?,
)

data class PublicReadingGoal(
    val year: Int,
    val goal: Int,
    val booksFinished: Int,
    val progressPercent: Int,
)

/**
 * Generates a public reading activity profile for a user.
 * Only returns data if the user has opted in via `profile.public` setting.
 * Exposes metadata only — never book content, file paths, or private notes.
 */
class PublicProfileService(
    private val jdbi: Jdbi,
    private val userSettingsService: UserSettingsService,
) {
    /**
     * Returns the public profile for [username], or null if the user
     * doesn't exist or hasn't enabled public profile.
     */
    fun getProfile(username: String): PublicProfile? {
        val user =
            jdbi.withHandle<Map<String, Any?>?, Exception> { h ->
                h
                    .createQuery("SELECT id, username, created_at FROM users WHERE username = ?")
                    .bind(0, username)
                    .mapToMap()
                    .firstOrNull()
            } ?: return null

        val userId = UUID.fromString(user["id"] as String)

        // Check opt-in
        val isPublic = userSettingsService.get(userId, "profile.public")
        if (isPublic != "true") return null

        val uid = userId.toString()

        return jdbi.withHandle<PublicProfile, Exception> { h ->
            val booksFinished =
                h
                    .createQuery("SELECT COUNT(*) FROM book_status WHERE user_id = ? AND status = 'FINISHED'")
                    .bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            val currentlyReading =
                h
                    .createQuery(
                        """
                    SELECT b.title, b.author, b.cover_url
                    FROM book_status bs
                    INNER JOIN books b ON bs.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE bs.user_id = ? AND l.user_id = ? AND bs.status = 'READING'
                    ORDER BY bs.updated_at DESC LIMIT 5
                    """,
                    ).bind(0, uid)
                    .bind(1, uid)
                    .map { row ->
                        PublicBookEntry(
                            title = row.getColumn("title", String::class.java) ?: "",
                            author = row.getColumn("author", String::class.java),
                            coverUrl = row.getColumn("cover_url", String::class.java),
                            finishedAt = null,
                        )
                    }.list()

            val recentlyFinished =
                h
                    .createQuery(
                        """
                    SELECT b.title, b.author, b.cover_url, bs.updated_at AS finished_at
                    FROM book_status bs
                    INNER JOIN books b ON bs.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE bs.user_id = ? AND l.user_id = ? AND bs.status = 'FINISHED'
                    ORDER BY bs.updated_at DESC LIMIT 10
                    """,
                    ).bind(0, uid)
                    .bind(1, uid)
                    .map { row ->
                        PublicBookEntry(
                            title = row.getColumn("title", String::class.java) ?: "",
                            author = row.getColumn("author", String::class.java),
                            coverUrl = row.getColumn("cover_url", String::class.java),
                            finishedAt = row.getColumn("finished_at", String::class.java),
                        )
                    }.list()

            val favoriteAuthors =
                h
                    .createQuery(
                        """
                    SELECT b.author, COUNT(*) AS cnt
                    FROM book_status bs
                    INNER JOIN books b ON bs.book_id = b.id
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE bs.user_id = ? AND l.user_id = ? AND bs.status = 'FINISHED'
                      AND b.author IS NOT NULL AND b.author <> ''
                    GROUP BY b.author ORDER BY cnt DESC LIMIT 5
                    """,
                    ).bind(0, uid)
                    .bind(1, uid)
                    .mapTo(String::class.java)
                    .list()

            val topTags =
                h
                    .createQuery(
                        "SELECT tag, COUNT(*) AS cnt FROM book_tags WHERE user_id = ? GROUP BY tag ORDER BY cnt DESC LIMIT 5",
                    ).bind(0, uid)
                    .mapTo(String::class.java)
                    .list()

            // Reading goal for current year
            val year =
                java.time.LocalDate
                    .now()
                    .year
            val goalStr = userSettingsService.get(userId, "reading.goal.$year")
            val goal = goalStr?.toIntOrNull() ?: 0
            val readingGoal =
                if (goal > 0) {
                    val finished =
                        h
                            .createQuery(
                                """
                        SELECT COUNT(*) FROM book_status
                        WHERE user_id = ? AND status = 'FINISHED'
                          AND updated_at >= ? AND updated_at < ?
                        """,
                            ).bind(0, uid)
                            .bind(1, "$year-01-01")
                            .bind(2, "${year + 1}-01-01")
                            .mapTo(Int::class.javaObjectType)
                            .one()
                    PublicReadingGoal(year, goal, finished, ((finished * 100.0) / goal).toInt().coerceAtMost(100))
                } else {
                    null
                }

            PublicProfile(
                username = user["username"] as? String ?: "",
                memberSince = (user["created_at"]?.toString() ?: "").take(10),
                booksFinished = booksFinished,
                currentlyReading = currentlyReading,
                recentlyFinished = recentlyFinished,
                favoriteAuthors = favoriteAuthors,
                topTags = topTags,
                readingGoal = readingGoal,
            )
        }
    }

    /** Enable or disable public profile for a user. */
    fun setPublic(
        userId: UUID,
        enabled: Boolean,
    ) {
        userSettingsService.set(userId, "profile.public", if (enabled) "true" else "false")
    }

    fun isPublic(userId: UUID): Boolean = userSettingsService.get(userId, "profile.public") == "true"
}
