package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID

private val deliveryLogger = LoggerFactory.getLogger("runary.BookDeliveryService")

data class EmailRecipient(
    val id: String,
    val userId: String,
    val label: String,
    val email: String,
    val createdAt: String,
)

data class AddRecipientRequest(
    val label: String,
    val email: String,
)

class BookDeliveryService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
    private val emailService: EmailService,
    private val booksPath: String,
) {
    // ── Recipients ────────────────────────────────────────────────────────────

    fun listRecipients(userId: UUID): List<EmailRecipient> =
        jdbi.withHandle<List<EmailRecipient>, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, user_id, label, email, created_at FROM email_recipients WHERE user_id = ? ORDER BY label",
                ).bind(0, userId.toString())
                .map { row, _ ->
                    EmailRecipient(
                        id = row.getString("id"),
                        userId = row.getString("user_id"),
                        label = row.getString("label"),
                        email = row.getString("email"),
                        createdAt = row.getString("created_at"),
                    )
                }.list()
        }

    fun addRecipient(
        userId: UUID,
        req: AddRecipientRequest,
    ): EmailRecipient {
        require(req.label.isNotBlank()) { "label must not be blank" }
        require(req.email.contains('@')) { "invalid email address" }
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO email_recipients (id, user_id, label, email, created_at) VALUES (?, ?, ?, ?, ?)",
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, req.label.trim())
                .bind(3, req.email.trim().lowercase())
                .bind(4, now)
                .execute()
        }
        return EmailRecipient(id, userId.toString(), req.label.trim(), req.email.trim().lowercase(), now)
    }

    fun deleteRecipient(
        userId: UUID,
        recipientId: String,
    ): Boolean =
        jdbi.withHandle<Int, Exception> { h ->
            h
                .createUpdate("DELETE FROM email_recipients WHERE id = ? AND user_id = ?")
                .bind(0, recipientId)
                .bind(1, userId.toString())
                .execute()
        } > 0

    // ── Delivery ──────────────────────────────────────────────────────────────

    /**
     * Sends the book file to [toEmail].
     * Returns true if the send succeeded, false if the file is missing or SMTP is disabled.
     * Throws [IllegalArgumentException] if the book is not found.
     */
    fun sendBook(
        userId: UUID,
        bookId: UUID,
        toEmail: String,
    ): Boolean {
        require(toEmail.contains('@')) { "invalid email address" }
        val book =
            bookService.getBook(userId, bookId)
                ?: throw IllegalArgumentException("Book not found")
        val filePath =
            book.filePath?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Book has no file attached")
        val file =
            File(filePath).takeIf { it.exists() && it.isFile }
                ?: throw IllegalArgumentException("Book file not found on disk")
        val bytes = file.readBytes()
        return try {
            emailService.sendBook(toEmail, book.title, file.name, bytes)
            true
        } catch (e: Exception) {
            deliveryLogger.warn("Failed to send book '${book.title}' to $toEmail: ${e.message}")
            false
        }
    }
}
