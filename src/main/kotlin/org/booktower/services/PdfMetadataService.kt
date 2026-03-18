package org.booktower.services

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

private val logger = LoggerFactory.getLogger("booktower.PdfMetadataService")

data class PdfMetadata(
    val title: String?,
    val author: String?,
    val pageCount: Int,
    val coverPath: String?,
)

class PdfMetadataService(
    private val jdbi: Jdbi,
    private val coversPath: String,
) {
    companion object {
        private const val COVER_DPI = 150f
    }

    // Single-threaded so extractions are sequential and don't overwhelm memory
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "pdf-metadata").also { it.isDaemon = true }
        }

    fun submitAsync(
        bookId: String,
        pdfFile: File,
    ) {
        executor.submit {
            extractAndStore(bookId, pdfFile)
        }
    }

    fun shutdown(timeoutSeconds: Long = 30) {
        executor.shutdown()
        if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            logger.warn("PDF metadata executor did not terminate in ${timeoutSeconds}s, forcing shutdown")
            executor.shutdownNow()
        }
    }

    fun extractAndStore(
        bookId: String,
        pdfFile: File,
    ): PdfMetadata {
        var document: PDDocument? = null
        return try {
            document = Loader.loadPDF(pdfFile)
            val info = document.documentInformation

            val title = info?.title?.takeIf { it.isNotBlank() }
            val author = info?.author?.takeIf { it.isNotBlank() }
            val pageCount = document.numberOfPages

            val coverPath = extractCover(bookId, document)

            val metadata = PdfMetadata(title, author, pageCount, coverPath)
            persistMetadata(bookId, metadata)

            logger.info("PDF metadata extracted for book $bookId: pages=$pageCount, title=$title, author=$author")
            metadata
        } catch (e: Exception) {
            logger.warn("Failed to extract PDF metadata for book $bookId: ${e.message}")
            PdfMetadata(null, null, 0, null)
        } finally {
            document?.close()
        }
    }

    private fun extractCover(
        bookId: String,
        document: PDDocument,
    ): String? =
        try {
            val renderer = PDFRenderer(document)
            val image = renderer.renderImageWithDPI(0, COVER_DPI, ImageType.RGB)

            val coversDir = File(coversPath)
            if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create directory: ${coversDir.absolutePath}")

            val coverFile = File(coversDir, "$bookId.jpg")
            ImageIO.write(image, "JPEG", coverFile)

            coverFile.name
        } catch (e: Exception) {
            logger.warn("Failed to extract cover for book $bookId: ${e.message}")
            null
        }

    private fun persistMetadata(
        bookId: String,
        metadata: PdfMetadata,
    ) {
        val now = Instant.now().toString()

        jdbi.useHandle<Exception> { handle ->
            val setClauses =
                buildList {
                    add("page_count = ?")
                    add("updated_at = ?")
                    if (metadata.title != null) add("title = ?")
                    if (metadata.author != null) add("author = ?")
                    if (metadata.coverPath != null) add("cover_path = ?")
                }

            val sql = "UPDATE books SET ${setClauses.joinToString(", ")} WHERE id = ?"
            val update = handle.createUpdate(sql)

            var idx = 0
            update.bind(idx++, metadata.pageCount)
            update.bind(idx++, now)
            if (metadata.title != null) update.bind(idx++, metadata.title)
            if (metadata.author != null) update.bind(idx++, metadata.author)
            if (metadata.coverPath != null) update.bind(idx++, metadata.coverPath)
            update.bind(idx, bookId)

            update.execute()
        }
    }
}
