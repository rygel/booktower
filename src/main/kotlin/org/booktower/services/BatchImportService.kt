package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private val importLog = LoggerFactory.getLogger("booktower.BatchImportService")

private val IMPORTABLE_EXTENSIONS = setOf("epub", "pdf", "cbz", "cbr", "mobi", "fb2", "azw3", "djvu")

private val FORMAT_MAP =
    mapOf(
        "epub" to "EBOOK",
        "pdf" to "EBOOK",
        "mobi" to "EBOOK",
        "azw3" to "EBOOK",
        "fb2" to "EBOOK",
        "djvu" to "EBOOK",
        "cbz" to "COMIC",
        "cbr" to "COMIC",
    )

data class BatchImportResult(
    val queued: Int,
    val skipped: Int,
    val alreadyImported: Int,
)

data class BatchImportConfig(
    val maxConcurrency: Int = 2,
    val throttleMs: Long = 500,
    val maxFiles: Int = 1000,
    val maxFileSizeMb: Int = 500,
)

/**
 * Scans a directory for book files and batch-imports them into a library.
 *
 * Performance limits:
 * - Configurable thread pool (default 2 — won't saturate CPU)
 * - Throttle between files (default 500ms — won't overwhelm disk I/O)
 * - Max files per batch (default 1000)
 * - Max file size (default 500MB — skip huge files)
 * - Skips already-imported files (by absolute path match)
 */
class BatchImportService(
    private val jdbi: Jdbi,
    private val bookService: BookService,
    private val epubMetadataService: EpubMetadataService,
    private val pdfMetadataService: PdfMetadataService,
    private val backgroundTaskService: BackgroundTaskService,
    private val ftsService: FtsService?,
) {
    private val executor =
        Executors.newFixedThreadPool(2) { r ->
            Thread(r, "batch-import").also {
                it.isDaemon = true
                it.priority = Thread.MIN_PRIORITY
            }
        }

    /**
     * Scans [directoryPath] recursively for book files and imports them
     * into library [libraryId] owned by [userId].
     * Returns immediately with the count of queued files.
     */
    fun importDirectory(
        userId: UUID,
        libraryId: String,
        directoryPath: String,
        config: BatchImportConfig = BatchImportConfig(),
    ): BatchImportResult {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            return BatchImportResult(0, 0, 0)
        }

        // Verify library belongs to user
        val libExists =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM libraries WHERE id = ? AND user_id = ?")
                    .bind(0, libraryId)
                    .bind(1, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }
        if (!libExists) return BatchImportResult(0, 0, 0)

        // Find all importable files
        val maxSizeBytes = config.maxFileSizeMb.toLong() * 1024 * 1024
        val files =
            dir
                .walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in IMPORTABLE_EXTENSIONS }
                .filter { it.length() <= maxSizeBytes }
                .take(config.maxFiles)
                .toList()

        if (files.isEmpty()) return BatchImportResult(0, 0, 0)

        // Check which files are already imported (by absolute path)
        val existingPaths =
            jdbi.withHandle<Set<String>, Exception> { h ->
                h
                    .createQuery(
                        """
                    SELECT b.file_path FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.file_path IS NOT NULL
                    """,
                    ).bind(0, userId.toString())
                    .mapTo(String::class.java)
                    .toSet()
            }

        val toImport = files.filter { it.absolutePath !in existingPaths }
        val alreadyImported = files.size - toImport.size

        if (toImport.isEmpty()) return BatchImportResult(0, 0, alreadyImported)

        val imported = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val taskId =
            backgroundTaskService.start(
                userId,
                "batch-import",
                "Importing ${toImport.size} file(s) from ${dir.name}",
            )

        executor.submit {
            try {
                for (file in toImport) {
                    try {
                        importFile(userId, libraryId, file)
                        imported.incrementAndGet()
                    } catch (e: Exception) {
                        importLog.warn("Failed to import ${file.name}: ${e.message}")
                        skipped.incrementAndGet()
                    }
                    if (config.throttleMs > 0) Thread.sleep(config.throttleMs)
                }
                backgroundTaskService.complete(
                    taskId,
                    "${imported.get()} imported, ${skipped.get()} skipped",
                )
                importLog.info(
                    "Batch import complete from ${dir.absolutePath}: " +
                        "${imported.get()} imported, ${skipped.get()} skipped, $alreadyImported already existed",
                )
            } catch (e: Exception) {
                importLog.error("Batch import failed", e)
                backgroundTaskService.fail(taskId, e.message)
            }
        }

        return BatchImportResult(toImport.size, 0, alreadyImported)
    }

    private fun importFile(
        userId: UUID,
        libraryId: String,
        file: File,
    ) {
        val bookId = UUID.randomUUID()
        val ext = file.extension.lowercase()

        // Extract title from filename
        val title =
            file.nameWithoutExtension
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()

        // Create book record
        bookService.createBookFromDrop(userId, bookId, title, libraryId, file.absolutePath, file.length())

        // Set format
        val format = FORMAT_MAP[ext]
        if (format != null) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate("UPDATE books SET book_format = ? WHERE id = ?")
                    .bind(0, format)
                    .bind(1, bookId.toString())
                    .execute()
            }
        }

        // Extract metadata asynchronously (cover, author, ISBN)
        when (ext) {
            "epub" -> epubMetadataService.submitAsync(bookId.toString(), file)
            "pdf" -> pdfMetadataService.submitAsync(bookId.toString(), file)
        }

        // Enqueue for full-text search indexing
        if (ext in setOf("epub", "pdf")) {
            ftsService?.enqueue(bookId.toString())
        }

        importLog.debug("Imported: ${file.name} → $bookId ($ext)")
    }
}
