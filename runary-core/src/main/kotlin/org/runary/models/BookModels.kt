package org.runary.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant
import java.util.UUID

data class Book(
    val id: UUID,
    val libraryId: UUID,
    val title: String,
    val author: String?,
    val description: String?,
    val isbn: String?,
    val publisher: String?,
    val publishedDate: String?,
    val filePath: String,
    val fileSize: Long,
    val fileHash: String?,
    val pageCount: Int?,
    val coverPath: String?,
    val addedAt: Instant,
    val updatedAt: Instant,
)

enum class BookFormat {
    EBOOK,
    PHYSICAL,
    AUDIOBOOK,
    ;

    companion object {
        fun fromString(s: String?): BookFormat = values().firstOrNull { it.name.equals(s, ignoreCase = true) } ?: EBOOK
    }
}

enum class BookSortOrder(
    val sql: String,
    val label: String,
) {
    TITLE("b.title", "sort.title"),
    ADDED("b.added_at DESC, b.title", "sort.added"),
    AUTHOR("COALESCE(b.author, ''), b.title", "sort.author"),
    PUBLISHED_DATE("COALESCE(CAST(b.published_date AS VARCHAR), '') DESC, b.title", "sort.published.date"),
}

enum class ReadStatus(
    val label: String,
) {
    WANT_TO_READ("status.want.to.read"),
    READING("status.reading"),
    FINISHED("status.finished"),
}

data class BookDto(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val pageCount: Int?,
    val fileSize: Long,
    val addedAt: String,
    val progress: ReadingProgressDto?,
    val status: String? = null,
    val rating: Int? = null,
    val tags: List<String> = emptyList(),
    val isbn: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val filePath: String? = null,
    val readingDirection: String? = null,
    val authors: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val subtitle: String? = null,
    val language: String? = null,
    val contentRating: String? = null,
    val ageRating: String? = null,
    val moods: List<String> = emptyList(),
    val goodreadsId: String? = null,
    val hardcoverId: String? = null,
    val comicvineId: String? = null,
    val openlibraryId: String? = null,
    val googleBooksId: String? = null,
    val amazonId: String? = null,
    val audibleId: String? = null,
    val lockedFields: List<String> = emptyList(),
    // Comic-specific fields
    val issueNumber: String? = null,
    val volumeNumber: String? = null,
    val comicSeries: String? = null,
    val coverDate: String? = null,
    val storyArc: String? = null,
    val characters: List<String> = emptyList(),
    val teams: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val bookFormat: String = "EBOOK",
    val communityRating: Double? = null,
    val communityRatingCount: Int? = null,
    val communityRatingSource: String? = null,
    val contentSnippet: String? = null,
    val shareToken: String? = null,
)

data class ComicMetadataRequest(
    val issueNumber: String? = null,
    val volumeNumber: String? = null,
    val comicSeries: String? = null,
    val coverDate: String? = null,
    val storyArc: String? = null,
    val characters: List<String>? = null,
    val teams: List<String>? = null,
    val locations: List<String>? = null,
)

class BookListDto(
    books: List<BookDto>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
) {
    private val _books: List<BookDto> = books.toList()

    fun getBooks(): List<BookDto> = _books.toList()
}

data class BookFileDto(
    val id: String,
    val trackIndex: Int,
    val title: String?,
    val durationSec: Int?,
    val fileSize: Long,
    @JsonIgnore val filePath: String? = null,
)

data class BookFormatDto(
    val id: String,
    val bookId: String,
    val filePath: String,
    val fileSize: Long,
    val format: String,
    val isPrimary: Boolean,
    val label: String?,
    val addedAt: String,
)

data class AddBookFileRequest(
    val filePath: String,
    val label: String? = null,
    val isPrimary: Boolean = false,
)

data class CreateBookRequest(
    val title: String,
    val author: String?,
    val description: String?,
    val libraryId: String,
    val bookFormat: String? = null,
)

data class UpdateBookRequest(
    val title: String,
    val author: String?,
    val description: String?,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val subtitle: String? = null,
    val language: String? = null,
    val contentRating: String? = null,
    val ageRating: String? = null,
    val goodreadsId: String? = null,
    val hardcoverId: String? = null,
    val comicvineId: String? = null,
    val openlibraryId: String? = null,
    val googleBooksId: String? = null,
    val amazonId: String? = null,
    val audibleId: String? = null,
    /** For optimistic locking — if provided, update only applies when book's updatedAt matches */
    val expectedUpdatedAt: String? = null,
)

data class MergeBookRequest(
    val sourceId: String,
)

data class CommunityRatingDto(
    val rating: Double?,
    val count: Int?,
    val source: String?,
    val fetchedAt: String?,
)

data class FetchedMetadata(
    val title: String?,
    val author: String?,
    val description: String?,
    val isbn: String?,
    val publisher: String?,
    val publishedDate: String?,
    val openLibraryCoverId: Long? = null,
    val coverUrl: String? = null,
    val source: String? = null,
    val pageCount: Int? = null,
    val subtitle: String? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val series: String? = null,
    val seriesIndex: Double? = null,
    val narrator: String? = null,
    val durationSeconds: Int? = null,
)

data class SeriesDto(
    val name: String,
    val bookCount: Int,
    val coverUrl: String?,
)

data class AuthorDto(
    val name: String,
    val bookCount: Int,
    val coverUrl: String?,
    val readingCount: Int = 0,
    val finishedCount: Int = 0,
)

data class TagDto(
    val name: String,
    val bookCount: Int,
)

// ── Whispersync (linked books) ──────────────────────────────────────────────

data class LinkedBookDto(
    val id: String,
    val ebookId: String,
    val audioId: String,
    val ebookTitle: String,
    val audioTitle: String,
    val createdAt: String,
)

data class SyncPositionDto(
    val targetBookId: String,
    val targetTitle: String,
    val targetFormat: String,
    val chapterIndex: Int,
    val chapterLabel: String?,
    val positionInChapter: Double,
)

data class LinkBooksRequest(
    val linkedBookId: String,
)
