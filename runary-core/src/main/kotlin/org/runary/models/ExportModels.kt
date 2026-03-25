package org.runary.models

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
