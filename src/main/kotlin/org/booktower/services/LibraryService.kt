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

class LibraryService(
    private val jdbi: Jdbi,
    private val storageConfig: StorageConfig,
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

