package org.booktower.services

import org.booktower.models.LinkedBookDto
import org.booktower.models.SyncPositionDto
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class BookLinkService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
) {
    private val logger = LoggerFactory.getLogger("booktower.BookLinkService")

    fun linkBooks(userId: UUID, bookId: UUID, linkedBookId: UUID): LinkedBookDto? {
        // Get both books
        val book1 = bookService.getBook(userId, bookId) ?: return null
        val book2 = bookService.getBook(userId, linkedBookId) ?: return null

        // Determine which is ebook and which is audiobook
        val (ebookId, audioId) = when {
            book1.bookFormat == "AUDIOBOOK" && book2.bookFormat != "AUDIOBOOK" -> linkedBookId to bookId
            book2.bookFormat == "AUDIOBOOK" && book1.bookFormat != "AUDIOBOOK" -> bookId to linkedBookId
            else -> bookId to linkedBookId // default: first is ebook, second is audio
        }

        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        return try {
            jdbi.withHandle<LinkedBookDto?, Exception> { handle ->
                handle.createUpdate(
                    """
                    INSERT INTO linked_books (id, user_id, ebook_id, audio_id, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                ).bind(0, id).bind(1, userId.toString())
                    .bind(2, ebookId.toString()).bind(3, audioId.toString())
                    .bind(4, now).execute()

                val ebook = bookService.getBook(userId, ebookId)
                val audio = bookService.getBook(userId, audioId)

                LinkedBookDto(
                    id = id,
                    ebookId = ebookId.toString(),
                    audioId = audioId.toString(),
                    ebookTitle = ebook?.title ?: "",
                    audioTitle = audio?.title ?: "",
                    createdAt = now,
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to link books: ${e.message}")
            null
        }
    }

    fun unlinkBook(userId: UUID, bookId: UUID): Boolean {
        return jdbi.withHandle<Int, Exception> { handle ->
            handle.createUpdate("DELETE FROM linked_books WHERE user_id = ? AND (ebook_id = ? OR audio_id = ?)")
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
                .bind(2, bookId.toString())
                .execute()
        } > 0
    }

    fun getLinkForBook(userId: UUID, bookId: UUID): LinkedBookDto? {
        return jdbi.withHandle<LinkedBookDto?, Exception> { handle ->
            val row = handle.createQuery(
                """
                SELECT lb.id, lb.ebook_id, lb.audio_id, lb.created_at,
                       eb.title AS ebook_title, ab.title AS audio_title
                FROM linked_books lb
                JOIN books eb ON eb.id = lb.ebook_id
                JOIN books ab ON ab.id = lb.audio_id
                WHERE lb.user_id = ? AND (lb.ebook_id = ? OR lb.audio_id = ?)
                """,
            ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .bind(2, bookId.toString())
                .mapToMap()
                .firstOrNull() ?: return@withHandle null

            LinkedBookDto(
                id = row["id"].toString(),
                ebookId = row["ebook_id"].toString(),
                audioId = row["audio_id"].toString(),
                ebookTitle = row["ebook_title"].toString(),
                audioTitle = row["audio_title"].toString(),
                createdAt = row["created_at"].toString(),
            )
        }
    }

    /**
     * Given the user's current position in bookId, compute where to resume in the linked book.
     *
     * Position mapping uses chapter-based conversion:
     * - Ebook progress comes from reading_progress (current_page / total_pages)
     * - Audiobook progress comes from listen_progress (position_sec / total_sec)
     * - Track count is derived from book_files table
     * - The overall percentage through the source maps to a chapter + position in the target
     */
    fun syncPosition(userId: UUID, bookId: UUID): SyncPositionDto? {
        val link = getLinkForBook(userId, bookId) ?: return null
        val isEbookSide = bookId.toString() == link.ebookId

        val sourceBook = bookService.getBook(userId, bookId) ?: return null
        val targetBookId = if (isEbookSide) link.audioId else link.ebookId
        val targetBook = bookService.getBook(userId, UUID.fromString(targetBookId)) ?: return null

        if (isEbookSide) {
            // Source is ebook, target is audiobook
            val currentPage = sourceBook.progress?.currentPage ?: 0
            val totalPages = sourceBook.progress?.totalPages ?: sourceBook.pageCount ?: 1
            if (totalPages <= 0) return null

            // Get audiobook track count from book_files
            val trackCount = countBookFiles(targetBookId).coerceAtLeast(1)

            // Which chapter is the reader in? (evenly distributed pages across chapters)
            val pagesPerChapter = totalPages.toDouble() / trackCount
            val chapterIndex = ((currentPage / pagesPerChapter).toInt()).coerceIn(0, trackCount - 1)
            val posInChapter = (currentPage - chapterIndex * pagesPerChapter) / pagesPerChapter

            return SyncPositionDto(
                targetBookId = targetBookId,
                targetTitle = targetBook.title,
                targetFormat = "AUDIOBOOK",
                chapterIndex = chapterIndex,
                chapterLabel = "Chapter ${chapterIndex + 1}",
                positionInChapter = posInChapter.coerceIn(0.0, 1.0),
            )
        } else {
            // Source is audiobook, target is ebook
            val listenProgress = getListenProgress(userId, bookId)
            val positionSec = listenProgress?.first ?: 0
            val totalSec = listenProgress?.second ?: 1
            if (totalSec <= 0) return null

            val totalPages = targetBook.pageCount ?: 1
            val trackCount = countBookFiles(bookId.toString()).coerceAtLeast(1)
            val pagesPerChapter = totalPages.toDouble() / trackCount

            // Compute overall fraction through the audiobook
            val fraction = positionSec.toDouble() / totalSec
            val estimatedPage = (fraction * totalPages).toInt().coerceIn(0, totalPages)
            val chapterIndex = (fraction * trackCount).toInt().coerceIn(0, trackCount - 1)
            val chapterFraction = (fraction * trackCount) - chapterIndex

            return SyncPositionDto(
                targetBookId = targetBookId,
                targetTitle = targetBook.title,
                targetFormat = "EBOOK",
                chapterIndex = chapterIndex,
                chapterLabel = "Chapter ${chapterIndex + 1}",
                positionInChapter = chapterFraction.coerceIn(0.0, 1.0),
            )
        }
    }

    /** Count tracks in book_files for a given book. */
    private fun countBookFiles(bookId: String): Int {
        return jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery("SELECT COUNT(*) FROM book_files WHERE book_id = ?")
                .bind(0, bookId)
                .mapTo(Int::class.java)
                .one()
        }
    }

    /** Get listen progress (positionSec, totalSec) for a user/book. */
    private fun getListenProgress(userId: UUID, bookId: UUID): Pair<Int, Int>? {
        return jdbi.withHandle<Pair<Int, Int>?, Exception> { handle ->
            val row = handle.createQuery(
                "SELECT position_sec, total_sec FROM listen_progress WHERE user_id = ? AND book_id = ?",
            ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .mapToMap()
                .firstOrNull() ?: return@withHandle null
            val pos = (row["position_sec"] as? Number)?.toInt() ?: 0
            val total = (row["total_sec"] as? Number)?.toInt() ?: 0
            pos to total
        }
    }
}
