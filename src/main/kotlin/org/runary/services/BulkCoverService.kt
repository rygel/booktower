package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.BulkCoverService")

data class BulkCoverResult(
    val submitted: Int,
    val skipped: Int,
    val errors: Int,
)

class BulkCoverService(
    private val jdbi: Jdbi,
    private val pdfMetadataService: PdfMetadataService,
    private val epubMetadataService: EpubMetadataService,
) {
    /**
     * Re-enqueue cover extraction for all books in [libraryId] owned by [userId].
     * If [libraryId] is null, processes all libraries the user owns.
     * Only processes books that have a non-blank file_path pointing to an existing file.
     * Returns a summary of what was submitted.
     */
    fun regenerateCovers(
        userId: UUID,
        libraryId: String?,
    ): BulkCoverResult {
        val books =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
                val query =
                    if (libraryId != null) {
                        h
                            .createQuery(
                                "SELECT b.id, b.file_path FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ? AND b.library_id = ? AND b.file_path IS NOT NULL AND b.file_path <> ''",
                            ).bind(0, userId.toString())
                            .bind(1, libraryId)
                    } else {
                        h
                            .createQuery(
                                "SELECT b.id, b.file_path FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ? AND b.file_path IS NOT NULL AND b.file_path <> ''",
                            ).bind(0, userId.toString())
                    }
                query.mapToMap().list()
            }

        var submitted = 0
        var skipped = 0
        var errors = 0

        for (book in books) {
            val bookId = book["id"] as? String ?: continue
            val filePath = book["file_path"] as? String ?: continue
            val file = File(filePath)
            if (!file.exists()) {
                skipped++
                continue
            }

            try {
                when (file.extension.lowercase()) {
                    "pdf" -> {
                        pdfMetadataService.submitAsync(bookId, file)
                        submitted++
                    }

                    "epub" -> {
                        epubMetadataService.submitAsync(bookId, file)
                        submitted++
                    }

                    else -> {
                        skipped++
                    }
                }
            } catch (e: Exception) {
                logger.warn("BulkCoverService: failed to submit cover extraction for $bookId: ${e.message}")
                errors++
            }
        }

        logger.info("BulkCoverService: regenerateCovers submitted=$submitted skipped=$skipped errors=$errors")
        return BulkCoverResult(submitted, skipped, errors)
    }
}
