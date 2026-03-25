package org.runary.services

import org.runary.models.BookDto
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.BookSharingService")

data class SharedBookDto(
    val title: String,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val pageCount: Int?,
    val publisher: String?,
    val publishedDate: String?,
    val isbn: String?,
    val bookFormat: String?,
    val shareToken: String,
    val downloadUrl: String?,
)

class BookSharingService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
) {
    /** Generate a share token for a book. Returns the token. */
    fun shareBook(
        userId: UUID,
        bookId: UUID,
    ): String? {
        // Verify ownership
        val book = bookService.getBook(userId, bookId) ?: return null
        val token = UUID.randomUUID().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET share_token = ? WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)")
                .bind(0, token)
                .bind(1, bookId.toString())
                .bind(2, userId.toString())
                .execute()
        }
        logger.info("Book shared: {} (token={})", book.title, token)
        return token
    }

    /** Remove share token from a book. */
    fun unshareBook(
        userId: UUID,
        bookId: UUID,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate(
                        "UPDATE books SET share_token = NULL WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)",
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .execute()
            }
        if (updated > 0) logger.info("Book unshared: {}", bookId)
        return updated > 0
    }

    /** Get the share token for a book (if shared). */
    fun getShareToken(
        userId: UUID,
        bookId: UUID,
    ): String? =
        jdbi.withHandle<String?, Exception> { h ->
            h
                .createQuery(
                    "SELECT share_token FROM books WHERE id = ? AND library_id IN (SELECT id FROM libraries WHERE user_id = ?)",
                ).bind(0, bookId.toString())
                .bind(1, userId.toString())
                .mapTo(String::class.java)
                .firstOrNull()
        }

    /** Get a shared book by its public token. No authentication required. */
    fun getPublicBook(shareToken: String): SharedBookDto? =
        jdbi.withHandle<SharedBookDto?, Exception> { h ->
            h
                .createQuery(
                    """SELECT b.id, b.title, b.author, b.description, b.page_count, b.publisher,
                              b.published_date, b.isbn, b.book_format, b.share_token, b.file_path
                       FROM books b
                       WHERE b.share_token = ?""",
                ).bind(0, shareToken)
                .map { row ->
                    val bookId = row.getColumn("id", String::class.java)
                    val filePath = row.getColumn("file_path", String::class.java)
                    SharedBookDto(
                        title = row.getColumn("title", String::class.java) ?: "Untitled",
                        author = row.getColumn("author", String::class.java),
                        description = row.getColumn("description", String::class.java),
                        coverUrl = "/covers/$bookId.jpg",
                        pageCount = row.getColumn("page_count", java.lang.Integer::class.java)?.toInt(),
                        publisher = row.getColumn("publisher", String::class.java),
                        publishedDate = row.getColumn("published_date", String::class.java),
                        isbn = row.getColumn("isbn", String::class.java),
                        bookFormat = row.getColumn("book_format", String::class.java),
                        shareToken = shareToken,
                        downloadUrl = if (!filePath.isNullOrBlank()) "/public/book/$shareToken/download" else null,
                    )
                }.firstOrNull()
        }

    /** Get the file path for a shared book (for public download). */
    fun getSharedBookFilePath(shareToken: String): String? =
        jdbi.withHandle<String?, Exception> { h ->
            h
                .createQuery("SELECT file_path FROM books WHERE share_token = ?")
                .bind(0, shareToken)
                .mapTo(String::class.java)
                .firstOrNull()
        }
}
