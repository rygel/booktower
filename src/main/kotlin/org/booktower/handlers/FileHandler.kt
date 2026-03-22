package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.config.StorageConfig
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.ErrorResponse
import org.booktower.services.BookService
import org.booktower.services.CalibreConversionService
import org.booktower.services.EpubMetadataService
import org.booktower.services.Fb2ReaderService
import org.booktower.services.PdfMetadataService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("booktower.FileHandler")

private val ALLOWED_EXTENSIONS =
    setOf("pdf", "epub", "mobi", "azw3", "cbz", "cbr", "fb2", "djvu", "mp3", "m4b", "m4a", "ogg", "flac", "aac")
private val ALLOWED_COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val MAX_COVER_SIZE = 10L * 1024 * 1024 // 10 MB
private val CONTENT_TYPES =
    mapOf(
        "pdf" to "application/pdf",
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "azw3" to "application/x-mobi8-ebook",
        "cbz" to "application/zip",
        "cbr" to "application/x-rar-compressed",
        "fb2" to "application/xml",
        "djvu" to "image/vnd.djvu",
        "mp3" to "audio/mpeg",
        "m4b" to "audio/mp4",
        "m4a" to "audio/mp4",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
    )
private const val MAX_FILE_SIZE = 500L * 1024 * 1024 // 500 MB

