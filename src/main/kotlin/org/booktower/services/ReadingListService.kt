package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val rlLog = LoggerFactory.getLogger("booktower.ReadingListService")

data class ReadingListDto(
    val id: String,
    val name: String,
    val description: String?,
    val itemCount: Int,
    val completedCount: Int,
    val createdAt: String,
)

data class ReadingListItemDto(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val bookAuthor: String?,
    val coverUrl: String?,
    val sortOrder: Int,
    val completed: Boolean,
    val completedAt: String?,
)

data class ReadingListDetail(
    val id: String,
    val name: String,
    val description: String?,
    val items: List<ReadingListItemDto>,
    val progressPercent: Int,
)

data class CreateReadingListRequest(
    val name: String,
    val description: String? = null,
)

/**
 * Ordered reading lists with per-item completion tracking.
 * Unlike collections (unordered bags), reading lists have a specific order
 * and track which items have been completed.
 */
class ReadingListService(
    private val jdbi: Jdbi,
) {
    fun getLists(userId: UUID): List<ReadingListDto> =
        jdbi.withHandle<List<ReadingListDto>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT rl.id, rl.name, rl.description, rl.created_at,
                       COALESCE(i.cnt, 0) AS item_count,
                       COALESCE(i.done, 0) AS completed_count
                FROM reading_lists rl
                LEFT JOIN (
                    SELECT list_id, COUNT(*) AS cnt,
                           SUM(CASE WHEN completed THEN 1 ELSE 0 END) AS done
                    FROM reading_list_items GROUP BY list_id
                ) i ON i.list_id = rl.id
                WHERE rl.user_id = ?
                ORDER BY rl.updated_at DESC
                """,
                ).bind(0, userId.toString())
                .map { row ->
                    ReadingListDto(
                        id = row.getColumn("id", String::class.java) ?: "",
                        name = row.getColumn("name", String::class.java) ?: "",
                        description = row.getColumn("description", String::class.java),
                        itemCount = row.getColumn("item_count", Int::class.javaObjectType) ?: 0,
                        completedCount = row.getColumn("completed_count", Int::class.javaObjectType) ?: 0,
                        createdAt = row.getColumn("created_at", String::class.java) ?: "",
                    )
                }.list()
        }

    fun getDetail(
        userId: UUID,
        listId: String,
    ): ReadingListDetail? {
        val list =
            jdbi.withHandle<Map<String, Any?>?, Exception> { h ->
                h
                    .createQuery("SELECT * FROM reading_lists WHERE id = ? AND user_id = ?")
                    .bind(0, listId)
                    .bind(1, userId.toString())
                    .mapToMap()
                    .firstOrNull()
            } ?: return null

        val items =
            jdbi.withHandle<List<ReadingListItemDto>, Exception> { h ->
                h
                    .createQuery(
                        """
                    SELECT rli.id, rli.book_id, rli.sort_order, rli.completed, rli.completed_at,
                           b.title AS book_title, b.author AS book_author, b.cover_path
                    FROM reading_list_items rli
                    INNER JOIN books b ON rli.book_id = b.id
                    WHERE rli.list_id = ?
                    ORDER BY rli.sort_order, rli.added_at
                    """,
                    ).bind(0, listId)
                    .map { row ->
                        ReadingListItemDto(
                            id = row.getColumn("id", String::class.java) ?: "",
                            bookId = row.getColumn("book_id", String::class.java) ?: "",
                            bookTitle = row.getColumn("book_title", String::class.java) ?: "",
                            bookAuthor = row.getColumn("book_author", String::class.java),
                            coverUrl = row.getColumn("cover_path", String::class.java),
                            sortOrder = row.getColumn("sort_order", Int::class.javaObjectType) ?: 0,
                            completed = row.getColumn("completed", Boolean::class.javaObjectType) ?: false,
                            completedAt = row.getColumn("completed_at", String::class.java),
                        )
                    }.list()
            }

        val progressPercent = if (items.isNotEmpty()) (items.count { it.completed } * 100) / items.size else 0

        return ReadingListDetail(
            id = list["id"] as? String ?: "",
            name = list["name"] as? String ?: "",
            description = list["description"] as? String,
            items = items,
            progressPercent = progressPercent,
        )
    }

    fun create(
        userId: UUID,
        request: CreateReadingListRequest,
    ): ReadingListDto {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("INSERT INTO reading_lists (id, user_id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
                .bind(0, id)
                .bind(1, userId.toString())
                .bind(2, request.name.trim().take(200))
                .bind(3, request.description?.take(2000))
                .bind(4, now)
                .bind(5, now)
                .execute()
        }
        rlLog.info("Reading list created: ${request.name}")
        return ReadingListDto(id, request.name.trim(), request.description, 0, 0, now)
    }

    fun delete(
        userId: UUID,
        listId: String,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM reading_lists WHERE id = ? AND user_id = ?")
                    .bind(0, listId)
                    .bind(1, userId.toString())
                    .execute()
            }
        return deleted > 0
    }

    fun addBook(
        userId: UUID,
        listId: String,
        bookId: String,
    ): Boolean {
        // Verify list belongs to user
        val owns =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM reading_lists WHERE id = ? AND user_id = ?")
                    .bind(0, listId)
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }
        if (!owns) return false

        val maxOrder =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createQuery("SELECT COALESCE(MAX(sort_order), 0) FROM reading_list_items WHERE list_id = ?")
                    .bind(0, listId)
                    .mapTo(Int::class.javaObjectType)
                    .one()
            }

        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        return try {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate("INSERT INTO reading_list_items (id, list_id, book_id, sort_order, added_at) VALUES (?, ?, ?, ?, ?)")
                    .bind(0, id)
                    .bind(1, listId)
                    .bind(2, bookId)
                    .bind(3, maxOrder + 1)
                    .bind(4, now)
                    .execute()
                h
                    .createUpdate("UPDATE reading_lists SET updated_at = ? WHERE id = ?")
                    .bind(0, now)
                    .bind(1, listId)
                    .execute()
            }
            true
        } catch (e: Exception) {
            false // duplicate
        }
    }

    fun removeBook(
        userId: UUID,
        listId: String,
        bookId: String,
    ): Boolean {
        val owns =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM reading_lists WHERE id = ? AND user_id = ?")
                    .bind(0, listId)
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }
        if (!owns) return false

        val deleted =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM reading_list_items WHERE list_id = ? AND book_id = ?")
                    .bind(0, listId)
                    .bind(1, bookId)
                    .execute()
            }
        return deleted > 0
    }

    fun toggleCompleted(
        userId: UUID,
        listId: String,
        bookId: String,
        completed: Boolean,
    ): Boolean {
        val owns =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM reading_lists WHERE id = ? AND user_id = ?")
                    .bind(0, listId)
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }
        if (!owns) return false

        val now = if (completed) Instant.now().toString() else null
        val updated =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("UPDATE reading_list_items SET completed = ?, completed_at = ? WHERE list_id = ? AND book_id = ?")
                    .bind(0, completed)
                    .bind(1, now)
                    .bind(2, listId)
                    .bind(3, bookId)
                    .execute()
            }
        return updated > 0
    }

    fun reorder(
        userId: UUID,
        listId: String,
        bookIds: List<String>,
    ): Boolean {
        val owns =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM reading_lists WHERE id = ? AND user_id = ?")
                    .bind(0, listId)
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }
        if (!owns) return false

        jdbi.useHandle<Exception> { h ->
            bookIds.forEachIndexed { idx, bid ->
                h
                    .createUpdate("UPDATE reading_list_items SET sort_order = ? WHERE list_id = ? AND book_id = ?")
                    .bind(0, idx)
                    .bind(1, listId)
                    .bind(2, bid)
                    .execute()
            }
        }
        return true
    }
}
