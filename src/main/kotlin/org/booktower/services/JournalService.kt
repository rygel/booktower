package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.JournalService")

data class JournalEntry(
    val id: String,
    val userId: String,
    val bookId: String,
    val title: String?,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateJournalEntryRequest(val title: String?, val content: String)
data class UpdateJournalEntryRequest(val title: String?, val content: String)

class JournalService(private val jdbi: Jdbi) {

    fun list(userId: UUID, bookId: UUID): List<JournalEntry> =
        jdbi.withHandle<List<JournalEntry>, Exception> { h ->
            h.createQuery(
                """SELECT id, user_id, book_id, title, content, created_at, updated_at
                   FROM book_journal_entries WHERE user_id = ? AND book_id = ?
                   ORDER BY created_at DESC""",
            )
                .bind(0, userId.toString()).bind(1, bookId.toString())
                .map { row -> row.toEntry() }.list()
        }

    fun listAll(userId: UUID): List<JournalEntry> =
        jdbi.withHandle<List<JournalEntry>, Exception> { h ->
            h.createQuery(
                """SELECT id, user_id, book_id, title, content, created_at, updated_at
                   FROM book_journal_entries WHERE user_id = ? ORDER BY created_at DESC""",
            )
                .bind(0, userId.toString())
                .map { row -> row.toEntry() }.list()
        }

    fun get(userId: UUID, entryId: UUID): JournalEntry? =
        jdbi.withHandle<JournalEntry?, Exception> { h ->
            h.createQuery(
                """SELECT id, user_id, book_id, title, content, created_at, updated_at
                   FROM book_journal_entries WHERE id = ? AND user_id = ?""",
            )
                .bind(0, entryId.toString()).bind(1, userId.toString())
                .map { row -> row.toEntry() }.firstOrNull()
        }

    fun create(userId: UUID, bookId: UUID, req: CreateJournalEntryRequest): JournalEntry {
        require(req.content.isNotBlank()) { "content must not be blank" }
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                """INSERT INTO book_journal_entries (id, user_id, book_id, title, content, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
            )
                .bind(0, id).bind(1, userId.toString()).bind(2, bookId.toString())
                .bind(3, req.title?.takeIf { it.isNotBlank() })
                .bind(4, req.content).bind(5, now).bind(6, now).execute()
        }
        logger.info("Journal entry created: $id for book $bookId by user $userId")
        return JournalEntry(id, userId.toString(), bookId.toString(), req.title?.takeIf { it.isNotBlank() }, req.content, now, now)
    }

    fun update(userId: UUID, entryId: UUID, req: UpdateJournalEntryRequest): JournalEntry? {
        require(req.content.isNotBlank()) { "content must not be blank" }
        val now = Instant.now().toString()
        val updated = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate(
                """UPDATE book_journal_entries SET title = ?, content = ?, updated_at = ?
                   WHERE id = ? AND user_id = ?""",
            )
                .bind(0, req.title?.takeIf { it.isNotBlank() })
                .bind(1, req.content).bind(2, now)
                .bind(3, entryId.toString()).bind(4, userId.toString()).execute()
        }
        return if (updated > 0) get(userId, entryId) else null
    }

    fun delete(userId: UUID, entryId: UUID): Boolean =
        jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM book_journal_entries WHERE id = ? AND user_id = ?")
                .bind(0, entryId.toString()).bind(1, userId.toString()).execute()
        } > 0

    private fun org.jdbi.v3.core.result.RowView.toEntry() = JournalEntry(
        id = getColumn("id", String::class.java),
        userId = getColumn("user_id", String::class.java),
        bookId = getColumn("book_id", String::class.java),
        title = getColumn("title", String::class.java),
        content = getColumn("content", String::class.java),
        createdAt = getColumn("created_at", String::class.java) ?: "",
        updatedAt = getColumn("updated_at", String::class.java) ?: "",
    )
}
