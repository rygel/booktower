package org.booktower.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.models.CreateBookRequest
import org.booktower.models.ErrorResponse
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.BookHandler")
private val objectMapper = ObjectMapper()

class BookHandler2(private val bookService: BookService, private val jwtService: JwtService) {
    fun list(req: Request): Response {
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId == null) {
            return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        }

        return try {
            val libraryId = req.query("libraryId")
            val page = req.query("page")?.toIntOrNull() ?: 1
            val pageSize = req.query("pageSize")?.toIntOrNull() ?: 20

            val bookList = bookService.getBooks(userId, libraryId, page, pageSize)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(bookList))
        } catch (e: Exception) {
            logger.error("Error listing books", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to list books")))
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

            val createRequest = objectMapper.readValue(requestBody, CreateBookRequest::class.java)

            val validationError = validateCreateBookRequest(createRequest)
            if (validationError != null) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
            }

            val result = bookService.createBook(userId, createRequest)

            result.fold(
                onSuccess = { book ->
                    logger.info("Book created: ${book.title}")
                    Response(Status.CREATED)
                        .header("Content-Type", "application/json")
                        .body(objectMapper.writeValueAsString(book))
                },
                onFailure = { error ->
                    logger.error("Error creating book: ${error.message}")
                    Response(Status.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to create book")))
                },
            )
        } catch (e: Exception) {
            logger.error("Error creating book", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to create book")))
        }
    }

    fun recent(req: Request): Response {
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId == null) {
            return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        }

        return try {
            val limit = req.query("limit")?.toIntOrNull() ?: 10
            val books = bookService.getRecentBooks(userId, limit)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(books))
        } catch (e: Exception) {
            logger.error("Error fetching recent books", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to fetch recent books")))
        }
    }

    fun get(req: Request): Response {
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId == null) {
            return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        }

        return try {
            val pathParts = req.uri.path.split("/")
            val bookId = pathParts.lastOrNull()?.let { UUID.fromString(it) }

            if (bookId == null) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Invalid book ID")))
            }

            val book = bookService.getBook(userId, bookId)
            if (book != null) {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(book))
            } else {
                Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Book not found")))
            }
        } catch (e: Exception) {
            logger.error("Error fetching book", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Failed to fetch book")))
        }
    }

    private fun validateCreateBookRequest(request: CreateBookRequest): String? {
        if (request.title.isBlank()) {
            return "Book title is required"
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
}
