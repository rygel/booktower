package org.runary.handlers

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.models.CreateBookmarkRequest
import org.runary.models.ErrorResponse
import org.runary.models.SuccessResponse
import org.runary.services.BookmarkService
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.BookmarkHandler")

class BookmarkHandler(
    private val bookmarkService: BookmarkService,
) {
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.query("bookId")
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "bookId query parameter is required")))

        val parsedBookId =
            try {
                UUID.fromString(bookId)
            } catch (e: IllegalArgumentException) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid book ID format")))
            }

        val bookmarks = bookmarkService.getBookmarks(userId, parsedBookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(bookmarks))
    }

    fun create(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val requestBody = req.bodyString()

        if (requestBody.isBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
        }

        val createRequest =
            try {
                Json.mapper.readValue(requestBody, CreateBookmarkRequest::class.java)
            } catch (_: Exception) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid JSON body")))
            }

        val validationError = validate(createRequest)
        if (validationError != null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
        }

        return bookmarkService.createBookmark(userId, createRequest).fold(
            onSuccess = { bookmark ->
                logger.info("Bookmark created at page ${bookmark.page}")
                Response(Status.CREATED)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(bookmark))
            },
            onFailure = { error ->
                logger.warn("Failed to create bookmark: ${error.message}")
                Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", error.message ?: "Failed to create bookmark")))
            },
        )
    }

    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookmarkId =
            req.uri.path.split("/").lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        if (bookmarkId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid bookmark ID")))
        }

        val deleted = bookmarkService.deleteBookmark(userId, bookmarkId)
        return if (deleted) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(SuccessResponse("Bookmark deleted successfully")))
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Bookmark not found")))
        }
    }

    private fun validate(request: CreateBookmarkRequest): String? {
        if (request.bookId.isBlank()) return "bookId is required"
        if (request.page < 0) return "page must be non-negative"
        if ((request.title?.length ?: 0) > 255) return "title must be 255 characters or fewer"
        return null
    }
}
