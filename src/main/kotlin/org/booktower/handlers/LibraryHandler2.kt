package org.booktower.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.ErrorResponse
import org.booktower.models.LibraryDto
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.LibraryHandler")
private val objectMapper = ObjectMapper()

class LibraryHandler2(private val libraryService: LibraryService, private val jwtService: JwtService) {
    fun list(req: Request): Response {
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId == null) {
            return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        }

        return try {
            val libraries = libraryService.getLibraries(userId)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(libraries))
        } catch (e: Exception) {
            logger.error("Error listing libraries", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to list libraries")))
        }
    }

    fun create(req: Request): Response {
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId == null) {
            return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        }

        return try {
            val requestBody = req.bodyString()
            if (requestBody.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
            }

            val createRequest = objectMapper.readValue(requestBody, CreateLibraryRequest::class.java)

            val validationError = validateCreateLibraryRequest(createRequest)
            if (validationError != null) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
            }

            val library = libraryService.createLibrary(userId, createRequest)
            logger.info("Library created: ${library.name}")

            Response(Status.CREATED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(library))
        } catch (e: Exception) {
            logger.error("Error creating library", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to create library")))
        }
    }

    fun delete(req: Request): Response {
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId == null) {
            return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        }

        return try {
            val pathParts = req.uri.path.split("/")
            val libraryId = pathParts.lastOrNull()?.let { UUID.fromString(it) }

            if (libraryId == null) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid library ID")))
            }

            val deleted = libraryService.deleteLibrary(userId, libraryId)
            if (deleted) {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(mapOf("message" to "Library deleted successfully")))
            } else {
                Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Library not found")))
            }
        } catch (e: Exception) {
            logger.error("Error deleting library", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to delete library")))
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
