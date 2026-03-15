package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.ErrorResponse
import org.booktower.services.LibraryService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.LibraryHandler")

class LibraryHandler2(private val libraryService: LibraryService) {
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
        val libraryId = pathParts.getOrNull(pathParts.size - 2)?.let {
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
