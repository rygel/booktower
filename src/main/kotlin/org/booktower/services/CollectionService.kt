package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.CollectionService")

data class CollectionDto(
    val id: String,
    val name: String,
    val description: String?,
    val bookCount: Int,
    val createdAt: String,
)

data class CreateCollectionRequest(
    val name: String,
    val description: String? = null,
)

class CollectionService(
    private val jdbi: Jdbi,
) {
    fun getCollections(userId: UUID): List<CollectionDto> =
        jdbi.withHandle<List<CollectionDto>, Exception> { h ->
            h
                .createQuery(
                    """SELECT c.id, c.name, c.description, c.created_at,
                              COALESCE(cb.cnt, 0) AS book_count
                       FROM collections c
                       LEFT JOIN (SELECT collection_id, COUNT(*) AS cnt FROM collection_books GROUP BY collection_id) cb
                         ON cb.collection_id = c.id
                       WHERE c.user_id = ?
                       ORDER BY c.name""",
                ).bind(0, userId.toString())
                .map { row ->
                    CollectionDto(
                        id = row.getColumn("id", String::class.java),
                        name = row.getColumn("name", String::class.java),
                        description = row.getColumn("description", String::class.java),
                        bookCount = row.getColumn("book_count", java.lang.Integer::class.java)?.toInt() ?: 0,
                        createdAt = row.getColumn("created_at", String::class.java),
                    )
                }.list()
        }

    fun createCollection(
        userId: UUID,
        request: CreateCollectionRequest,
    ): CollectionDto {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO collections (id, user_id, name, description, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, request.name.trim())
                .bind(3, request.description?.trim())
                .bind(4, now)
                .bind(5, now)
                .execute()
        }
        logger.info("Collection created: {} for user {}", request.name, userId)
        return CollectionDto(id, request.name.trim(), request.description?.trim(), 0, now)
    }

    fun deleteCollection(
        userId: UUID,
        collectionId: UUID,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM collections WHERE id = ? AND user_id = ?")
                    .bind(0, collectionId.toString())
                    .bind(1, userId.toString())
                    .execute()
            }
        return deleted > 0
    }

    fun addBook(
        userId: UUID,
        collectionId: UUID,
        bookId: UUID,
    ): Boolean {
        // Verify ownership
        val owns =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM collections WHERE id = ? AND user_id = ?")
                    .bind(0, collectionId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .firstOrNull()!! > 0
            }
        if (!owns) return false
        val maxOrder =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createQuery("SELECT COALESCE(MAX(sort_order), 0) FROM collection_books WHERE collection_id = ?")
                    .bind(0, collectionId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .firstOrNull() ?: 0
            }
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """INSERT INTO collection_books (collection_id, book_id, sort_order, added_at)
                       VALUES (?,?,?,?)
                       ON CONFLICT (collection_id, book_id) DO NOTHING""",
                ).bind(0, collectionId.toString())
                .bind(1, bookId.toString())
                .bind(2, maxOrder + 1)
                .bind(3, Instant.now().toString())
                .execute()
        }
        return true
    }

    fun removeBook(
        userId: UUID,
        collectionId: UUID,
        bookId: UUID,
    ): Boolean {
        val owns =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM collections WHERE id = ? AND user_id = ?")
                    .bind(0, collectionId.toString())
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .firstOrNull()!! > 0
            }
        if (!owns) return false
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM collection_books WHERE collection_id = ? AND book_id = ?")
                .bind(0, collectionId.toString())
                .bind(1, bookId.toString())
                .execute()
        }
        return true
    }

    fun getCollectionBooks(
        userId: UUID,
        collectionId: UUID,
    ): List<String> =
        jdbi.withHandle<List<String>, Exception> { h ->
            h
                .createQuery(
                    """SELECT cb.book_id FROM collection_books cb
                       JOIN collections c ON c.id = cb.collection_id
                       WHERE cb.collection_id = ? AND c.user_id = ?
                       ORDER BY cb.sort_order""",
                ).bind(0, collectionId.toString())
                .bind(1, userId.toString())
                .mapTo(String::class.java)
                .list()
        }
}
