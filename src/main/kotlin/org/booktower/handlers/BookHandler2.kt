package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.CreateBookRequest
import org.booktower.models.ErrorResponse
import org.booktower.models.SuccessResponse
import org.booktower.models.UpdateBookRequest
import org.booktower.models.UpdateProgressRequest
import org.booktower.services.BookService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.BookHandler")

class BookHandler2(private val bookService: BookService) {
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId = req.query("libraryId")
        val page = req.query("page")?.toIntOrNull() ?: 1
        val pageSize = req.query("pageSize")?.toIntOrNull() ?: 20

        val bookList = bookService.getBooks(userId, libraryId, page, pageSize)
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
        val bookId = req.uri.path.split("/").lastOrNull()?.let { id ->
            try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
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
        val bookId = req.uri.path.split("/").lastOrNull()?.let { id ->
            try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
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

        val book = bookService.updateBook(userId, bookId, updateRequest)
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
        val bookId = pathParts.dropLast(1).lastOrNull()?.let { id ->
            try { UUID.fromString(id) } catch (e: IllegalArgumentException) { null }
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
        return null
    }
}