class FileHandler(
    private val bookService: BookService,
    private val pdfMetadataService: PdfMetadataService,
    private val epubMetadataService: EpubMetadataService,
    private val storageConfig: StorageConfig,
    private val fb2ReaderService: Fb2ReaderService = Fb2ReaderService(),
    private val calibreService: CalibreConversionService? = null,
    private val ftsService: org.booktower.services.FtsService? = null,
) {
    private val coverCache =
        com.github.benmanes.caffeine.cache.Caffeine
            .newBuilder()
            .maximumSize(200)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build<String, ByteArray>()

    /** Strip path separators and traversal sequences from a filename extension. */
    private fun sanitizeExt(filename: String): String =
        filename
            .substringAfterLast('.', "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")

    fun upload(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        if (bookId == null) {
            return badRequest("Invalid book ID")
        }

        val filename = req.header("X-Filename")?.trim()
        if (filename.isNullOrBlank()) {
            return badRequest("X-Filename header is required")
        }

        val ext = sanitizeExt(filename)
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
        if (!destDir.exists() && !destDir.mkdirs()) logger.warn("Could not create directory: ${destDir.absolutePath}")

        val freeSpace = destDir.usableSpace
        if (freeSpace > 0 && freeSpace < bytes.size * 2L) {
            return Response(Status(507, "Insufficient Storage"))
                .header("Content-Type", "application/json")
                .body(
                    Json.mapper.writeValueAsString(
                        ErrorResponse("INSUFFICIENT_STORAGE", "Not enough disk space (${freeSpace / 1024 / 1024}MB free, need ${bytes.size / 1024 / 1024}MB)"),
                    ),
                )
        }

        val destFile = File(destDir, "$bookId.$ext")
        destFile.writeBytes(bytes)

        val updated = bookService.updateFileInfo(userId, bookId, destFile.path, bytes.size.toLong())
        if (!updated) {
            if (!destFile.delete()) logger.warn("Could not delete file: ${destFile.absolutePath}")
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }

        logger.info("File uploaded for book $bookId: ${bytes.size} bytes, ext=$ext")

        // Extract metadata and cover asynchronously via managed executors
        when (ext) {
            "pdf" -> pdfMetadataService.submitAsync(bookId.toString(), destFile)
            "epub" -> epubMetadataService.submitAsync(bookId.toString(), destFile)
        }

        // Enqueue for full-text search indexing (EPUB and PDF only)
        if (ext in setOf("epub", "pdf")) {
            ftsService?.enqueue(bookId.toString())
        }

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("filename" to destFile.name, "size" to bytes.size)))
    }

    fun download(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        if (bookId == null) {
            return badRequest("Invalid book ID")
        }

        val filePath =
            bookService.getBookFilePath(userId, bookId)
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

    /**
     * GET /api/books/{id}/kepub — serves the EPUB as a KEPUB by returning it with the
     * `.kepub.epub` extension and `application/x-kobo-epub+zip` content-type.
     * Kobo firmware recognizes this and enables its enhanced reading UI.
     */
    fun downloadKepub(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")

        val filePath =
            bookService.getBookFilePath(userId, bookId)
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

        if (file.extension.lowercase() != "epub") {
            return Response(Status.UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("UNSUPPORTED", "KEPUB conversion is only available for EPUB files")))
        }

        val kepubFilename = file.nameWithoutExtension + ".kepub.epub"
        return Response(Status.OK)
            .header("Content-Type", "application/x-kobo-epub+zip")
            .header("Content-Disposition", "attachment; filename=\"$kepubFilename\"")
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
    }

    /**
     * GET /api/books/{id}/read-content — converts FB2 to HTML for in-browser reading.
     * Returns 422 for non-FB2 files.
     */
    fun readContent(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")

        val filePath =
            bookService.getBookFilePath(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
        val file = File(filePath)
        if (!file.exists()) return Response(Status.NOT_FOUND)

        val ext = file.extension.lowercase()
        return when (ext) {
            "fb2" -> {
                try {
                    val html = fb2ReaderService.toHtml(file)
                    Response(Status.OK)
                        .header("Content-Type", "text/html; charset=utf-8")
                        .body(html)
                } catch (e: Exception) {
                    logger.error("FB2 conversion failed for book $bookId", e)
                    Response(Status.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"FB2 conversion failed: ${e.message}"}""")
                }
            }

            "mobi", "azw3" -> {
                val svc = calibreService
                if (svc == null || !svc.isAvailable) {
                    return Response(Status.UNPROCESSABLE_ENTITY)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"Calibre is not installed — install it to enable in-browser Kindle reading"}""")
                }
                try {
                    val html = svc.toHtml(file)
                    Response(Status.OK)
                        .header("Content-Type", "text/html; charset=utf-8")
                        .body(html)
                } catch (e: Exception) {
                    logger.error("Calibre conversion failed for book $bookId", e)
                    Response(Status.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"Calibre conversion failed: ${e.message}"}""")
                }
            }

            else -> {
                Response(Status.UNPROCESSABLE_ENTITY)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"read-content is only supported for FB2, MOBI, and AZW3 files"}""")
            }
        }
    }

    /** GET /api/books/{id}/audio — streams audio with HTTP Range support for seeking */
    fun audioStream(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")

        val filePath =
            bookService.getBookFilePath(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))

        if (filePath.isBlank()) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "No file uploaded for this book")))
        }

        val file = java.io.File(filePath)
        if (!file.exists() || !file.isFile) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "File not found on disk")))
        }

        val contentType = CONTENT_TYPES[file.extension.lowercase()] ?: "audio/mpeg"
        val totalSize = file.length()
        val rangeHeader = req.header("Range")

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=")
            val parts = range.split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.toLongOrNull() ?: (totalSize - 1)
            val safeEnd = minOf(end, totalSize - 1)
            val length = safeEnd - start + 1
            val inputStream = file.inputStream().apply { skip(start) }
            return Response(Status.PARTIAL_CONTENT)
                .header("Content-Type", contentType)
                .header("Content-Range", "bytes $start-$safeEnd/$totalSize")
                .header("Content-Length", length.toString())
                .header("Accept-Ranges", "bytes")
                .body(
                    inputStream.buffered().let { buf ->
                        object : java.io.InputStream() {
                            private var remaining = length

                            override fun read(): Int {
                                if (remaining <= 0) return -1
                                remaining--
                                return buf.read()
                            }

                            override fun read(
                                b: ByteArray,
                                off: Int,
                                len: Int,
                            ): Int {
                                if (remaining <= 0) return -1
                                val toRead = minOf(len.toLong(), remaining).toInt()
                                val n = buf.read(b, off, toRead)
                                if (n > 0) remaining -= n
                                return n
                            }

                            override fun close() {
                                buf.close()
                                inputStream.close()
                            }
                        }
                    },
                )
        }

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Length", totalSize.toString())
            .header("Accept-Ranges", "bytes")
            .body(file.inputStream())
    }

    fun uploadCover(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")

        val filename = req.header("X-Filename")?.trim()
        if (filename.isNullOrBlank()) return badRequest("X-Filename header is required")

        val ext = sanitizeExt(filename)
        if (ext !in ALLOWED_COVER_EXTENSIONS) {
            return badRequest("Unsupported image type '$ext'. Allowed: ${ALLOWED_COVER_EXTENSIONS.joinToString()}")
        }

        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) return badRequest("File body is empty")
        if (bytes.size > MAX_COVER_SIZE) return badRequest("Cover image exceeds maximum size of 10 MB")

        val coversDir = File(storageConfig.coversPath)
        if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create directory: ${coversDir.absolutePath}")

        // Normalise to a canonical filename; keep ext for correct Content-Type serving
        val coverFilename = "$bookId.$ext"
        val destFile = File(coversDir, coverFilename)
        destFile.writeBytes(bytes)
        coverCache.invalidate(coverFilename)

        val updated = bookService.updateCoverPath(userId, bookId, coverFilename)
        if (!updated) {
            if (!destFile.delete()) logger.warn("Could not delete file: ${destFile.absolutePath}")
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
        val filename =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.NOT_FOUND)

        // Reject path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid filename")))
        }

        val bytes =
            coverCache.getIfPresent(filename) ?: run {
                val f = File(storageConfig.coversPath, filename)
                if (f.exists() && f.isFile) {
                    val data = f.readBytes()
                    coverCache.put(filename, data)
                    data
                } else {
                    null
                }
            } ?: return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Cover not found")))

        val contentType =
            when (filename.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Cache-Control", "public, max-age=86400")
            .header("Content-Length", bytes.size.toString())
            .body(bytes.inputStream())
    }

    /** GET /api/books/{id}/chapters — list all chapter files for a book */
    fun listChapters(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")
        val chapters = bookService.getBookFiles(userId, bookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(chapters))
    }

    /** POST /api/books/{id}/chapters — upload a single audio chapter */
    fun uploadChapter(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")

        bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)

        val filename = req.header("X-Filename")?.trim()
        if (filename.isNullOrBlank()) return badRequest("X-Filename header is required")

        val ext = sanitizeExt(filename)
        if (ext !in setOf("mp3", "m4b", "m4a", "ogg", "flac", "aac")) {
            return badRequest("Unsupported audio format: $ext")
        }

        val trackIndex =
            req.header("X-Track-Index")?.toIntOrNull()
                ?: return badRequest("X-Track-Index header is required")
        val chapterTitle = req.header("X-Chapter-Title")?.trim()

        val existing = bookService.getBookFilePath(userId, bookId, trackIndex)
        if (existing != null) {
            return Response(Status.CONFLICT)
                .header("Content-Type", "application/json")
                .body(
                    Json.mapper.writeValueAsString(
                        ErrorResponse("CONFLICT", "Chapter $trackIndex already exists — delete it before re-uploading"),
                    ),
                )
        }

        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) return badRequest("File body is empty")
        if (bytes.size > MAX_FILE_SIZE) return badRequest("File exceeds maximum size of 500 MB")

        val destDir = File(storageConfig.booksPath)
        if (!destDir.exists() && !destDir.mkdirs()) logger.warn("Could not create directory: ${destDir.absolutePath}")
        val destFile = File(destDir, "$bookId-${trackIndex.toString().padStart(4, '0')}.$ext")
        destFile.writeBytes(bytes)

        bookService.addBookFile(userId, bookId, trackIndex, chapterTitle, destFile.path, bytes.size.toLong())
        logger.info("Chapter $trackIndex uploaded for book $bookId: ${bytes.size} bytes")

        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body("""{"trackIndex":$trackIndex,"fileSize":${bytes.size}}""")
    }

    /** GET /api/books/{id}/chapters/{trackIndex} — stream one chapter with Range support */
    fun audioStreamChapter(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        val trackIndex =
            pathParts.lastOrNull()?.toIntOrNull()
                ?: return badRequest("Invalid track index")
        val bookId =
            pathParts.dropLast(2).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")

        val filePath =
            bookService.getBookFilePath(userId, bookId, trackIndex)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Chapter not found")))

        val file = java.io.File(filePath)
        if (!file.exists() || !file.isFile) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "File not found on disk")))
        }

        val contentType = CONTENT_TYPES[file.extension.lowercase()] ?: "audio/mpeg"
        val totalSize = file.length()
        val rangeHeader = req.header("Range")

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=")
            val parts = range.split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.toLongOrNull() ?: (totalSize - 1)
            val safeEnd = minOf(end, totalSize - 1)
            val length = safeEnd - start + 1
            val inputStream = file.inputStream().apply { skip(start) }
            return Response(Status.PARTIAL_CONTENT)
                .header("Content-Type", contentType)
                .header("Content-Range", "bytes $start-$safeEnd/$totalSize")
                .header("Content-Length", length.toString())
                .header("Accept-Ranges", "bytes")
                .body(
                    inputStream.buffered().let { buf ->
                        object : java.io.InputStream() {
                            private var remaining = length

                            override fun read(): Int {
                                if (remaining <= 0) return -1
                                remaining--
                                return buf.read()
                            }

                            override fun read(
                                b: ByteArray,
                                off: Int,
                                len: Int,
                            ): Int {
                                if (remaining <= 0) return -1
                                val toRead = minOf(len.toLong(), remaining).toInt()
                                val n = buf.read(b, off, toRead)
                                if (n > 0) remaining -= n
                                return n
                            }

                            override fun close() {
                                buf.close()
                                inputStream.close()
                            }
                        }
                    },
                )
        }

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Length", totalSize.toString())
            .header("Accept-Ranges", "bytes")
            .body(file.inputStream())
    }

    /** DELETE /api/books/{id}/chapters/{trackIndex} — remove one chapter */
    fun deleteChapter(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        val trackIndex =
            pathParts.lastOrNull()?.toIntOrNull()
                ?: return badRequest("Invalid track index")
        val bookId =
            pathParts.dropLast(2).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")
        val deleted = bookService.deleteBookFile(userId, bookId, trackIndex)
        return if (deleted) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    /** PUT /api/books/{id}/chapters/{trackIndex} — update chapter title */
    fun updateChapterMeta(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        val trackIndex =
            pathParts.lastOrNull()?.toIntOrNull()
                ?: return badRequest("Invalid track index")
        val bookId =
            pathParts.dropLast(2).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return badRequest("Invalid book ID")
        val body =
            runCatching { Json.mapper.readTree(req.bodyString()) }.getOrNull()
                ?: return badRequest("Invalid JSON")
        val newTitle = body.get("title")?.asText()
        bookService.updateBookFileTitle(userId, bookId, trackIndex, newTitle)
        return Response(Status.OK).header("Content-Type", "application/json").body("{}")
    }

    private fun badRequest(message: String) =
        Response(Status.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", message)))
}
