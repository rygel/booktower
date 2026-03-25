package org.runary.services

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val dropLogger = LoggerFactory.getLogger("runary.BookDropService")

private val BOOK_EXTENSIONS = setOf("pdf", "epub", "mobi", "cbz", "cbr", "fb2", "azw3", "djvu")

data class DroppedFile(
    val filename: String,
    val sizeBytes: Long,
    val droppedAt: String,
    val format: String,
)

/**
 * Manages a "BookDrop" staging folder where users can drop book files.
 * Files in [dropPath] are listed as pending; the user can import them into a library or discard them.
 */
class BookDropService(
    private val bookService: BookService,
    val dropPath: String,
) {
    private val dropDir get() =
        File(dropPath).also {
            if (!it.exists() && !it.mkdirs()) dropLogger.warn("Could not create directory: ${it.absolutePath}")
        }

    /** List all files currently in the drop folder. */
    fun listPending(): List<DroppedFile> =
        try {
            (dropDir.listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension.lowercase() in BOOK_EXTENSIONS }
                .sortedBy { it.lastModified() }
                .map { f ->
                    DroppedFile(
                        filename = f.name,
                        sizeBytes = f.length(),
                        droppedAt =
                            Instant
                                .ofEpochMilli(f.lastModified())
                                .atOffset(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        format = f.extension.lowercase(),
                    )
                }
        } catch (e: Exception) {
            dropLogger.warn("listPending failed: ${e.message}")
            emptyList()
        }

    /**
     * Import [filename] from the drop folder into [libraryDir].
     * Creates a book record and moves the file.
     * Returns the new book ID, or null if the file was not found.
     */
    fun import(
        userId: UUID,
        filename: String,
        libraryId: String,
        libraryDir: String,
    ): String? {
        val safeFilename = File(filename).name // strip any path traversal
        val sourceFile = File(dropDir, safeFilename)
        if (!sourceFile.exists() || !sourceFile.isFile) return null

        val destDir =
            File(
                libraryDir,
            ).also { if (!it.exists() && !it.mkdirs()) dropLogger.warn("Could not create directory: ${it.absolutePath}") }
        val destFile = File(destDir, safeFilename)

        // Move file into the library directory
        Files.move(
            sourceFile.toPath(),
            destFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )

        // Extract title from filename (strip extension)
        val title =
            safeFilename
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()

        // Create book record
        val bookId = UUID.randomUUID()
        bookService.createBookFromDrop(userId, bookId, title, libraryId, destFile.absolutePath, destFile.length())
        dropLogger.info("Imported '$safeFilename' into library $libraryId as book $bookId")
        return bookId.toString()
    }

    /**
     * Discard (delete) [filename] from the drop folder.
     * Returns false if the file was not found.
     */
    fun discard(filename: String): Boolean {
        val safeFilename = File(filename).name
        val file = File(dropDir, safeFilename)
        if (!file.exists()) return false
        val deleted = file.delete()
        if (deleted) dropLogger.info("Discarded dropped file: $safeFilename")
        return deleted
    }
}
