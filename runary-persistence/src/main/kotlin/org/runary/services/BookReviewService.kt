package org.runary.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

data class BookReviewDto(
    val id: String,
    val bookId: String,
    val userId: String,
    val rating: Int?,
    val title: String?,
    val body: String,
    val spoiler: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateReviewRequest(
    val rating: Int? = null,
    val title: String? = null,
    val body: String,
    val spoiler: Boolean = false,
)

data class UpdateReviewRequest(
    val rating: Int? = null,
    val title: String? = null,
    val body: String? = null,
    val spoiler: Boolean? = null,
)

class BookReviewService(
    private val jdbi: Jdbi,
) {
    fun listForBook(bookId: String): List<BookReviewDto> =
        jdbi.withHandle<List<BookReviewDto>, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, book_id, user_id, rating, title, body, spoiler, created_at, updated_at FROM book_reviews WHERE book_id = ? ORDER BY created_at DESC",
                ).bind(0, bookId)
                .map { row -> mapRow(row) }
                .list()
        }

    fun getForUser(
        bookId: String,
        userId: UUID,
    ): BookReviewDto? =
        jdbi.withHandle<BookReviewDto?, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, book_id, user_id, rating, title, body, spoiler, created_at, updated_at FROM book_reviews WHERE book_id = ? AND user_id = ?",
                ).bind(0, bookId)
                .bind(1, userId.toString())
                .map { row -> mapRow(row) }
                .firstOrNull()
        }

    fun create(
        bookId: String,
        userId: UUID,
        request: CreateReviewRequest,
    ): BookReviewDto {
        require(request.body.isNotBlank()) { "Review body must not be blank" }
        request.rating?.let { require(it in 1..5) { "Rating must be between 1 and 5" } }

        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO book_reviews (id, book_id, user_id, rating, title, body, spoiler, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).bind(0, id)
                .bind(1, bookId)
                .bind(2, userId.toString())
                .bind(3, request.rating)
                .bind(4, request.title)
                .bind(5, request.body)
                .bind(6, request.spoiler)
                .bind(7, now)
                .bind(8, now)
                .execute()
        }
        return BookReviewDto(
            id = id,
            bookId = bookId,
            userId = userId.toString(),
            rating = request.rating,
            title = request.title,
            body = request.body,
            spoiler = request.spoiler,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        bookId: String,
        reviewId: String,
        userId: UUID,
        request: UpdateReviewRequest,
    ): BookReviewDto? {
        val existing =
            jdbi.withHandle<BookReviewDto?, Exception> { h ->
                h
                    .createQuery(
                        "SELECT id, book_id, user_id, rating, title, body, spoiler, created_at, updated_at FROM book_reviews WHERE id = ? AND book_id = ? AND user_id = ?",
                    ).bind(0, reviewId)
                    .bind(1, bookId)
                    .bind(2, userId.toString())
                    .map { row -> mapRow(row) }
                    .firstOrNull()
            } ?: return null

        request.rating?.let { require(it in 1..5) { "Rating must be between 1 and 5" } }
        request.body?.let { require(it.isNotBlank()) { "Review body must not be blank" } }

        val now = Instant.now().toString()
        val sets = mutableListOf<String>()
        val bindings = mutableListOf<Any?>()
        request.rating?.let {
            sets += "rating = ?"
            bindings += it
        } ?: run {
            sets += "rating = ?"
            bindings += existing.rating
        }
        request.title?.let {
            sets += "title = ?"
            bindings += it
        }
        request.body?.let {
            sets += "body = ?"
            bindings += it
        }
        request.spoiler?.let {
            sets += "spoiler = ?"
            bindings += it
        }
        sets += "updated_at = ?"
        bindings += now
        bindings += reviewId

        jdbi.useHandle<Exception> { h ->
            val stmt = h.createUpdate("UPDATE book_reviews SET ${sets.joinToString(", ")} WHERE id = ?")
            bindings.forEachIndexed { idx, v -> stmt.bind(idx, v) }
            stmt.execute()
        }
        return jdbi.withHandle<BookReviewDto?, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, book_id, user_id, rating, title, body, spoiler, created_at, updated_at FROM book_reviews WHERE id = ?",
                ).bind(0, reviewId)
                .map { row -> mapRow(row) }
                .firstOrNull()
        }
    }

    fun delete(
        bookId: String,
        reviewId: String,
        userId: UUID,
    ): Boolean {
        val rows =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM book_reviews WHERE id = ? AND book_id = ? AND user_id = ?")
                    .bind(0, reviewId)
                    .bind(1, bookId)
                    .bind(2, userId.toString())
                    .execute()
            }
        return rows > 0
    }

    private fun mapRow(row: org.jdbi.v3.core.result.RowView) =
        BookReviewDto(
            id = row.getColumn("id", String::class.java),
            bookId = row.getColumn("book_id", String::class.java),
            userId = row.getColumn("user_id", String::class.java),
            rating = row.getColumn("rating", java.lang.Integer::class.java) as? Int,
            title = row.getColumn("title", String::class.java),
            body = row.getColumn("body", String::class.java),
            spoiler = row.getColumn("spoiler", java.lang.Boolean::class.java) == true,
            createdAt = row.getColumn("created_at", String::class.java),
            updatedAt = row.getColumn("updated_at", String::class.java),
        )
}
