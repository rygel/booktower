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
class RecommendationService(private val jdbi: Jdbi) {

    companion object {
        private const val MAX_PER_REASON = 5
        private const val MAX_TOTAL = 10
    }

    /**
     * Returns up to [MAX_TOTAL] similar books for the given book.
     * Returns an empty list on any error or if the book is not found.
     */
    fun findSimilar(userId: UUID, bookId: UUID): List<SimilarBook> {
        return try {
            val source = fetchSource(userId, bookId) ?: return emptyList()
            val results = mutableListOf<SimilarBook>()
            val seen = mutableSetOf(bookId.toString())

            // 1. Same author
            if (!source.author.isNullOrBlank()) {
                byAuthor(userId, source.author, seen).forEach {
                    results += it; seen += it.bookId
                }
            }

            // 2. Same series
            if (!source.series.isNullOrBlank() && results.size < MAX_TOTAL) {
                bySeries(userId, source.series, seen).forEach {
                    results += it; seen += it.bookId
                }
            }

            // 3. Shared tags
            if (results.size < MAX_TOTAL) {
                val tags = fetchTags(userId, bookId)
                if (tags.isNotEmpty()) {
                    byTags(userId, tags, seen).forEach {
                        if (results.size < MAX_TOTAL) { results += it; seen += it.bookId }
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

    private data class SourceInfo(val author: String?, val series: String?)

    private fun fetchSource(userId: UUID, bookId: UUID): SourceInfo? =
        jdbi.withHandle<SourceInfo?, Exception> { h ->
            h.createQuery(
                """SELECT b.author, b.series FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   WHERE b.id = ? AND l.user_id = ?""",
            )
                .bind(0, bookId.toString())
                .bind(1, userId.toString())
                .map { row, _ -> SourceInfo(row.getString("author"), row.getString("series")) }
                .findFirst().orElse(null)
        }

    private fun byAuthor(userId: UUID, author: String, exclude: Set<String>): List<SimilarBook> {
        val excludeList = exclude.toList()
        val placeholders = excludeList.joinToString(",") { "?" }
        return jdbi.withHandle<List<SimilarBook>, Exception> { h ->
            val q = h.createQuery(
                """SELECT b.id, b.title, b.author, b.cover_path FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   WHERE l.user_id = ? AND b.author = ? AND b.id NOT IN ($placeholders)
                   ORDER BY b.title LIMIT $MAX_PER_REASON""",
            )
                .bind(0, userId.toString())
                .bind(1, author)
            excludeList.forEachIndexed { i, id -> q.bind(i + 2, id) }
            q.map { row, _ ->
                SimilarBook(row.getString("id"), row.getString("title"),
                    row.getString("author"), row.getString("cover_path"), "author")
            }.list()
        }
    }

    private fun bySeries(userId: UUID, series: String, exclude: Set<String>): List<SimilarBook> {
        val excludeList = exclude.toList()
        val placeholders = excludeList.joinToString(",") { "?" }
        return jdbi.withHandle<List<SimilarBook>, Exception> { h ->
            val q = h.createQuery(
                """SELECT b.id, b.title, b.author, b.cover_path FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   WHERE l.user_id = ? AND b.series = ? AND b.id NOT IN ($placeholders)
                   ORDER BY b.series_index, b.title LIMIT $MAX_PER_REASON""",
            )
                .bind(0, userId.toString())
                .bind(1, series)
            excludeList.forEachIndexed { i, id -> q.bind(i + 2, id) }
            q.map { row, _ ->
                SimilarBook(row.getString("id"), row.getString("title"),
                    row.getString("author"), row.getString("cover_path"), "series")
            }.list()
        }
    }

    private fun fetchTags(userId: UUID, bookId: UUID): List<String> =
        jdbi.withHandle<List<String>, Exception> { h ->
            h.createQuery("SELECT tag FROM book_tags WHERE book_id = ? AND user_id = ?")
                .bind(0, bookId.toString())
                .bind(1, userId.toString())
                .mapTo(String::class.java)
                .list()
        }

    private fun byTags(userId: UUID, tags: List<String>, exclude: Set<String>): List<SimilarBook> {
        val excludeList = exclude.toList()
        val tagPlaceholders = tags.joinToString(",") { "?" }
        val excludePlaceholders = excludeList.joinToString(",") { "?" }
        return jdbi.withHandle<List<SimilarBook>, Exception> { h ->
            val q = h.createQuery(
                """SELECT b.id, b.title, b.author, b.cover_path, COUNT(*) AS shared_tags FROM books b
                   JOIN libraries l ON b.library_id = l.id
                   JOIN book_tags bt ON bt.book_id = b.id AND bt.user_id = ?
                   WHERE l.user_id = ? AND bt.tag IN ($tagPlaceholders) AND b.id NOT IN ($excludePlaceholders)
                   GROUP BY b.id, b.title, b.author, b.cover_path
                   ORDER BY shared_tags DESC, b.title LIMIT $MAX_PER_REASON""",
            )
                .bind(0, userId.toString())
                .bind(1, userId.toString())
            var idx = 2
            tags.forEach { tag -> q.bind(idx++, tag) }
            excludeList.forEach { id -> q.bind(idx++, id) }
            q.map { row, _ ->
                SimilarBook(row.getString("id"), row.getString("title"),
                    row.getString("author"), row.getString("cover_path"), "tags")
            }.list()
        }
    }
}

data class SimilarBook(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val reason: String,   // "author" | "series" | "tags"
)
