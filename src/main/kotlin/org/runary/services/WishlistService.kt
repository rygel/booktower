package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val wlLog = LoggerFactory.getLogger("runary.WishlistService")

data class WishlistItemDto(
    val id: String,
    val title: String,
    val author: String?,
    val isbn: String?,
    val coverUrl: String?,
    val description: String?,
    val source: String?,
    val sourceUrl: String?,
    val notes: String?,
    val priority: Int,
    val addedAt: String,
)

data class CreateWishlistItemRequest(
    val title: String,
    val author: String? = null,
    val isbn: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val source: String? = null,
    val sourceUrl: String? = null,
    val notes: String? = null,
    val priority: Int = 0,
)

/**
 * Want-to-read wishlist for books the user doesn't own yet.
 * Items are metadata-only — no file, no library association.
 * Users can add items manually or from metadata search results.
 */
class WishlistService(
    private val jdbi: Jdbi,
) {
    fun getItems(userId: UUID): List<WishlistItemDto> =
        jdbi.withHandle<List<WishlistItemDto>, Exception> { h ->
            h
                .createQuery("SELECT * FROM wishlist WHERE user_id = ? ORDER BY priority DESC, added_at DESC")
                .bind(0, userId.toString())
                .map { row -> mapItem(row) }
                .list()
        }

    fun addItem(
        userId: UUID,
        request: CreateWishlistItemRequest,
    ): WishlistItemDto {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """
                INSERT INTO wishlist (id, user_id, title, author, isbn, cover_url, description, source, source_url, notes, priority, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, request.title.trim().take(255))
                .bind(3, request.author?.take(255))
                .bind(4, request.isbn?.take(20))
                .bind(5, request.coverUrl?.take(500))
                .bind(6, request.description?.take(5000))
                .bind(7, request.source?.take(50))
                .bind(8, request.sourceUrl?.take(500))
                .bind(9, request.notes?.take(2000))
                .bind(10, request.priority.coerceIn(0, 10))
                .bind(11, now)
                .execute()
        }
        wlLog.info("Wishlist item added: ${request.title}")
        return WishlistItemDto(
            id,
            request.title.trim(),
            request.author,
            request.isbn,
            request.coverUrl,
            request.description,
            request.source,
            request.sourceUrl,
            request.notes,
            request.priority,
            now,
        )
    }

    fun updateItem(
        userId: UUID,
        itemId: String,
        request: CreateWishlistItemRequest,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate(
                        """
                    UPDATE wishlist SET title = ?, author = ?, isbn = ?, cover_url = ?,
                        description = ?, source = ?, source_url = ?, notes = ?, priority = ?
                    WHERE id = ? AND user_id = ?
                    """,
                    ).bind(0, request.title.trim().take(255))
                    .bind(1, request.author?.take(255))
                    .bind(2, request.isbn?.take(20))
                    .bind(3, request.coverUrl?.take(500))
                    .bind(4, request.description?.take(5000))
                    .bind(5, request.source?.take(50))
                    .bind(6, request.sourceUrl?.take(500))
                    .bind(7, request.notes?.take(2000))
                    .bind(8, request.priority.coerceIn(0, 10))
                    .bind(9, itemId)
                    .bind(10, userId.toString())
                    .execute()
            }
        return updated > 0
    }

    fun deleteItem(
        userId: UUID,
        itemId: String,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM wishlist WHERE id = ? AND user_id = ?")
                    .bind(0, itemId)
                    .bind(1, userId.toString())
                    .execute()
            }
        return deleted > 0
    }

    private fun mapItem(row: org.jdbi.v3.core.result.RowView): WishlistItemDto =
        WishlistItemDto(
            id = row.getColumn("id", String::class.java) ?: "",
            title = row.getColumn("title", String::class.java) ?: "",
            author = row.getColumn("author", String::class.java),
            isbn = row.getColumn("isbn", String::class.java),
            coverUrl = row.getColumn("cover_url", String::class.java),
            description = row.getColumn("description", String::class.java),
            source = row.getColumn("source", String::class.java),
            sourceUrl = row.getColumn("source_url", String::class.java),
            notes = row.getColumn("notes", String::class.java),
            priority = row.getColumn("priority", Int::class.javaObjectType) ?: 0,
            addedAt = row.getColumn("added_at", String::class.java) ?: "",
        )
}
