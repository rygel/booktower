package org.booktower.services

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.booktower.models.CreateBookRequest
import org.booktower.models.FetchedMetadata
import org.booktower.models.ReadStatus
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.GoodreadsImportService")

data class GoodreadsImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: Int,
)

class GoodreadsImportService(
    private val bookService: BookService,
) {
    fun import(
        userId: UUID,
        libraryId: String,
        csvStream: InputStream,
    ): GoodreadsImportResult {
        var imported = 0
        var skipped = 0
        var errors = 0

        try {
            val format =
                CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build()

            InputStreamReader(csvStream, Charsets.UTF_8).use { reader ->
                format.parse(reader).use { parser ->
                    for (record in parser) {
                        try {
                            val title = record.safeGet("title")
                            if (title == null) {
                                skipped++
                                continue
                            }
                            val author = record.safeGet("author")
                            val isbn = parseIsbn(record.safeGet("isbn") ?: record.safeGet("isbn13") ?: "")
                            val publisher = record.safeGet("publisher")
                            val year = record.safeGet("year published")

                            val result =
                                bookService.createBook(
                                    userId,
                                    CreateBookRequest(title = title, author = author, description = null, libraryId = libraryId),
                                )
                            if (result.isFailure) {
                                errors++
                                continue
                            }
                            val book = result.getOrThrow()
                            val bookId = UUID.fromString(book.id)

                            if (isbn != null || publisher != null || year != null) {
                                bookService.applyFetchedMetadata(
                                    userId,
                                    bookId,
                                    FetchedMetadata(
                                        title = null,
                                        author = null,
                                        description = null,
                                        isbn = isbn,
                                        publisher = publisher,
                                        publishedDate = year,
                                        openLibraryCoverId = null,
                                    ),
                                )
                            }

                            val rating = record.safeGet("my rating")?.toIntOrNull()
                            if (rating != null && rating in 1..5) {
                                bookService.setRating(userId, bookId, rating)
                            }

                            val shelf = record.safeGet("exclusive shelf")?.lowercase()
                            val status =
                                when (shelf) {
                                    "read" -> ReadStatus.FINISHED
                                    "currently-reading" -> ReadStatus.READING
                                    "to-read" -> ReadStatus.WANT_TO_READ
                                    else -> null
                                }
                            if (status != null) {
                                bookService.setStatus(userId, bookId, status)
                            }

                            val shelves = record.safeGet("bookshelves")
                            if (!shelves.isNullOrBlank()) {
                                val excluded = setOf("read", "currently-reading", "to-read")
                                val tags =
                                    shelves
                                        .split(",")
                                        .map { it.trim().lowercase() }
                                        .filter { it.isNotBlank() && it !in excluded }
                                if (tags.isNotEmpty()) {
                                    bookService.setTags(userId, bookId, tags)
                                }
                            }

                            imported++
                        } catch (e: Exception) {
                            logger.warn("Error importing Goodreads row: ${e.message}")
                            errors++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse Goodreads CSV: ${e.message}")
            return GoodreadsImportResult(imported, skipped, errors + 1)
        }

        logger.info("Goodreads import for user $userId: imported=$imported, skipped=$skipped, errors=$errors")
        return GoodreadsImportResult(imported, skipped, errors)
    }

    private fun CSVRecord.safeGet(name: String): String? = if (isMapped(name)) get(name)?.takeIf { it.isNotBlank() } else null

    // Goodreads ISBNs are wrapped like ="0123456789" — strip the wrapping
    private fun parseIsbn(raw: String): String? {
        val stripped = raw.trim().removePrefix("=\"").removeSuffix("\"")
        return stripped.takeIf { it.isNotBlank() && it != "=" }
    }
}
