package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

data class AdvancedSearchRequest(
    val query: String? = null,
    val title: String? = null,
    val author: String? = null,
    val subtitle: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val series: String? = null,
    val language: String? = null,
    val tag: String? = null,
    val format: String? = null,
    val status: String? = null,
    val customField: String? = null,
    val customValue: String? = null,
    val contentQuery: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20,
)

data class SearchResultDto(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val isbn: String?,
    val series: String?,
    val language: String?,
    val format: String?,
    val matchField: String,
)

data class AdvancedSearchResult(
    val results: List<SearchResultDto>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
)

/**
 * Advanced search with per-field filters.
 * Searches across: title, author, subtitle, ISBN, publisher, series,
 * language, tags, format, reading status, custom metadata fields,
 * and full-text content (via FtsService).
 */
class AdvancedSearchService(
    private val jdbi: Jdbi,
    private val ftsService: FtsService?,
) {
    fun search(
        userId: UUID,
        request: AdvancedSearchRequest,
    ): AdvancedSearchResult {
        val uid = userId.toString()
        val safePage = request.page.coerceAtLeast(1)
        val safePageSize = request.pageSize.coerceIn(1, 100)
        val offset = (safePage - 1) * safePageSize

        val conditions = mutableListOf<String>()
        val bindings = mutableListOf<Any>()

        // Base: user's books
        conditions += "l.user_id = ?"
        bindings += uid

        // Per-field filters
        if (!request.query.isNullOrBlank()) {
            conditions += "(LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.description) LIKE ?)"
            val pattern = "%${request.query.lowercase()}%"
            bindings += pattern
            bindings += pattern
            bindings += pattern
        }
        if (!request.title.isNullOrBlank()) {
            conditions += "LOWER(b.title) LIKE ?"
            bindings += "%${request.title.lowercase()}%"
        }
        if (!request.author.isNullOrBlank()) {
            conditions += "LOWER(b.author) LIKE ?"
            bindings += "%${request.author.lowercase()}%"
        }
        if (!request.subtitle.isNullOrBlank()) {
            conditions += "LOWER(b.subtitle) LIKE ?"
            bindings += "%${request.subtitle.lowercase()}%"
        }
        if (!request.isbn.isNullOrBlank()) {
            conditions += "b.isbn = ?"
            bindings += request.isbn.trim()
        }
        if (!request.publisher.isNullOrBlank()) {
            conditions += "LOWER(b.publisher) LIKE ?"
            bindings += "%${request.publisher.lowercase()}%"
        }
        if (!request.series.isNullOrBlank()) {
            conditions += "LOWER(b.series) LIKE ?"
            bindings += "%${request.series.lowercase()}%"
        }
        if (!request.language.isNullOrBlank()) {
            conditions += "b.language = ?"
            bindings += request.language.trim().lowercase()
        }
        if (!request.format.isNullOrBlank()) {
            conditions += "b.book_format = ?"
            bindings += request.format.trim().uppercase()
        }

        // Tag filter via subquery
        val tagJoin =
            if (!request.tag.isNullOrBlank()) {
                conditions += "bt.tag = ?"
                bindings += request.tag.trim()
                "INNER JOIN book_tags bt ON bt.book_id = b.id AND bt.user_id = ?"
                    .also { bindings += uid }
            } else {
                ""
            }

        // Status filter via subquery
        val statusJoin =
            if (!request.status.isNullOrBlank()) {
                conditions += "bs.status = ?"
                bindings += request.status.trim().uppercase()
                "INNER JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?"
                    .also { bindings += uid }
            } else {
                ""
            }

        // Custom metadata field search
        val customJoin =
            if (!request.customField.isNullOrBlank() && !request.customValue.isNullOrBlank()) {
                conditions += "cf.field_name = ? AND LOWER(cf.field_value) LIKE ?"
                bindings += request.customField.trim()
                bindings += "%${request.customValue.lowercase()}%"
                "INNER JOIN book_custom_fields cf ON cf.book_id = b.id AND cf.user_id = ?"
                    .also { bindings += uid }
            } else {
                ""
            }

        // FTS content search (PostgreSQL only)
        var ftsBookIds = emptySet<String>()
        if (!request.contentQuery.isNullOrBlank() && ftsService?.isActive() == true) {
            ftsBookIds = ftsService.search(request.contentQuery).map { it.bookId }.toSet()
            if (ftsBookIds.isNotEmpty()) {
                conditions += "b.id IN (${ftsBookIds.indices.joinToString(",") { "?" }})"
                bindings.addAll(ftsBookIds.toList())
            } else {
                // No FTS matches — return empty
                return AdvancedSearchResult(emptyList(), 0, safePage, safePageSize)
            }
        }

        val whereClause = conditions.joinToString(" AND ")

        return jdbi.withHandle<AdvancedSearchResult, Exception> { h ->
            // Count
            val countQ =
                h.createQuery(
                    """
                SELECT COUNT(DISTINCT b.id)
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                $tagJoin $statusJoin $customJoin
                WHERE $whereClause
                """,
                )
            bindings.forEachIndexed { i, v ->
                when (v) {
                    is String -> countQ.bind(i, v)
                    is Int -> countQ.bind(i, v)
                    else -> countQ.bind(i, v.toString())
                }
            }
            val totalCount = countQ.mapTo(Int::class.javaObjectType).one()

            // Results
            val q =
                h.createQuery(
                    """
                SELECT DISTINCT b.id, b.title, b.author, b.cover_url, b.isbn, b.series, b.language, b.book_format
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                $tagJoin $statusJoin $customJoin
                WHERE $whereClause
                ORDER BY b.title
                LIMIT ? OFFSET ?
                """,
                )
            bindings.forEachIndexed { i, v ->
                when (v) {
                    is String -> q.bind(i, v)
                    is Int -> q.bind(i, v)
                    else -> q.bind(i, v.toString())
                }
            }
            q.bind(bindings.size, safePageSize)
            q.bind(bindings.size + 1, offset)

            val matchField =
                when {
                    !request.contentQuery.isNullOrBlank() -> "content"
                    !request.title.isNullOrBlank() -> "title"
                    !request.author.isNullOrBlank() -> "author"
                    !request.isbn.isNullOrBlank() -> "isbn"
                    !request.customField.isNullOrBlank() -> "custom:${request.customField}"
                    else -> "all"
                }

            val results =
                q
                    .map { row ->
                        SearchResultDto(
                            bookId = row.getColumn("id", String::class.java) ?: "",
                            title = row.getColumn("title", String::class.java) ?: "",
                            author = row.getColumn("author", String::class.java),
                            coverUrl = row.getColumn("cover_url", String::class.java),
                            isbn = row.getColumn("isbn", String::class.java),
                            series = row.getColumn("series", String::class.java),
                            language = row.getColumn("language", String::class.java),
                            format = row.getColumn("book_format", String::class.java),
                            matchField = matchField,
                        )
                    }.list()

            AdvancedSearchResult(results, totalCount, safePage, safePageSize)
        }
    }
}
