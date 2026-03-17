package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

/**
 * Provides Komga-compatible REST API responses, allowing Komga-aware clients
 * (e.g. Tachiyomi, Paperback) to connect to BookTower as if it were Komga.
 *
 * Implements a subset of Komga's v1 REST API:
 *   GET /api/v1/libraries       — list libraries
 *   GET /api/v1/series          — list series (mapped from book series field)
 *   GET /api/v1/series/{id}     — series detail
 *   GET /api/v1/books           — list books
 *   GET /api/v1/books/{id}      — book detail
 *   GET /api/v1/books/{id}/file — download book file
 *   GET /api/v1/books/{id}/thumbnail — cover image
 */
class KomgaApiService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
    private val libraryService: LibraryService,
    private val appBaseUrl: String,
) {

    fun listLibraries(userId: UUID): List<Map<String, Any>> {
        val libs = libraryService.getLibraries(userId)
        return libs.map { lib ->
            mapOf(
                "id" to lib.id,
                "name" to lib.name,
                "root" to lib.path,
                "createdDate" to lib.createdAt,
                "lastModifiedDate" to lib.createdAt,
                "unavailable" to false,
                "empty" to (lib.bookCount == 0),
            )
        }
    }

    /** Returns series derived from the `series` field on books. Each unique series name = one Komga series. */
    fun listSeries(userId: UUID, libraryId: String?): Map<String, Any> {
        val books = bookService.getBooks(userId, libraryId, page = 1, pageSize = 100).getBooks()
        val seriesMap = books.filter { it.series != null }.groupBy { it.series!! }
        val content = seriesMap.entries.mapIndexed { idx, (name, seriesBooks) ->
            mapOf(
                "id" to "series-${name.hashCode()}",
                "libraryId" to (seriesBooks.first().libraryId),
                "name" to name,
                "url" to "/series/$name",
                "booksCount" to seriesBooks.size,
                "booksReadCount" to seriesBooks.count { (it.progress?.percentage ?: 0.0) >= 100.0 },
                "booksUnreadCount" to seriesBooks.count { (it.progress?.percentage ?: 0.0) < 100.0 },
                "booksInProgressCount" to seriesBooks.count { val p = it.progress?.percentage ?: 0.0; p > 0 && p < 100 },
                "metadata" to mapOf(
                    "status" to "ONGOING",
                    "title" to name,
                    "titleLock" to false,
                    "titleSort" to name,
                    "titleSortLock" to false,
                    "summary" to "",
                    "summaryLock" to false,
                    "readingDirection" to "LEFT_TO_RIGHT",
                    "readingDirectionLock" to false,
                    "publisher" to "",
                    "publisherLock" to false,
                    "ageRating" to null,
                    "ageRatingLock" to false,
                    "language" to "",
                    "languageLock" to false,
                    "genres" to emptyList<String>(),
                    "genresLock" to false,
                    "tags" to emptyList<String>(),
                    "tagsLock" to false,
                    "totalBookCount" to null,
                    "authors" to emptyList<Map<String, String>>(),
                    "authorsLock" to false,
                    "links" to emptyList<Map<String, String>>(),
                ),
                "created" to (seriesBooks.first().addedAt),
                "lastModified" to (seriesBooks.first().addedAt),
            )
        }
        return mapOf(
            "content" to content,
            "pageable" to mapOf("sort" to mapOf("sorted" to false, "unsorted" to true), "offset" to 0, "pageNumber" to 0, "pageSize" to content.size),
            "totalElements" to content.size,
            "totalPages" to 1,
            "last" to true,
            "first" to true,
            "size" to content.size,
            "number" to 0,
            "numberOfElements" to content.size,
            "empty" to content.isEmpty(),
        )
    }

    fun getSeriesById(userId: UUID, seriesId: String): Map<String, Any>? {
        val books = bookService.getBooks(userId, null, page = 1, pageSize = 100).getBooks()
        val seriesEntry = books.filter { it.series != null }
            .groupBy { it.series!! }
            .entries.firstOrNull { (name, _) -> "series-${name.hashCode()}" == seriesId }
            ?: return null
        val name = seriesEntry.key
        val seriesBooks = seriesEntry.value
        return mapOf(
            "id" to seriesId,
            "libraryId" to seriesBooks.first().libraryId,
            "name" to name,
            "booksCount" to seriesBooks.size,
            "metadata" to mapOf("status" to "ONGOING", "title" to name),
            "created" to seriesBooks.first().addedAt,
            "lastModified" to seriesBooks.first().addedAt,
        )
    }

    fun listBooks(userId: UUID, libraryId: String?, seriesId: String?): Map<String, Any> {
        val books = bookService.getBooks(userId, libraryId, page = 1, pageSize = 100).getBooks()
        val filtered = if (seriesId != null) {
            books.filter { "series-${it.series?.hashCode()}" == seriesId }
        } else books
        val content = filtered.map { book -> toKomgaBook(book) }
        return mapOf(
            "content" to content,
            "totalElements" to content.size,
            "totalPages" to 1,
            "last" to true,
            "first" to true,
            "size" to content.size,
            "number" to 0,
            "numberOfElements" to content.size,
            "empty" to content.isEmpty(),
        )
    }

    fun getBook(userId: UUID, bookId: UUID): Map<String, Any>? {
        val book = bookService.getBook(userId, bookId) ?: return null
        return toKomgaBook(book)
    }

    private fun toKomgaBook(book: org.booktower.models.BookDto): Map<String, Any> {
        val ext = book.filePath?.substringAfterLast('.', "epub")?.lowercase() ?: "epub"
        val mediaType = when (ext) {
            "pdf"  -> "application/pdf"
            "cbz"  -> "application/zip"
            "cbr"  -> "application/x-rar-compressed"
            else   -> "application/epub+zip"
        }
        val pct = book.progress?.percentage ?: 0.0
        val readStatus = when {
            pct >= 100.0 -> "READ"
            pct > 0 -> "IN_PROGRESS"
            else -> "UNREAD"
        }
        return mapOf(
            "id" to book.id,
            "seriesId" to "series-${book.series?.hashCode() ?: 0}",
            "seriesTitle" to (book.series ?: book.title),
            "libraryId" to book.libraryId,
            "name" to book.title,
            "url" to "/api/books/${book.id}/file",
            "number" to (book.seriesIndex ?: 1.0),
            "created" to book.addedAt,
            "lastModified" to book.addedAt,
            "fileLastModified" to book.addedAt,
            "sizeBytes" to book.fileSize,
            "size" to "${book.fileSize / 1024} KB",
            "media" to mapOf(
                "status" to "READY",
                "mediaType" to mediaType,
                "pagesCount" to (book.pageCount ?: 0),
                "comment" to "",
                "active" to true,
            ),
            "metadata" to mapOf(
                "title" to book.title,
                "titleLock" to false,
                "summary" to (book.description ?: ""),
                "summaryLock" to false,
                "number" to (book.seriesIndex?.toString() ?: "1"),
                "numberLock" to false,
                "numberSort" to (book.seriesIndex ?: 1.0),
                "numberSortLock" to false,
                "releaseDate" to book.publishedDate,
                "releaseDateLock" to false,
                "authors" to listOfNotNull(book.author?.let { mapOf("name" to it, "role" to "writer") }),
                "authorsLock" to false,
                "tags" to book.tags,
                "tagsLock" to false,
                "isbn" to (book.isbn ?: ""),
                "isbnLock" to false,
                "links" to emptyList<Map<String, String>>(),
                "created" to book.addedAt,
                "lastModified" to book.addedAt,
            ),
            "readProgress" to mapOf(
                "page" to (book.progress?.currentPage ?: 0),
                "completed" to (pct >= 100.0),
                "readDate" to book.progress?.lastReadAt,
                "created" to book.addedAt,
                "lastModified" to (book.progress?.lastReadAt ?: book.addedAt),
                "deviceId" to null,
                "deviceName" to null,
            ),
            "deleted" to false,
            "fileHash" to "",
            "availableForDownload" to true,
        )
    }
}
