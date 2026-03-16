package org.booktower.models

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isAdmin: Boolean = false,
)

data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: String,
    val isAdmin: Boolean,
)

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val user: UserDto,
)

data class Library(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val path: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LibraryDto(
    val id: String,
    val name: String,
    val path: String,
    val bookCount: Int = 0,
    val createdAt: String,
)

data class CreateLibraryRequest(
    val name: String,
    val path: String,
)

data class UpdateLibraryRequest(
    val name: String,
)

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

enum class BookSortOrder(val sql: String, val label: String) {
    TITLE("b.title", "sort.title"),
    ADDED("b.added_at DESC, b.title", "sort.added"),
    AUTHOR("COALESCE(b.author, ''), b.title", "sort.author"),
    PUBLISHED_DATE("COALESCE(b.published_date, '') DESC, b.title", "sort.published.date"),
}

enum class ReadStatus(val label: String) {
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

data class CreateBookRequest(
    val title: String,
    val author: String?,
    val description: String?,
    val libraryId: String,
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
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class ChangeEmailRequest(
    val currentPassword: String,
    val newEmail: String,
)

data class ReadingProgress(
    val id: UUID,
    val userId: UUID,
    val bookId: UUID,
    val currentPage: Int,
    val totalPages: Int?,
    val percentage: Double?,
    val lastReadAt: Instant,
    val createdAt: Instant,
)

data class ReadingProgressDto(
    val currentPage: Int,
    val totalPages: Int?,
    val percentage: Double?,
    val lastReadAt: String,
)

data class ReadingSessionDto(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val startPage: Int,
    val endPage: Int,
    val pagesRead: Int,
    val sessionAt: String,
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

data class UpdateProgressRequest(
    val currentPage: Int,
)

data class Bookmark(
    val id: UUID,
    val userId: UUID,
    val bookId: UUID,
    val page: Int,
    val title: String?,
    val note: String?,
    val createdAt: Instant,
)

data class BookmarkDto(
    val id: String,
    val page: Int,
    val title: String?,
    val note: String?,
    val createdAt: String,
)

data class CreateBookmarkRequest(
    val bookId: String,
    val page: Int,
    val title: String?,
    val note: String?,
)

data class ErrorResponse(
    val error: String,
    val message: String,
)

data class SuccessResponse(
    val message: String,
)

data class Language(
    val code: String,
    val name: String,
)

data class ThemePreference(
    val theme: String,
)

data class LanguagePreference(
    val lang: String,
)

data class ScanResult(
    val added: Int,
    val skipped: Int,
    val errors: Int,
    val books: List<BookDto>,
)

data class UserAdminDto(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: String,
    val isAdmin: Boolean,
)

data class SetAdminRequest(
    val isAdmin: Boolean,
)

data class FetchedMetadata(
    val title: String?,
    val author: String?,
    val description: String?,
    val isbn: String?,
    val publisher: String?,
    val publishedDate: String?,
    val openLibraryCoverId: Long?,
)

data class AnnotationDto(
    val id: String,
    val bookId: String,
    val page: Int,
    val selectedText: String,
    val color: String,
    val createdAt: String,
)

enum class ShelfRuleType { STATUS, TAG, RATING_GTE }

data class MagicShelfDto(
    val id: String,
    val name: String,
    val ruleType: ShelfRuleType,
    val ruleValue: String?,
    val bookCount: Int = 0,
    val createdAt: String,
)

data class CreateMagicShelfRequest(
    val name: String,
    val ruleType: ShelfRuleType,
    val ruleValue: String?,
)

// ── API Tokens ─────────────────────────────────────────────────────────────────

data class ApiTokenDto(
    val id: String,
    val name: String,
    val createdAt: String,
    val lastUsedAt: String?,
)

data class CreateApiTokenRequest(
    val name: String,
)

data class CreatedApiTokenResponse(
    val id: String,
    val name: String,
    val token: String,  // shown only once
    val createdAt: String,
)

// ── Export ────────────────────────────────────────────────────────────────────

data class BookmarkExportDto(
    val page: Int,
    val title: String?,
    val note: String?,
    val createdAt: String,
)

data class ProgressExportDto(
    val currentPage: Int,
    val totalPages: Int?,
    val percentage: Double?,
    val lastReadAt: String,
)

data class BookExportDto(
    val title: String,
    val author: String?,
    val description: String?,
    val series: String?,
    val seriesIndex: Double?,
    val status: String?,
    val rating: Int?,
    val tags: List<String>,
    val progress: ProgressExportDto?,
    val bookmarks: List<BookmarkExportDto>,
    val addedAt: String,
)

data class LibraryExportDto(
    val name: String,
    val path: String,
    val books: List<BookExportDto>,
    val createdAt: String,
)

data class UserExportDto(
    val username: String,
    val email: String,
    val memberSince: String,
    val libraries: List<LibraryExportDto>,
)

// ── Scan jobs (in-memory) ─────────────────────────────────────────────────────

enum class ScanJobState { RUNNING, DONE, FAILED }

data class ScanJobStatus(
    val jobId: String,
    val libraryId: String,
    val state: ScanJobState,
    val added: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
    val message: String? = null,
)
