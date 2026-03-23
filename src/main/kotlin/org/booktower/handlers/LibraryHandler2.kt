package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.config.StorageConfig
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.ErrorResponse
import org.booktower.models.ScanJobState
import org.booktower.models.ScanJobStatus
import org.booktower.models.UpdateLibrarySettingsRequest
import org.booktower.services.BackgroundTaskService
import org.booktower.services.LibraryService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val logger = LoggerFactory.getLogger("booktower.LibraryHandler")

// In-memory job registry — keyed by jobId. Survives restarts only within the JVM lifetime.
private val scanJobs = ConcurrentHashMap<String, ScanJobStatus>()
private val scanExecutor =
    Executors.newCachedThreadPool { r ->
        Thread(r, "scan-worker").also { it.isDaemon = true }
    }

private val ALLOWED_ICON_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "svg")
private const val MAX_ICON_SIZE = 2L * 1024 * 1024 // 2 MB

class LibraryHandler2(
    private val libraryService: LibraryService,
    private val taskService: BackgroundTaskService? = null,
    private val storageConfig: StorageConfig? = null,
) {
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraries = libraryService.getLibraries(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(libraries))
    }

    fun create(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val requestBody = req.bodyString()
        if (requestBody.isBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
        }

        val createRequest = Json.mapper.readValue(requestBody, CreateLibraryRequest::class.java)

        val validationError = validateCreateLibraryRequest(createRequest)
        if (validationError != null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
        }

        val library = libraryService.createLibrary(userId, createRequest)
        logger.info("Library created: ${library.name}")

        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(library))
    }

    fun scan(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        // path: /api/libraries/{id}/scan  → second to last segment is the id
        val libraryId =
            pathParts.getOrNull(pathParts.size - 2)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }

        if (libraryId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))
        }

        val result = libraryService.scanLibrary(userId, libraryId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(result))
    }

    /**
     * POST /api/libraries/{id}/scan/async
     * Starts a background scan and returns a jobId immediately.
     * Poll GET /api/libraries/{id}/scan/{jobId} for status.
     */
    fun scanAsync(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        // path: /api/libraries/{id}/scan/async  → index [size-3] is the id
        val libraryId =
            pathParts.getOrNull(pathParts.size - 3)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }

        if (libraryId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))
        }

        val jobId = UUID.randomUUID().toString()
        scanJobs[jobId] = ScanJobStatus(jobId = jobId, libraryId = libraryId.toString(), state = ScanJobState.RUNNING)
        val bgTaskId = taskService?.start(userId, "library.scan", "Scanning library $libraryId")

        scanExecutor.submit {
            try {
                val result = libraryService.scanLibrary(userId, libraryId)
                scanJobs[jobId] =
                    ScanJobStatus(
                        jobId = jobId,
                        libraryId = libraryId.toString(),
                        state = ScanJobState.DONE,
                        added = result.added,
                        skipped = result.skipped,
                        errors = result.errors,
                    )
                bgTaskId?.let { taskService?.complete(it, "+${result.added} added, ${result.skipped} skipped, ${result.errors} errors") }
                logger.info("Async scan job $jobId done: +${result.added} added")
            } catch (e: Exception) {
                logger.error("Async scan job $jobId failed", e)
                scanJobs[jobId] =
                    ScanJobStatus(
                        jobId = jobId,
                        libraryId = libraryId.toString(),
                        state = ScanJobState.FAILED,
                        message = e.message,
                    )
                bgTaskId?.let { taskService?.fail(it, e.message) }
            }
        }

        return Response(Status.ACCEPTED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("jobId" to jobId)))
    }

    /** GET /api/libraries/{id}/scan/{jobId} */
    fun scanStatus(req: Request): Response {
        val pathParts = req.uri.path.split("/")
        val jobId = pathParts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val job =
            scanJobs[jobId]
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Job not found")))
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(job))
    }

    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        val libraryId = pathParts.lastOrNull()?.let { UUID.fromString(it) }

        if (libraryId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))
        }

        val deleted = libraryService.deleteLibrary(userId, libraryId)
        return if (deleted) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(mapOf("message" to "Library deleted successfully")))
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Library not found")))
        }
    }

    /** GET /api/libraries/{id}/settings */
    fun getSettings(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId =
            req.uri.path.split("/").let { parts ->
                parts.getOrNull(parts.size - 2)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            } ?: return Response(Status.BAD_REQUEST)
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))

        val settings =
            libraryService.getSettings(userId, libraryId)
                ?: return Response(Status.NOT_FOUND)
                    .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Library not found")))

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(settings))
    }

    /** PUT /api/libraries/{id}/settings */
    fun updateSettings(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId =
            req.uri.path.split("/").let { parts ->
                parts.getOrNull(parts.size - 2)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            } ?: return Response(Status.BAD_REQUEST)
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))

        val request =
            runCatching {
                Json.mapper.readValue(req.bodyString(), UpdateLibrarySettingsRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid request body")))

        val updated =
            runCatching { libraryService.updateSettings(userId, libraryId, request) }
                .getOrElse { e ->
                    return Response(Status.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", e.message ?: "Invalid settings")))
                }
                ?: return Response(Status.NOT_FOUND)
                    .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Library not found")))

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(updated))
    }

    /** POST /api/libraries/{id}/organize */
    fun organize(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId =
            req.uri.path.split("/").let { parts ->
                parts.getOrNull(parts.size - 2)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            } ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))

        val result = libraryService.organizeFiles(userId, libraryId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(result))
    }

    /** POST /api/libraries/{id}/icon — upload an icon image */
    fun uploadIcon(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId =
            req.uri.path.split("/").let { parts ->
                parts.getOrNull(parts.size - 2)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            } ?: return badRequest("Invalid library ID")

        val storage =
            storageConfig ?: return Response(Status.SERVICE_UNAVAILABLE)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("SERVICE_UNAVAILABLE", "Storage not configured")))

        val filename = req.header("X-Filename")?.trim()
        if (filename.isNullOrBlank()) return badRequest("X-Filename header is required")

        val ext = filename.substringAfterLast('.', "").lowercase().replace(Regex("[^a-z0-9]"), "")
        if (ext !in ALLOWED_ICON_EXTENSIONS) {
            return badRequest(
                "Unsupported image type '$ext'. Allowed: ${ALLOWED_ICON_EXTENSIONS.joinToString()}",
            )
        }

        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) return badRequest("File body is empty")
        if (bytes.size > MAX_ICON_SIZE) return badRequest("Icon exceeds maximum size of 2 MB")

        val iconsDir = File(storage.coversPath, "library-icons")
        if (!iconsDir.exists() && !iconsDir.mkdirs()) logger.warn("Could not create directory: ${iconsDir.absolutePath}")

        val iconFilename = "$libraryId.$ext"
        val destFile = File(iconsDir, iconFilename)
        destFile.writeBytes(bytes)

        val updated = libraryService.updateIconPath(userId, libraryId, "library-icons/$iconFilename")
        if (!updated) {
            if (!destFile.delete()) logger.warn("Could not delete file: ${destFile.absolutePath}")
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Library not found")))
        }

        logger.info("Icon uploaded for library $libraryId: ${bytes.size} bytes")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("iconUrl" to "/api/libraries/$libraryId/icon")))
    }

    /** GET /api/libraries/{id}/icon — serve the icon image */
    fun getIcon(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId =
            req.uri.path.split("/").let { parts ->
                parts.getOrNull(parts.size - 2)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            } ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))

        val storage =
            storageConfig ?: return Response(Status.SERVICE_UNAVAILABLE)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("SERVICE_UNAVAILABLE", "Storage not configured")))

        val iconPath =
            libraryService.getIconPath(userId, libraryId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "No icon set for this library")))

        // Reject path traversal in stored icon path
        if (iconPath.contains("..")) {
            logger.warn("Path traversal attempt in icon path: $iconPath")
            return Response(Status.FORBIDDEN)
        }
        val file = File(storage.coversPath, iconPath)
        val baseDir = File(storage.coversPath)
        if (!file.canonicalPath.startsWith(baseDir.canonicalPath)) {
            logger.warn("Path traversal attempt blocked: ${file.absolutePath}")
            return Response(Status.FORBIDDEN)
        }
        if (!file.exists() || !file.isFile) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Icon file not found")))
        }

        val contentType =
            when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                else -> "application/octet-stream"
            }

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Cache-Control", "public, max-age=86400")
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
    }

    /** DELETE /api/libraries/{id}/icon — remove the icon */
    fun deleteIcon(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId =
            req.uri.path.split("/").let { parts ->
                parts.getOrNull(parts.size - 2)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            } ?: return badRequest("Invalid library ID")

        val storage = storageConfig
        val existingPath = libraryService.getIconPath(userId, libraryId)
        if (existingPath != null && storage != null && !existingPath.contains("..")) {
            val iconFile = File(storage.coversPath, existingPath)
            val baseDir = File(storage.coversPath)
            if (iconFile.canonicalPath.startsWith(baseDir.canonicalPath)) {
                if (!iconFile.delete()) logger.warn("Could not delete file: ${iconFile.absolutePath}")
            } else {
                logger.warn("Path traversal attempt blocked on icon delete: ${iconFile.absolutePath}")
            }
        }

        val updated = libraryService.updateIconPath(userId, libraryId, null)
        return if (updated) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Library not found")))
        }
    }

    private fun badRequest(message: String) =
        Response(Status.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", message)))

    private fun validateCreateLibraryRequest(request: CreateLibraryRequest): String? {
        if (request.name.isBlank()) {
            return "Library name is required"
        }
        if (request.name.length > 100) {
            return "Library name must be less than 100 characters"
        }
        if (request.path.isBlank()) {
            return "Library path is required"
        }
        return null
    }
}
