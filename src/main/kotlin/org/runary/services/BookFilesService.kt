package org.runary.services

import org.runary.models.AddBookFileRequest
import org.runary.models.BookFormatDto
import org.jdbi.v3.core.Jdbi
import java.io.File
import java.time.Instant
import java.util.UUID

class BookFilesService(
    private val jdbi: Jdbi,
) {
    fun listFiles(bookId: String): List<BookFormatDto> =
        jdbi.withHandle<List<BookFormatDto>, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, book_id, file_path, file_size, format, is_primary, label, added_at FROM book_formats WHERE book_id = ? ORDER BY is_primary DESC, added_at",
                ).bind(0, bookId)
                .map { row ->
                    BookFormatDto(
                        id = row.getColumn("id", String::class.java),
                        bookId = row.getColumn("book_id", String::class.java),
                        filePath = row.getColumn("file_path", String::class.java),
                        fileSize = (row.getColumn("file_size", java.lang.Long::class.java) as? Long) ?: 0L,
                        format = row.getColumn("format", String::class.java),
                        isPrimary = row.getColumn("is_primary", java.lang.Boolean::class.java) == true,
                        label = row.getColumn("label", String::class.java),
                        addedAt = row.getColumn("added_at", String::class.java),
                    )
                }.list()
        }

    fun addFile(
        bookId: String,
        request: AddBookFileRequest,
    ): BookFormatDto {
        require(request.filePath.isNotBlank()) { "filePath must not be blank" }
        val file = File(request.filePath)
        val format = file.extension.lowercase().ifBlank { "unknown" }
        val fileSize = if (file.exists()) file.length() else 0L
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        jdbi.useHandle<Exception> { h ->
            if (request.isPrimary) {
                // demote any existing primary
                h
                    .createUpdate("UPDATE book_formats SET is_primary = FALSE WHERE book_id = ?")
                    .bind(0, bookId)
                    .execute()
            }
            h
                .createUpdate(
                    "INSERT INTO book_formats (id, book_id, file_path, file_size, format, is_primary, label, added_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ).bind(0, id)
                .bind(1, bookId)
                .bind(2, request.filePath)
                .bind(3, fileSize)
                .bind(4, format)
                .bind(5, request.isPrimary)
                .bind(6, request.label)
                .bind(7, now)
                .execute()
        }

        return BookFormatDto(
            id = id,
            bookId = bookId,
            filePath = request.filePath,
            fileSize = fileSize,
            format = format,
            isPrimary = request.isPrimary,
            label = request.label,
            addedAt = now,
        )
    }

    /** Remove a specific file entry. Returns true if deleted, false if not found. */
    fun removeFile(
        bookId: String,
        fileId: String,
    ): Boolean {
        val rows =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM book_formats WHERE id = ? AND book_id = ?")
                    .bind(0, fileId)
                    .bind(1, bookId)
                    .execute()
            }
        return rows > 0
    }
}
