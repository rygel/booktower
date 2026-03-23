package org.booktower.services

import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID

private val PATTERN_TOKEN_RE = Regex("\\{(title|author|year|publisher|isbn|ext|series|seriesIndex)}")
private val UNSAFE_PATH_CHARS = Regex("[\\\\:*?\"<>|\\x00-\\x1f]")

private val VALID_SORT_FIELDS = setOf("title", "author", "added_at", "updated_at", "file_size", "page_count")
private val VALID_METADATA_SOURCES = setOf("openlibrary", "googlebooks", "hardcover", "comicvine", "audible")

private val logger = LoggerFactory.getLogger("booktower.LibraryService")

private val SCANNABLE_EXTENSIONS =
    setOf("pdf", "epub", "mobi", "azw3", "cbz", "cbr", "fb2", "djvu", "mp3", "m4b", "m4a", "ogg", "flac", "aac")

class LibraryService(
    private val jdbi: Jdbi,
    private val pdfMetadataService: PdfMetadataService,
    private val libraryAccessService: LibraryAccessService? = null,
    private val ftsService: org.booktower.services.FtsService? = null,
    private val comicPageHashService: ComicPageHashService? = null,
) {
    /** Cache library listings per user — invalidated on create/delete/rename. */
    private val libraryCache =
        com.github.benmanes.caffeine.cache.Caffeine
            .newBuilder()
            .maximumSize(500)
            .expireAfterWrite(10, java.util.concurrent.TimeUnit.MINUTES)
            .build<UUID, List<LibraryDto>>()

    fun getLibraries(userId: UUID): List<LibraryDto> {
        libraryCache.getIfPresent(userId)?.let { return it }
        val accessibleIds = libraryAccessService?.getAccessibleLibraryIds(userId)
        return jdbi
            .withHandle<List<LibraryDto>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                        SELECT l.*, COALESCE(bc.cnt, 0) AS book_count
                        FROM libraries l
                        LEFT JOIN (SELECT library_id, COUNT(*) AS cnt FROM books GROUP BY library_id) bc
                            ON bc.library_id = l.id
                        WHERE l.user_id = ?
                        ORDER BY l.name
                        """,
                    ).bind(0, userId.toString())
                    .map { row -> mapLibraryFromRow(row) }
                    .list()
            }.let { all ->
                if (accessibleIds == null) {
                    all
                } else {
                    all.filter { it.id in accessibleIds }
                }
            }.also { libraryCache.put(userId, it) }
    }

    fun getLibrary(
        userId: UUID,
        libraryId: UUID,
    ): LibraryDto? {
        val accessibleIds = libraryAccessService?.getAccessibleLibraryIds(userId)
        if (accessibleIds != null && libraryId.toString() !in accessibleIds) return null
        return jdbi.withHandle<LibraryDto?, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT l.*, COALESCE(bc.cnt, 0) AS book_count
                    FROM libraries l
                    LEFT JOIN (SELECT library_id, COUNT(*) AS cnt FROM books GROUP BY library_id) bc
                        ON bc.library_id = l.id
                    WHERE l.user_id = ? AND l.id = ?
                    """,
                ).bind(0, userId.toString())
                .bind(1, libraryId.toString())
                .map { row -> mapLibraryFromRow(row) }
                .firstOrNull()
        }
    }

    private fun mapLibraryFromRow(row: RowView): LibraryDto =
        LibraryDto(
            id = row.getColumn("id", String::class.java),
            name = row.getColumn("name", String::class.java),
            path = row.getColumn("path", String::class.java),
            bookCount = (row.getColumn("book_count", java.lang.Integer::class.java) ?: 0).toInt(),
            createdAt = row.getColumn("created_at", String::class.java),
        )

    fun createLibrary(
        userId: UUID,
        request: CreateLibraryRequest,
    ): LibraryDto {
        val now = Instant.now()
        val libId = UUID.randomUUID()

        val dir = File(request.path)
        if (!dir.exists() && !dir.mkdirs()) {
            error("Failed to create library directory: ${request.path}")
        }

        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("INSERT INTO libraries (id, user_id, name, path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
                .bind(0, libId.toString())
                .bind(1, userId.toString())
                .bind(2, request.name)
                .bind(3, request.path)
                .bind(4, now.toString())
                .bind(5, now.toString())
                .execute()
        }

        libraryCache.invalidate(userId)
        logger.info("Library created: ${request.name}")

        return LibraryDto(libId.toString(), request.name, request.path, 0, now.toString())
    }

    fun renameLibrary(
        userId: UUID,
        libraryId: UUID,
        request: UpdateLibraryRequest,
    ): LibraryDto? {
        val library = getLibrary(userId, libraryId) ?: return null

        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE libraries SET name = ?, updated_at = ? WHERE id = ? AND user_id = ?")
                .bind(0, request.name)
                .bind(1, Instant.now().toString())
                .bind(2, libraryId.toString())
                .bind(3, userId.toString())
                .execute()
        }

        libraryCache.invalidate(userId)
        logger.info("Library renamed to: ${request.name}")
        return library.copy(name = request.name)
    }

    fun scanLibrary(
        userId: UUID,
        libraryId: UUID,
    ): ScanResult {
        val library =
            getLibrary(userId, libraryId)
                ?: return ScanResult(0, 0, 0, emptyList())

        val dir = File(library.path)
        if (!dir.exists() || !dir.isDirectory) {
            logger.warn("Library path does not exist or is not a directory: ${library.path}")
            return ScanResult(0, 0, 0, emptyList())
        }

        val existingPaths =
            jdbi.withHandle<Set<String>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.file_path FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ? AND b.file_path <> ''
                """,
                    ).bind(0, userId.toString())
                    .mapTo(String::class.java)
                    .set()
            }

        var added = 0
        var skipped = 0
        var errors = 0
        val addedBooks = mutableListOf<BookDto>()

        dir
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in SCANNABLE_EXTENSIONS }
            .forEach { file ->
                val absolutePath = file.absolutePath
                if (absolutePath in existingPaths) {
                    skipped++
                    return@forEach
                }
                try {
                    val now = Instant.now()
                    val bookId = UUID.randomUUID()
                    val title =
                        file.nameWithoutExtension
                            .replace(Regex("[_\\-]+"), " ")
                            .trim()

                    jdbi.useHandle<Exception> { handle ->
                        handle
                            .createUpdate(
                                """
                            INSERT INTO books (id, library_id, title, author, description, file_path, file_size, added_at, updated_at)
                            VALUES (?, ?, ?, NULL, NULL, ?, ?, ?, ?)
                            """,
                            ).bind(0, bookId.toString())
                            .bind(1, library.id)
                            .bind(2, title)
                            .bind(3, absolutePath)
                            .bind(4, file.length())
                            .bind(5, now.toString())
                            .bind(6, now.toString())
                            .execute()
                    }

                    if (file.extension.lowercase() == "pdf") {
                        pdfMetadataService.submitAsync(bookId.toString(), file)
                    }

                    // Apply sidecar metadata (.opf / .nfo) if present
                    val sidecar = SidecarMetadataService.read(absolutePath)
                    if (sidecar != null) {
                        val updates = mutableListOf<String>()
                        val bindings = mutableListOf<Any?>()
                        sidecar.title?.let {
                            updates += "title = ?"
                            bindings += it
                        }
                        sidecar.author?.let {
                            updates += "author = ?"
                            bindings += it
                        }
                        sidecar.description?.let {
                            updates += "description = ?"
                            bindings += it
                        }
                        sidecar.isbn?.let {
                            updates += "isbn = ?"
                            bindings += it
                        }
                        sidecar.publisher?.let {
                            updates += "publisher = ?"
                            bindings += it
                        }
                        if (updates.isNotEmpty()) {
                            bindings += bookId.toString()
                            jdbi.useHandle<Exception> { h ->
                                val stmt = h.createUpdate("UPDATE books SET ${updates.joinToString(", ")} WHERE id = ?")
                                bindings.forEachIndexed { idx, v -> stmt.bind(idx, v) }
                                stmt.execute()
                            }
                            logger.info("Sidecar metadata applied from ${sidecar.source} for $absolutePath")
                        }
                    }

                    val book =
                        BookDto(
                            id = bookId.toString(),
                            libraryId = library.id,
                            title = title,
                            author = null,
                            description = null,
                            coverUrl = null,
                            pageCount = null,
                            fileSize = file.length(),
                            addedAt = now.toString(),
                            progress = null,
                        )
                    addedBooks += book
                    ftsService?.enqueue(bookId.toString())
                    if (file.extension.lowercase() in setOf("cbz", "cbr")) {
                        comicPageHashService?.enqueue(bookId.toString())
                    }
                    added++
                    logger.info("Scan imported: $absolutePath as book $title")
                } catch (e: Exception) {
                    logger.warn("Scan failed to import ${file.name}: ${e.message}")
                    errors++
                }
            }

        logger.info("Library scan complete for '${library.name}': added=$added skipped=$skipped errors=$errors")
        return ScanResult(added, skipped, errors, addedBooks)
    }

    fun deleteLibrary(
        userId: UUID,
        libraryId: UUID,
    ): Boolean {
        val lib = getLibrary(userId, libraryId) ?: return false

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM libraries WHERE id = ?").bind(0, libraryId.toString()).execute()
        }

        libraryCache.invalidate(userId)
        logger.info("Library deleted: ${lib.name}")
        return true
    }

    fun getIconPath(
        userId: UUID,
        libraryId: UUID,
    ): String? {
        getLibrary(userId, libraryId) ?: return null
        return jdbi.withHandle<String?, Exception> { h ->
            h
                .createQuery("SELECT icon_path FROM libraries WHERE id = ?")
                .bind(0, libraryId.toString())
                .mapTo(String::class.java)
                .firstOrNull()
        }
    }

    fun updateIconPath(
        userId: UUID,
        libraryId: UUID,
        iconPath: String?,
    ): Boolean {
        getLibrary(userId, libraryId) ?: return false
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE libraries SET icon_path = ?, updated_at = ? WHERE id = ? AND user_id = ?")
                .bind(0, iconPath)
                .bind(1, Instant.now().toString())
                .bind(2, libraryId.toString())
                .bind(3, userId.toString())
                .execute()
        }
        return true
    }

    fun getSettings(
        userId: UUID,
        libraryId: UUID,
    ): LibrarySettings? {
        getLibrary(userId, libraryId) ?: return null
        return jdbi.withHandle<LibrarySettings, Exception> { h ->
            val row =
                h
                    .createQuery(
                        "SELECT format_allowlist, metadata_source, default_sort, file_naming_pattern FROM libraries WHERE id = ?",
                    ).bind(0, libraryId.toString())
                    .mapToMap()
                    .firstOrNull() ?: return@withHandle LibrarySettings(null, null, null, emptyList(), null)

            val allowlist =
                (row["format_allowlist"] as? String)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }

            val paths =
                h
                    .createQuery("SELECT path FROM library_paths WHERE library_id = ? ORDER BY added_at")
                    .bind(0, libraryId.toString())
                    .mapTo(String::class.java)
                    .list()

            LibrarySettings(
                formatAllowlist = allowlist,
                metadataSource = row["metadata_source"] as? String,
                defaultSort = row["default_sort"] as? String,
                additionalPaths = paths,
                fileNamingPattern = row["file_naming_pattern"] as? String,
            )
        }
    }

    fun updateSettings(
        userId: UUID,
        libraryId: UUID,
        request: UpdateLibrarySettingsRequest,
    ): LibrarySettings? {
        getLibrary(userId, libraryId) ?: return null

        request.metadataSource?.let {
            require(it in VALID_METADATA_SOURCES) { "Unknown metadata source: $it" }
        }
        request.defaultSort?.let {
            require(it in VALID_SORT_FIELDS) { "Invalid sort field: $it" }
        }

        val allowlistStr =
            request.formatAllowlist
                ?.map { it.lowercase().trimStart('.') }
                ?.filter { it.isNotBlank() }
                ?.joinToString(",")

        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "UPDATE libraries SET format_allowlist = ?, metadata_source = ?, default_sort = ?, file_naming_pattern = ?, updated_at = ? WHERE id = ? AND user_id = ?",
                ).bind(0, allowlistStr)
                .bind(1, request.metadataSource)
                .bind(2, request.defaultSort)
                .bind(3, request.fileNamingPattern)
                .bind(4, Instant.now().toString())
                .bind(5, libraryId.toString())
                .bind(6, userId.toString())
                .execute()

            if (request.additionalPaths != null) {
                h
                    .createUpdate("DELETE FROM library_paths WHERE library_id = ?")
                    .bind(0, libraryId.toString())
                    .execute()
                val now = Instant.now().toString()
                for (path in request.additionalPaths) {
                    if (path.isBlank()) continue
                    h
                        .createUpdate("INSERT INTO library_paths (id, library_id, path, added_at) VALUES (?, ?, ?, ?)")
                        .bind(0, UUID.randomUUID().toString())
                        .bind(1, libraryId.toString())
                        .bind(2, path)
                        .bind(3, now)
                        .execute()
                }
            }
        }

        return getSettings(userId, libraryId)
    }

    /** All paths to scan for this library: primary + additional. */
    fun allScanPaths(
        userId: UUID,
        libraryId: UUID,
    ): List<String> {
        val library = getLibrary(userId, libraryId) ?: return emptyList()
        val extra =
            jdbi.withHandle<List<String>, Exception> { h ->
                h
                    .createQuery("SELECT path FROM library_paths WHERE library_id = ? ORDER BY added_at")
                    .bind(0, libraryId.toString())
                    .mapTo(String::class.java)
                    .list()
            }
        return listOf(library.path) + extra
    }

    /**
     * Rename/move all book files in a library to match the configured file_naming_pattern.
     * Pattern tokens: {title}, {author}, {year}, {publisher}, {isbn}, {ext}, {series}, {seriesIndex}.
     * Files are moved within the library's primary path. Sub-directories are created as needed.
     * Returns an [OrganizeResult] summarising what happened.
     */
    fun organizeFiles(
        userId: UUID,
        libraryId: UUID,
    ): OrganizeResult {
        val library = getLibrary(userId, libraryId) ?: return OrganizeResult(0, 0, 0, emptyList())
        val settings = getSettings(userId, libraryId)
        val pattern =
            settings?.fileNamingPattern
                ?: return OrganizeResult(0, 0, 0, listOf("No file_naming_pattern configured for this library"))

        val books =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
                h
                    .createQuery(
                        """
                SELECT id, title, author, publisher, published_date, isbn, file_path
                FROM books WHERE library_id = ? AND file_path IS NOT NULL AND file_path <> ''
                """,
                    ).bind(0, libraryId.toString())
                    .mapToMap()
                    .list()
            }

        var moved = 0
        var skipped = 0
        var errors = 0
        val details = mutableListOf<String>()
        val libraryRoot = File(library.path)

        for (book in books) {
            val currentPath = book["file_path"] as? String ?: continue
            val currentFile = File(currentPath)
            if (!currentFile.exists()) {
                skipped++
                continue
            }

            val ext = currentFile.extension
            val title = sanitizeSegment(book["title"] as? String ?: "Unknown")
            val author = sanitizeSegment(book["author"] as? String ?: "Unknown")
            val year = (book["published_date"] as? String)?.take(4) ?: "Unknown"
            val publisher = sanitizeSegment(book["publisher"] as? String ?: "Unknown")
            val isbn = sanitizeSegment(book["isbn"] as? String ?: "")

            val resolved =
                pattern
                    .replace("{title}", title)
                    .replace("{author}", author)
                    .replace("{year}", year)
                    .replace("{publisher}", publisher)
                    .replace("{isbn}", isbn)
                    .replace("{ext}", ext)
                    .replace("{series}", "")
                    .replace("{seriesIndex}", "")

            val targetRelative = if (resolved.endsWith(".$ext") || ext.isEmpty()) resolved else "$resolved.$ext"
            val targetFile = File(libraryRoot, targetRelative)

            if (targetFile.absolutePath == currentFile.absolutePath) {
                skipped++
                continue
            }

            try {
                targetFile.parentFile?.let {
                    if (!it.mkdirs() && !it.exists()) {
                        logger.warn(
                            "Could not create directory: ${it.absolutePath}",
                        )
                    }
                }
                if (currentFile.renameTo(targetFile)) {
                    jdbi.useHandle<Exception> { h ->
                        h
                            .createUpdate("UPDATE books SET file_path = ?, updated_at = ? WHERE id = ?")
                            .bind(0, targetFile.absolutePath)
                            .bind(1, Instant.now().toString())
                            .bind(2, book["id"].toString())
                            .execute()
                    }
                    details += "${currentFile.name} → $targetRelative"
                    moved++
                } else {
                    errors++
                    details += "Failed to move: ${currentFile.name}"
                }
            } catch (e: Exception) {
                errors++
                details += "Error moving ${currentFile.name}: ${e.message}"
                logger.warn("organizeFiles: error moving ${currentFile.name}", e)
            }
        }

        logger.info("organizeFiles for '${library.name}': moved=$moved skipped=$skipped errors=$errors")
        return OrganizeResult(moved, skipped, errors, details)
    }

    private fun sanitizeSegment(value: String): String =
        value
            .replace(UNSAFE_PATH_CHARS, "_")
            .trim()
            .take(120)
            .trimEnd('.')
}
