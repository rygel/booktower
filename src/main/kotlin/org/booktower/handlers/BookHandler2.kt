package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.CreateBookRequest
import org.booktower.models.ErrorResponse
import org.booktower.models.SuccessResponse
import org.booktower.models.UpdateBookRequest
import org.booktower.models.UpdateProgressRequest
import org.booktower.services.BookService
import org.booktower.services.ReadingSessionService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.BookHandler")

class BookHandler2(
    private val bookService: BookService,
    private val readingSessionService: ReadingSessionService? = null,
) {
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId = req.query("libraryId")
        val page = req.query("page")?.toIntOrNull() ?: 1
        val pageSize = req.query("pageSize")?.toIntOrNull() ?: 20
        val statusFilter = req.query("status")?.takeIf { it.isNotBlank() }
        val tagFilter = req.query("tag")?.takeIf { it.isNotBlank() }
        val ratingGte = req.query("ratingGte")?.toIntOrNull()
        val formatFilter = req.query("format")?.takeIf { it.isNotBlank() }

        val bookList =
            bookService.getBooks(
                userId,
                libraryId,
                page,
                pageSize,
                statusFilter = statusFilter,
                tagFilter = tagFilter,
                ratingGte = ratingGte,
                formatFilter = formatFilter,
            )
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(bookList))
    }

    fun create(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val requestBody = req.bodyString()
        if (requestBody.isBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
        }

        val createRequest = Json.mapper.readValue(requestBody, CreateBookRequest::class.java)

        val validationError = validateCreateBookRequest(createRequest)
        if (validationError != null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
        }

        val result = bookService.createBook(userId, createRequest)

        return result.fold(
            onSuccess = { book ->
                logger.info("Book created: ${book.title}")
                Response(Status.CREATED)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(book))
            },
            onFailure = { error ->
                logger.error("Error creating book: ${error.message}")
                Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", error.message ?: "Failed to create book")))
            },
        )
    }

    fun recent(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val limit = req.query("limit")?.toIntOrNull() ?: 10
        val books = bookService.getRecentBooks(userId, limit)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(books))
    }

    fun get(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        val bookId = pathParts.lastOrNull()?.let { UUID.fromString(it) }

        if (bookId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid book ID")))
        }

        val book = bookService.getBook(userId, bookId)
        return if (book != null) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(book))
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }
    }

    fun search(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val query = req.query("q")?.trim()
        if (query.isNullOrBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Query parameter 'q' is required")))
        }
        val page = req.query("page")?.toIntOrNull() ?: 1
        val pageSize = req.query("pageSize")?.toIntOrNull() ?: 20
        val results = bookService.searchBooks(userId, query, page, pageSize)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(results))
    }

    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        if (bookId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid book ID")))
        }

        val deleted = bookService.deleteBook(userId, bookId)
        return if (deleted) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(SuccessResponse("Book deleted successfully")))
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }
    }

    fun update(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path.split("/").lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        if (bookId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid book ID")))
        }

        val requestBody = req.bodyString()
        if (requestBody.isBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
        }

        val updateRequest = Json.mapper.readValue(requestBody, UpdateBookRequest::class.java)
        val validationError = validateUpdateBookRequest(updateRequest)
        if (validationError != null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
        }

        val book = bookService.updateBook(userId, bookId, updateRequest, updateRequest.expectedUpdatedAt)
        if (book == null && updateRequest.expectedUpdatedAt != null) {
            // Distinguish 404 (not found) from 409 (conflict)
            val exists = bookService.getBook(userId, bookId) != null
            if (exists) {
                return Response(Status.CONFLICT)
                    .header("Content-Type", "application/json")
                    .body(
                        Json.mapper.writeValueAsString(
                            ErrorResponse("CONFLICT", "Book was modified by another request. Reload and try again."),
                        ),
                    )
            }
        }
        return if (book != null) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(book))
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }
    }

    fun updateProgress(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val pathParts = req.uri.path.split("/")
        // path: /api/books/{id}/progress  → parts[-1]="progress", parts[-2]=id
        val bookId =
            pathParts.dropLast(1).lastOrNull()?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        if (bookId == null) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid book ID")))
        }

        val requestBody = req.bodyString()
        if (requestBody.isBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
        }

        val progressRequest = Json.mapper.readValue(requestBody, UpdateProgressRequest::class.java)
        if (progressRequest.currentPage < 0) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "currentPage must be non-negative")))
        }

        val progress = bookService.updateProgress(userId, bookId, progressRequest)
        return if (progress != null) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(progress))
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
        }
    }

    fun sessions(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                ?: return Response(Status.BAD_REQUEST)
        val limit = req.query("limit")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val sessions = readingSessionService?.getSessionsForBook(userId, bookId, limit) ?: emptyList()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(sessions))
    }

    private fun validateCreateBookRequest(request: CreateBookRequest): String? {
        if (request.title.isBlank()) {
            return "Book title is required"
        }
        if (request.title.length > 255) {
            return "Book title must be 255 characters or fewer"
        }
        if ((request.author?.length ?: 0) > 255) {
            return "Author must be 255 characters or fewer"
        }
        if (request.libraryId.isBlank()) {
            return "Library ID is required"
        }
        try {
            UUID.fromString(request.libraryId)
        } catch (e: IllegalArgumentException) {
            return "Invalid library ID format"
        }
        return null
    }

    private fun validateUpdateBookRequest(request: UpdateBookRequest): String? {
        if (request.title.isBlank()) return "Book title is required"
        if (request.title.length > 255) return "Book title must be 255 characters or fewer"
        if ((request.author?.length ?: 0) > 255) return "Author must be 255 characters or fewer"
        if ((request.description?.length ?: 0) > 10_000) return "Description must be 10,000 characters or fewer"
        if ((request.series?.length ?: 0) > 255) return "Series name must be 255 characters or fewer"
        if ((request.isbn?.length ?: 0) > 20) return "ISBN must be 20 characters or fewer"
        if ((request.publisher?.length ?: 0) > 255) return "Publisher must be 255 characters or fewer"
        if ((request.subtitle?.length ?: 0) > 500) return "Subtitle must be 500 characters or fewer"
        if ((request.language?.length ?: 0) > 10) return "Language code must be 10 characters or fewer"
        val pc = request.pageCount
        if (pc != null && pc < 0) return "Page count cannot be negative"
        if (pc != null && pc > 100_000) return "Page count exceeds maximum (100,000)"
        return null
    }
}
