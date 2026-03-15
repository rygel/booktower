package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.config.StorageConfig
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.ErrorResponse
import org.booktower.services.BookService
import org.booktower.services.PdfMetadataService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.FileHandler")

private val ALLOWED_EXTENSIONS = setOf("pdf", "epub", "mobi", "cbz", "cbr", "fb2")
private val ALLOWED_COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val MAX_COVER_SIZE = 10L * 1024 * 1024 // 10 MB
private val CONTENT_TYPES = mapOf(
    "pdf" to "application/pdf",
    "epub" to "application/epub+zip",
    "mobi" to "application/x-mobipocket-ebook",
    "cbz" to "application/zip",
    "cbr" to "application/x-rar-compressed",
    "fb2" to "application/xml",
)
private const val MAX_FILE_SIZE = 500L * 1024 * 1024 // 500 MB

class FileHandler(
    private val bookService: BookService,
    private val pdfMetadataService: PdfMetadataService,
    private val storageConfig: StorageConfig,
) {

    fun upload(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId = req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
            try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
        }

        if (bookId == null) {
            return badRequest("Invalid book ID")
        }

        val filename = req.header("X-Filename")?.trim()
        if (filename.isNullOrBlank()) {
            return badRequest("X-Filename header is required")
        }

        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_EXTENSIONS) {
            return badRequest("Unsupported file type '$ext'. Allowed: ${ALLOWED_EXTENSIONS.joinToString()}")
        }

        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) {
            return badRequest("File body is empty")
        }
        if (bytes.size > MAX_FILE_SIZE) {
            return badRequest("File exceeds maximum size of 500 MB")
        }

        val destDir = File(storageConfig.booksPath)
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, "$bookId.$ext")
        destFile.writeBytes(bytes)

        val updated = bookService.updateFileInfo(userId, bookId, destFile.path, bytes.size.toLong())
        if (!updated) {
            destFile.delete()
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }

        logger.info("File uploaded for book $bookId: ${bytes.size} bytes, ext=$ext")

        // Extract PDF metadata and cover asynchronously via managed executor
        if (ext == "pdf") {
            pdfMetadataService.submitAsync(bookId.toString(), destFile)
        }

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("filename" to destFile.name, "size" to bytes.size)))
    }

    fun download(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId = req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
            try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
        }

        if (bookId == null) {
            return badRequest("Invalid book ID")
        }

        val filePath = bookService.getBookFilePath(userId, bookId)
            ?: return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))

        if (filePath.isBlank()) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "No file uploaded for this book")))
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "File not found on disk")))
        }

        val ext = file.extension.lowercase()
        val contentType = CONTENT_TYPES[ext] ?: "application/octet-stream"

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
    }

    fun uploadCover(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId = req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
            try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
        } ?: return badRequest("Invalid book ID")

        val filename = req.header("X-Filename")?.trim()
        if (filename.isNullOrBlank()) return badRequest("X-Filename header is required")

        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_COVER_EXTENSIONS) {
            return badRequest("Unsupported image type '$ext'. Allowed: ${ALLOWED_COVER_EXTENSIONS.joinToString()}")
        }

        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) return badRequest("File body is empty")
        if (bytes.size > MAX_COVER_SIZE) return badRequest("Cover image exceeds maximum size of 10 MB")

        val coversDir = File(storageConfig.coversPath)
        if (!coversDir.exists()) coversDir.mkdirs()

        // Normalise to a canonical filename; keep ext for correct Content-Type serving
        val coverFilename = "$bookId.$ext"
        val destFile = File(coversDir, coverFilename)
        destFile.writeBytes(bytes)

        val updated = bookService.updateCoverPath(userId, bookId, coverFilename)
        if (!updated) {
            destFile.delete()
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }

        logger.info("Cover uploaded for book $bookId: ${bytes.size} bytes")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("coverUrl" to "/covers/$coverFilename")))
    }

    fun cover(req: Request): Response {
        val filename = req.uri.path.split("/").lastOrNull()
            ?: return Response(Status.NOT_FOUND)

        // Reject path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid filename")))
        }

        val file = File(storageConfig.coversPath, filename)
        if (!file.exists() || !file.isFile) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Cover not found")))
        }

        val contentType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Cache-Control", "public, max-age=86400")
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
    }

    private fun badRequest(message: String) = Response(Status.BAD_REQUEST)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", message)))
}
