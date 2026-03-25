package org.runary.models

import java.time.Instant
import java.util.UUID

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

data class ListenSessionDto(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val startPosSec: Int,
    val endPosSec: Int,
    val secondsListened: Int,
    val sessionAt: String,
)

data class ListenProgressDto(
    val bookId: String,
    val positionSec: Int,
    val totalSec: Int?,
    val updatedAt: String,
)

data class UpdateProgressRequest(
    val currentPage: Int,
)

// ── Bookmarks & Annotations ────────────────────────────────────────────────

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

data class AnnotationDto(
    val id: String,
    val bookId: String,
    val page: Int,
    val selectedText: String,
    val color: String,
    val createdAt: String,
    val shared: Boolean = false,
    val note: String? = null,
    val username: String? = null,
)

// ── Smart Shelves ──────────────────────────────────────────────────────────

enum class ShelfRuleType { STATUS, TAG, RATING_GTE }

data class MagicShelfDto(
    val id: String,
    val name: String,
    val ruleType: ShelfRuleType,
    val ruleValue: String?,
    val bookCount: Int = 0,
    val createdAt: String,
    val isPublic: Boolean = false,
    val shareToken: String? = null,
)

data class PublicShelfDto(
    val name: String,
    val shareToken: String,
    val books: List<BookDto>,
)

data class CreateMagicShelfRequest(
    val name: String,
    val ruleType: ShelfRuleType,
    val ruleValue: String?,
)
