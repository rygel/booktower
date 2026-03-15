package org.booktower.services

import org.booktower.config.StorageConfig
import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.LibraryService")

private val SCANNABLE_EXTENSIONS = setOf("pdf", "epub", "mobi", "cbz", "cbr", "fb2")

class LibraryService(
    private val jdbi: Jdbi,
    private val storageConfig: StorageConfig,
    private val pdfMetadataService: PdfMetadataService,
) {
    fun getLibraries(userId: UUID): List<LibraryDto> {
        return jdbi.withHandle<List<LibraryDto>, Exception> { handle ->
            handle.createQuery("SELECT * FROM libraries WHERE user_id = ? ORDER BY name")
                .bind(0, userId.toString())
                .map { row -> mapLibrary(handle, row) }
                .list()
        }
    }

    fun getLibrary(
        userId: UUID,
        libraryId: UUID,
    ): LibraryDto? {
        return jdbi.withHandle<LibraryDto?, Exception> { handle ->
            handle.createQuery("SELECT * FROM libraries WHERE user_id = ? AND id = ?")
                .bind(0, userId.toString())
                .bind(1, libraryId.toString())
                .map { row -> mapLibrary(handle, row) }
                .firstOrNull()
        }
    }

    private fun mapLibrary(
        handle: org.jdbi.v3.core.Handle,
        row: RowView,
    ): LibraryDto {
        val libId = UUID.fromString(row.getColumn("id", String::class.java))
        val count =
            handle.createQuery("SELECT COUNT(*) FROM books WHERE library_id = ?")
                .bind(0, libId.toString())
                .mapTo(java.lang.Integer::class.java)
                .first()?.toInt() ?: 0

        return LibraryDto(
            id = libId.toString(),
            name = row.getColumn("name", String::class.java),
            path = row.getColumn("path", String::class.java),
            bookCount = count,
            createdAt = row.getColumn("created_at", String::class.java),
        )
    }

    fun createLibrary(
        userId: UUID,
        request: CreateLibraryRequest,
    ): LibraryDto {
        val now = Instant.now()
        val libId = UUID.randomUUID()

        val dir = File(request.path)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create library directory: ${request.path}")
        }

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("INSERT INTO libraries (id, user_id, name, path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
                .bind(0, libId.toString())
                .bind(1, userId.toString())
                .bind(2, request.name)
                .bind(3, request.path)
                .bind(4, now.toString())
                .bind(5, now.toString())
                .execute()
        }

        logger.info("Library created: ${request.name}")

        return LibraryDto(libId.toString(), request.name, request.path, 0, now.toString())
    }

    fun renameLibrary(userId: UUID, libraryId: UUID, request: UpdateLibraryRequest): LibraryDto? {
        val library = getLibrary(userId, libraryId) ?: return null

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("UPDATE libraries SET name = ?, updated_at = ? WHERE id = ? AND user_id = ?")
                .bind(0, request.name)
                .bind(1, Instant.now().toString())
                .bind(2, libraryId.toString())
                .bind(3, userId.toString())
                .execute()
        }

        logger.info("Library renamed to: ${request.name}")
        return library.copy(name = request.name)
    }

    fun scanLibrary(userId: UUID, libraryId: UUID): ScanResult {
        val library = getLibrary(userId, libraryId)
            ?: return ScanResult(0, 0, 0, emptyList())

        val dir = File(library.path)
        if (!dir.exists() || !dir.isDirectory) {
            logger.warn("Library path does not exist or is not a directory: ${library.path}")
            return ScanResult(0, 0, 0, emptyList())
        }

        val existingPaths = jdbi.withHandle<Set<String>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT b.file_path FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ? AND b.file_path <> ''
                """,
            )
                .bind(0, userId.toString())
                .mapTo(String::class.java)
                .set()
        }

        var added = 0
        var skipped = 0
        var errors = 0
        val addedBooks = mutableListOf<BookDto>()

        dir.walkTopDown()
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
                    val title = file.nameWithoutExtension
                        .replace(Regex("[_\\-]+"), " ")
                        .trim()

                    jdbi.useHandle<Exception> { handle ->
                        handle.createUpdate(
                            """
                            INSERT INTO books (id, library_id, title, author, description, file_path, file_size, added_at, updated_at)
                            VALUES (?, ?, ?, NULL, NULL, ?, ?, ?, ?)
                            """,
                        )
                            .bind(0, bookId.toString())
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

                    val book = BookDto(
                        id = bookId.toString(),
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

        logger.info("Library deleted: ${lib.name}")
        return true
    }
}

