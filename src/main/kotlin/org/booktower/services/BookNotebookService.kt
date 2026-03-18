package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

data class NotebookDto(
    val id: String,
    val bookId: String,
    val userId: String,
    val title: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateNotebookRequest(
    val title: String,
    val content: String = "",
)

data class UpdateNotebookRequest(
    val title: String? = null,
    val content: String? = null,
)

class BookNotebookService(
    private val jdbi: Jdbi,
) {
    fun listForBook(
        bookId: String,
        userId: UUID,
    ): List<NotebookDto> =
        jdbi.withHandle<List<NotebookDto>, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, book_id, user_id, title, content, created_at, updated_at FROM book_notebooks WHERE book_id = ? AND user_id = ? ORDER BY title",
                ).bind(0, bookId)
                .bind(1, userId.toString())
                .map { row -> mapRow(row) }
                .list()
        }

    fun get(
        bookId: String,
        notebookId: String,
        userId: UUID,
    ): NotebookDto? =
        jdbi.withHandle<NotebookDto?, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, book_id, user_id, title, content, created_at, updated_at FROM book_notebooks WHERE id = ? AND book_id = ? AND user_id = ?",
                ).bind(0, notebookId)
                .bind(1, bookId)
                .bind(2, userId.toString())
                .map { row -> mapRow(row) }
                .firstOrNull()
        }

    fun create(
        bookId: String,
        userId: UUID,
        request: CreateNotebookRequest,
    ): NotebookDto {
        require(request.title.isNotBlank()) { "Notebook title must not be blank" }
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO book_notebooks (id, book_id, user_id, title, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).bind(0, id)
                .bind(1, bookId)
                .bind(2, userId.toString())
                .bind(3, request.title)
                .bind(4, request.content)
                .bind(5, now)
                .bind(6, now)
                .execute()
        }
        return NotebookDto(
            id = id,
            bookId = bookId,
            userId = userId.toString(),
            title = request.title,
            content = request.content,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        bookId: String,
        notebookId: String,
        userId: UUID,
        request: UpdateNotebookRequest,
    ): NotebookDto? {
        get(bookId, notebookId, userId) ?: return null
        val now = Instant.now().toString()
        val sets = mutableListOf<String>()
        val bindings = mutableListOf<Any?>()
        request.title?.let {
            require(it.isNotBlank()) { "Title must not be blank" }
            sets += "title = ?"
            bindings += it
        }
        request.content?.let {
            sets += "content = ?"
            bindings += it
        }
        sets += "updated_at = ?"
        bindings += now
        bindings += notebookId

        jdbi.useHandle<Exception> { h ->
            val stmt = h.createUpdate("UPDATE book_notebooks SET ${sets.joinToString(", ")} WHERE id = ?")
            bindings.forEachIndexed { idx, v -> stmt.bind(idx, v) }
            stmt.execute()
        }
        return get(bookId, notebookId, userId)
    }

    fun delete(
        bookId: String,
        notebookId: String,
        userId: UUID,
    ): Boolean {
        val rows =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM book_notebooks WHERE id = ? AND book_id = ? AND user_id = ?")
                    .bind(0, notebookId)
                    .bind(1, bookId)
                    .bind(2, userId.toString())
                    .execute()
            }
        return rows > 0
    }

    private fun mapRow(row: org.jdbi.v3.core.result.RowView) =
        NotebookDto(
            id = row.getColumn("id", String::class.java),
            bookId = row.getColumn("book_id", String::class.java),
            userId = row.getColumn("user_id", String::class.java),
            title = row.getColumn("title", String::class.java),
            content = row.getColumn("content", String::class.java) ?: "",
            createdAt = row.getColumn("created_at", String::class.java),
            updatedAt = row.getColumn("updated_at", String::class.java),
        )
}
