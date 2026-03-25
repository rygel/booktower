package org.runary.models

import java.time.Instant
import java.util.UUID

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

data class LibrarySettings(
    val formatAllowlist: List<String>?,
    val metadataSource: String?,
    val defaultSort: String?,
    val additionalPaths: List<String>,
    val fileNamingPattern: String?,
)

data class UpdateLibrarySettingsRequest(
    val formatAllowlist: List<String>?,
    val metadataSource: String?,
    val defaultSort: String?,
    val additionalPaths: List<String>?,
    val fileNamingPattern: String? = null,
)

data class OrganizeResult(
    val moved: Int,
    val skipped: Int,
    val errors: Int,
    val details: List<String>,
)

data class ScanResult(
    val added: Int,
    val skipped: Int,
    val errors: Int,
    val books: List<BookDto>,
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
