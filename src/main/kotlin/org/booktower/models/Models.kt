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

data class BookDto(
    val id: String,
    val title: String,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val pageCount: Int?,
    val fileSize: Long,
    val addedAt: String,
    val progress: ReadingProgressDto?,
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
