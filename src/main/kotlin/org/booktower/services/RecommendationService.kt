package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID

private val recLogger = LoggerFactory.getLogger("booktower.RecommendationService")

/**
 * Returns similar books from the user's own library based on:
 *  1. Same author (highest priority)
 *  2. Same series
 *  3. Shared tags
 *
 * The source book is excluded from all results.
 * All queries are scoped to books owned by the requesting user.
 */
class RecommendationService(
    private val jdbi: Jdbi,
) {
    companion object {
        private const val MAX_PER_REASON = 5
        private const val MAX_TOTAL = 10
    }

    /**
     * Returns up to [MAX_TOTAL] similar books for the given book.
     * Returns an empty list on any error or if the book is not found.
     */
    fun findSimilar(
        userId: UUID,
        bookId: UUID,
    ): List<SimilarBook> {
        return try {
            val source = fetchSource(userId, bookId) ?: return emptyList()
            val results = mutableListOf<SimilarBook>()
            val seen = mutableSetOf(bookId.toString())

            // 1. Same author
            if (!source.author.isNullOrBlank()) {
                byAuthor(userId, source.author, seen).forEach {
                    results += it
                    seen += it.bookId
                }
            }

            // 2. Same series
            if (!source.series.isNullOrBlank() && results.size < MAX_TOTAL) {
                bySeries(userId, source.series, seen).forEach {
                    results += it
                    seen += it.bookId
                }
            }

            // 3. Shared tags
            if (results.size < MAX_TOTAL) {
                val tags = fetchTags(userId, bookId)
                if (tags.isNotEmpty()) {
                    byTags(userId, tags, seen).forEach {
                        if (results.size < MAX_TOTAL) {
                            results += it
                            seen += it.bookId
                        }
                    }
                }
            }

            results.take(MAX_TOTAL)
        } catch (e: Exception) {
            recLogger.warn("findSimilar failed for book $bookId: ${e.message}")
            emptyList()
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private data class SourceInfo(
        val author: String?,
        val series: String?,
    )

    private fun fetchSource(
        userId: UUID,
        bookId: UUID,
    ): SourceInfo? =
        jdbi.withHandle<SourceInfo?, Exception> { h ->
            h
                .createQuery(
                    """SELECT b.author, b.series FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   WHERE b.id = ? AND l.user_id = ?""",
                ).bind(0, bookId.toString())
                .bind(1, userId.toString())
                .map { row, _ -> SourceInfo(row.getString("author"), row.getString("series")) }
                .findFirst()
                .orElse(null)
        }

    private fun byAuthor(
        userId: UUID,
        author: String,
        exclude: Set<String>,
    ): List<SimilarBook> {
        val excludeList = exclude.toList()
        val placeholders = excludeList.joinToString(",") { "?" }
        return jdbi.withHandle<List<SimilarBook>, Exception> { h ->
            val q =
                h
                    .createQuery(
                        """SELECT b.id, b.title, b.author, b.cover_path FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   WHERE l.user_id = ? AND b.author = ? AND b.id NOT IN ($placeholders)
                   ORDER BY b.title LIMIT $MAX_PER_REASON""",
                    ).bind(0, userId.toString())
                    .bind(1, author)
            excludeList.forEachIndexed { i, id -> q.bind(i + 2, id) }
            q
                .map { row, _ ->
                    SimilarBook(
                        row.getString("id"),
                        row.getString("title"),
                        row.getString("author"),
                        row.getString("cover_path"),
                        "author",
                    )
                }.list()
        }
    }

    private fun bySeries(
        userId: UUID,
        series: String,
        exclude: Set<String>,
    ): List<SimilarBook> {
        val excludeList = exclude.toList()
        val placeholders = excludeList.joinToString(",") { "?" }
        return jdbi.withHandle<List<SimilarBook>, Exception> { h ->
            val q =
                h
                    .createQuery(
                        """SELECT b.id, b.title, b.author, b.cover_path FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   WHERE l.user_id = ? AND b.series = ? AND b.id NOT IN ($placeholders)
                   ORDER BY b.series_index, b.title LIMIT $MAX_PER_REASON""",
                    ).bind(0, userId.toString())
                    .bind(1, series)
            excludeList.forEachIndexed { i, id -> q.bind(i + 2, id) }
            q
                .map { row, _ ->
                    SimilarBook(
                        row.getString("id"),
                        row.getString("title"),
                        row.getString("author"),
                        row.getString("cover_path"),
                        "series",
                    )
                }.list()
        }
    }

    private fun fetchTags(
        userId: UUID,
        bookId: UUID,
    ): List<String> =
        jdbi.withHandle<List<String>, Exception> { h ->
            h
                .createQuery("SELECT tag FROM book_tags WHERE book_id = ? AND user_id = ?")
                .bind(0, bookId.toString())
                .bind(1, userId.toString())
                .mapTo(String::class.java)
                .list()
        }

    private fun byTags(
        userId: UUID,
        tags: List<String>,
        exclude: Set<String>,
    ): List<SimilarBook> {
        val excludeList = exclude.toList()
        val tagPlaceholders = tags.joinToString(",") { "?" }
        val excludePlaceholders = excludeList.joinToString(",") { "?" }
        return jdbi.withHandle<List<SimilarBook>, Exception> { h ->
            val q =
                h
                    .createQuery(
                        """SELECT b.id, b.title, b.author, b.cover_path, COUNT(*) AS shared_tags FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   JOIN book_tags bt ON bt.book_id = b.id AND bt.user_id = ?
                   WHERE l.user_id = ? AND bt.tag IN ($tagPlaceholders) AND b.id NOT IN ($excludePlaceholders)
                   GROUP BY b.id, b.title, b.author, b.cover_path
                   ORDER BY shared_tags DESC, b.title LIMIT $MAX_PER_REASON""",
                    ).bind(0, userId.toString())
                    .bind(1, userId.toString())
            var idx = 2
            tags.forEach { tag -> q.bind(idx++, tag) }
            excludeList.forEach { id -> q.bind(idx++, id) }
            q
                .map { row, _ ->
                    SimilarBook(
                        row.getString("id"),
                        row.getString("title"),
                        row.getString("author"),
                        row.getString("cover_path"),
                        "tags",
                    )
                }.list()
        }
    }
}

data class SimilarBook(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val reason: String,
)

data class DiscoveryRecommendation(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val reason: String,
    val score: Double,
)

/**
 * Broader "discover books you might like" recommendations based on
 * the user's reading patterns — highly-rated books, preferred categories/tags,
 * unfinished series, and authors with unread books.
 */
class DiscoveryService(
    private val jdbi: Jdbi,
) {
    companion object {
        private const val MAX_RESULTS = 20
    }

    /**
     * Returns personalized book recommendations from the user's own library.
     * Prioritizes: unread books by favorite authors > unfinished series >
     * books matching top-rated categories > unread books by prolific authors.
     */
    fun discover(userId: UUID): List<DiscoveryRecommendation> {
        val uid = userId.toString()
        val results = mutableListOf<DiscoveryRecommendation>()
        val seen = mutableSetOf<String>()

        // 1. Unfinished series: books in series where user has read some but not all
        results += unfinishedSeries(uid, seen)

        // 2. More from favorite authors: authors of 4-5 star rated books with unread titles
        if (results.size < MAX_RESULTS) {
            results += favoriteAuthorUnread(uid, seen)
        }

        // 3. Books matching top tags: tags the user uses most, applied to unread books
        if (results.size < MAX_RESULTS) {
            results += topTagUnread(uid, seen)
        }

        // 4. Highly rated unread: books with high community ratings that user hasn't read
        if (results.size < MAX_RESULTS) {
            results += highRatedUnread(uid, seen)
        }

        return results.take(MAX_RESULTS)
    }

    private fun unfinishedSeries(
        uid: String,
        seen: MutableSet<String>,
    ): List<DiscoveryRecommendation> =
        jdbi.withHandle<List<DiscoveryRecommendation>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT b.id, b.title, b.author, b.cover_path, b.series, b.series_index
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                WHERE l.user_id = ?
                  AND b.series IS NOT NULL AND b.series <> ''
                  AND (bs.status IS NULL OR bs.status NOT IN ('FINISHED', 'DID_NOT_FINISH'))
                  AND b.series IN (
                      SELECT DISTINCT b2.series FROM books b2
                      INNER JOIN libraries l2 ON b2.library_id = l2.id
                      INNER JOIN book_status bs2 ON bs2.book_id = b2.id AND bs2.user_id = ?
                      WHERE l2.user_id = ? AND bs2.status = 'FINISHED' AND b2.series IS NOT NULL
                  )
                ORDER BY b.series, b.series_index
                LIMIT 5
                """,
                ).bind(0, uid)
                .bind(1, uid)
                .bind(2, uid)
                .bind(3, uid)
                .map { row ->
                    val id = row.getColumn("id", String::class.java) ?: ""
                    if (id in seen) return@map null
                    seen += id
                    DiscoveryRecommendation(
                        bookId = id,
                        title = row.getColumn("title", String::class.java) ?: "",
                        author = row.getColumn("author", String::class.java),
                        coverUrl = row.getColumn("cover_path", String::class.java),
                        reason = "Next in series: ${row.getColumn("series", String::class.java) ?: ""}",
                        score = 0.9,
                    )
                }.list()
                .filterNotNull()
        }

    private fun favoriteAuthorUnread(
        uid: String,
        seen: MutableSet<String>,
    ): List<DiscoveryRecommendation> =
        jdbi.withHandle<List<DiscoveryRecommendation>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT b.id, b.title, b.author, b.cover_path
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                WHERE l.user_id = ?
                  AND (bs.status IS NULL OR bs.status NOT IN ('FINISHED', 'DID_NOT_FINISH'))
                  AND b.author IN (
                      SELECT DISTINCT b2.author FROM books b2
                      INNER JOIN libraries l2 ON b2.library_id = l2.id
                      INNER JOIN book_ratings br ON br.book_id = b2.id AND br.user_id = ?
                      WHERE l2.user_id = ? AND br.rating >= 4 AND b2.author IS NOT NULL
                  )
                ORDER BY b.author, b.title
                LIMIT 5
                """,
                ).bind(0, uid)
                .bind(1, uid)
                .bind(2, uid)
                .bind(3, uid)
                .map { row ->
                    val id = row.getColumn("id", String::class.java) ?: ""
                    if (id in seen) return@map null
                    seen += id
                    DiscoveryRecommendation(
                        bookId = id,
                        title = row.getColumn("title", String::class.java) ?: "",
                        author = row.getColumn("author", String::class.java),
                        coverUrl = row.getColumn("cover_path", String::class.java),
                        reason = "More from ${row.getColumn("author", String::class.java) ?: "favorite author"}",
                        score = 0.8,
                    )
                }.list()
                .filterNotNull()
        }

    private fun topTagUnread(
        uid: String,
        seen: MutableSet<String>,
    ): List<DiscoveryRecommendation> =
        jdbi.withHandle<List<DiscoveryRecommendation>, Exception> { h ->
            // Find user's top 3 tags by usage
            val topTags =
                h
                    .createQuery("SELECT tag, COUNT(*) AS cnt FROM book_tags WHERE user_id = ? GROUP BY tag ORDER BY cnt DESC LIMIT 3")
                    .bind(0, uid)
                    .mapTo(String::class.java)
                    .list()
            if (topTags.isEmpty()) return@withHandle emptyList()

            val tagPlaceholders = topTags.indices.joinToString(",") { "?" }
            val q =
                h.createQuery(
                    """
                SELECT DISTINCT b.id, b.title, b.author, b.cover_path
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                INNER JOIN book_tags bt ON bt.book_id = b.id AND bt.user_id = ?
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                WHERE l.user_id = ?
                  AND bt.tag IN ($tagPlaceholders)
                  AND (bs.status IS NULL OR bs.status NOT IN ('FINISHED', 'DID_NOT_FINISH'))
                ORDER BY b.title
                LIMIT 5
                """,
                )
            q.bind(0, uid).bind(1, uid).bind(2, uid)
            topTags.forEachIndexed { i, tag -> q.bind(i + 3, tag) }
            q
                .map { row ->
                    val id = row.getColumn("id", String::class.java) ?: ""
                    if (id in seen) return@map null
                    seen += id
                    DiscoveryRecommendation(
                        bookId = id,
                        title = row.getColumn("title", String::class.java) ?: "",
                        author = row.getColumn("author", String::class.java),
                        coverUrl = row.getColumn("cover_path", String::class.java),
                        reason = "Matches your interests",
                        score = 0.7,
                    )
                }.list()
                .filterNotNull()
        }

    private fun highRatedUnread(
        uid: String,
        seen: MutableSet<String>,
    ): List<DiscoveryRecommendation> =
        jdbi.withHandle<List<DiscoveryRecommendation>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT b.id, b.title, b.author, b.cover_path, b.community_rating
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                LEFT JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                WHERE l.user_id = ?
                  AND b.community_rating IS NOT NULL AND b.community_rating >= 4.0
                  AND (bs.status IS NULL OR bs.status NOT IN ('FINISHED', 'DID_NOT_FINISH'))
                ORDER BY b.community_rating DESC, b.title
                LIMIT 5
                """,
                ).bind(0, uid)
                .bind(1, uid)
                .map { row ->
                    val id = row.getColumn("id", String::class.java) ?: ""
                    if (id in seen) return@map null
                    seen += id
                    val rating = row.getColumn("community_rating", java.lang.Float::class.java)?.toFloat() ?: 0f
                    DiscoveryRecommendation(
                        bookId = id,
                        title = row.getColumn("title", String::class.java) ?: "",
                        author = row.getColumn("author", String::class.java),
                        coverUrl = row.getColumn("cover_path", String::class.java),
                        reason = "Highly rated (%.1f)".format(rating),
                        score = 0.6,
                    )
                }.list()
                .filterNotNull()
        }
}
