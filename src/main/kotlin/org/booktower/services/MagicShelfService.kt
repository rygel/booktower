package org.booktower.services

import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.MagicShelfService")

class MagicShelfService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
) {
    fun getShelves(userId: UUID): List<MagicShelfDto> {
        val shelves =
            jdbi.withHandle<List<MagicShelfDto>, Exception> { handle ->
                handle
                    .createQuery("SELECT * FROM magic_shelves WHERE user_id = ? ORDER BY name")
                    .bind(0, userId.toString())
                    .map { row -> mapShelf(row) }
                    .list()
            }
        return shelves.map { it.copy(bookCount = countMatchingBooks(userId, it)) }
    }

    fun getShelf(
        userId: UUID,
        shelfId: UUID,
    ): MagicShelfDto? {
        val shelf =
            jdbi.withHandle<MagicShelfDto?, Exception> { handle ->
                handle
                    .createQuery("SELECT * FROM magic_shelves WHERE id = ? AND user_id = ?")
                    .bind(0, shelfId.toString())
                    .bind(1, userId.toString())
                    .map { row -> mapShelf(row) }
                    .firstOrNull()
            } ?: return null
        return shelf.copy(bookCount = countMatchingBooks(userId, shelf))
    }

    fun createShelf(
        userId: UUID,
        request: CreateMagicShelfRequest,
    ): MagicShelfDto {
        val id = UUID.randomUUID()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "INSERT INTO magic_shelves (id, user_id, name, rule_type, rule_value, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                ).bind(0, id.toString())
                .bind(1, userId.toString())
                .bind(2, request.name)
                .bind(3, request.ruleType.name)
                .bind(4, request.ruleValue)
                .bind(5, now)
                .execute()
        }
        logger.info("Magic shelf created: ${request.name} (${request.ruleType}:${request.ruleValue})")
        val dto = MagicShelfDto(id.toString(), request.name, request.ruleType, request.ruleValue, 0, now)
        return dto.copy(bookCount = countMatchingBooks(userId, dto))
    }

    fun deleteShelf(
        userId: UUID,
        shelfId: UUID,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate("DELETE FROM magic_shelves WHERE id = ? AND user_id = ?")
                    .bind(0, shelfId.toString())
                    .bind(1, userId.toString())
                    .execute()
            }
        if (deleted > 0) logger.info("Magic shelf deleted: $shelfId")
        return deleted > 0
    }

    fun resolveBooks(
        userId: UUID,
        shelf: MagicShelfDto,
        limit: Int = 200,
    ): List<BookDto> =
        when (shelf.ruleType) {
            ShelfRuleType.STATUS -> bookService.getBooksForShelf(userId, statusFilter = shelf.ruleValue, limit = limit)
            ShelfRuleType.TAG -> bookService.getBooksForShelf(userId, tagFilter = shelf.ruleValue, limit = limit)
            ShelfRuleType.RATING_GTE -> bookService.getBooksForShelf(userId, ratingGte = shelf.ruleValue?.toIntOrNull(), limit = limit)
        }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun countMatchingBooks(
        userId: UUID,
        shelf: MagicShelfDto,
    ): Int =
        when (shelf.ruleType) {
            ShelfRuleType.STATUS -> {
                jdbi.withHandle<Int, Exception> { handle ->
                    handle
                        .createQuery(
                            """SELECT COUNT(*) FROM books b
                   INNER JOIN libraries l ON b.library_id = l.id
                   INNER JOIN book_status bs ON bs.book_id = b.id AND bs.user_id = ?
                   WHERE l.user_id = ? AND bs.status = ?""",
                        ).bind(0, userId.toString())
                        .bind(1, userId.toString())
                        .bind(2, shelf.ruleValue)
                        .mapTo(Int::class.java)
                        .first() ?: 0
                }
            }

            ShelfRuleType.TAG -> {
                jdbi.withHandle<Int, Exception> { handle ->
                    handle
                        .createQuery(
                            """SELECT COUNT(*) FROM books b
                   INNER JOIN libraries l ON b.library_id = l.id
                   INNER JOIN book_tags bt ON bt.book_id = b.id AND bt.user_id = ? AND bt.tag = ?
                   WHERE l.user_id = ?""",
                        ).bind(0, userId.toString())
                        .bind(1, shelf.ruleValue)
                        .bind(2, userId.toString())
                        .mapTo(Int::class.java)
                        .first() ?: 0
                }
            }

            ShelfRuleType.RATING_GTE -> {
                val minRating = shelf.ruleValue?.toIntOrNull() ?: 1
                jdbi.withHandle<Int, Exception> { handle ->
                    handle
                        .createQuery(
                            """SELECT COUNT(*) FROM books b
                       INNER JOIN libraries l ON b.library_id = l.id
                       INNER JOIN book_ratings br ON br.book_id = b.id AND br.user_id = ? AND br.rating >= ?
                       WHERE l.user_id = ?""",
                        ).bind(0, userId.toString())
                        .bind(1, minRating)
                        .bind(2, userId.toString())
                        .mapTo(Int::class.java)
                        .first() ?: 0
                }
            }
        }

    fun shareShelf(
        userId: UUID,
        shelfId: UUID,
    ): MagicShelfDto? {
        val token = UUID.randomUUID().toString()
        val updated =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        "UPDATE magic_shelves SET is_public = TRUE, share_token = ? WHERE id = ? AND user_id = ?",
                    ).bind(0, token)
                    .bind(1, shelfId.toString())
                    .bind(2, userId.toString())
                    .execute()
            }
        if (updated == 0) return null
        return getShelf(userId, shelfId)
    }

    fun unshareShelf(
        userId: UUID,
        shelfId: UUID,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        "UPDATE magic_shelves SET is_public = FALSE, share_token = NULL WHERE id = ? AND user_id = ?",
                    ).bind(0, shelfId.toString())
                    .bind(1, userId.toString())
                    .execute()
            }
        return updated > 0
    }

    fun getPublicShelf(shareToken: String): PublicShelfDto? {
        val shelf =
            jdbi.withHandle<MagicShelfDto?, Exception> { handle ->
                handle
                    .createQuery(
                        "SELECT * FROM magic_shelves WHERE share_token = ? AND is_public = TRUE",
                    ).bind(0, shareToken)
                    .map { row -> mapShelf(row) }
                    .firstOrNull()
            } ?: return null
        val ownerId =
            jdbi.withHandle<String?, Exception> { handle ->
                handle
                    .createQuery("SELECT user_id FROM magic_shelves WHERE share_token = ?")
                    .bind(0, shareToken)
                    .mapTo(String::class.java)
                    .firstOrNull()
            } ?: return null
        val books = resolveBooks(UUID.fromString(ownerId), shelf)
        return PublicShelfDto(shelf.name, shareToken, books)
    }

    private fun mapShelf(row: RowView): MagicShelfDto {
        val ruleType =
            try {
                ShelfRuleType.valueOf(row.getColumn("rule_type", String::class.java))
            } catch (_: Exception) {
                ShelfRuleType.STATUS
            }
        val isPublic =
            try {
                row.getColumn("is_public", java.lang.Boolean::class.java) == true
            } catch (_: Exception) {
                false
            }
        val shareToken =
            try {
                row.getColumn("share_token", String::class.java)
            } catch (_: Exception) {
                null
            }
        return MagicShelfDto(
            id = row.getColumn("id", String::class.java),
            name = row.getColumn("name", String::class.java),
            ruleType = ruleType,
            ruleValue = row.getColumn("rule_value", String::class.java),
            bookCount = 0,
            createdAt = row.getColumn("created_at", String::class.java),
            isPublic = isPublic,
            shareToken = shareToken,
        )
    }
}
