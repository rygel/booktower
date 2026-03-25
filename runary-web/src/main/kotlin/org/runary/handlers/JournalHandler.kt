package org.runary.handlers

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.models.ErrorResponse
import org.runary.services.CreateJournalEntryRequest
import org.runary.services.JournalService
import org.runary.services.UpdateJournalEntryRequest
import java.util.UUID

class JournalHandler(
    private val journalService: JournalService,
) {
    /** GET /api/books/{id}/journal */
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId = extractBookId(req) ?: return badRequest("Invalid book ID")
        val entries = journalService.list(userId, bookId)
        return ok(entries)
    }

    /** POST /api/books/{id}/journal */
    fun create(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId = extractBookId(req) ?: return badRequest("Invalid book ID")
        val body = req.bodyString()
        if (body.isBlank()) return badRequest("Request body required")
        val createReq =
            runCatching { Json.mapper.readValue(body, CreateJournalEntryRequest::class.java) }
                .getOrElse { return badRequest("Invalid JSON") }
        return try {
            val entry = journalService.create(userId, bookId, createReq)
            Response(Status.CREATED)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(entry))
        } catch (e: IllegalArgumentException) {
            badRequest(e.message ?: "Validation error")
        }
    }

    /** GET /api/journal — all entries for the authenticated user across all books */
    fun listAll(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val entries = journalService.listAll(userId)
        return ok(entries)
    }

    /** PUT /api/books/{bookId}/journal/{entryId} */
    fun update(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val entryId = extractLastUuid(req) ?: return badRequest("Invalid entry ID")
        val body = req.bodyString()
        if (body.isBlank()) return badRequest("Request body required")
        val updateReq =
            runCatching { Json.mapper.readValue(body, UpdateJournalEntryRequest::class.java) }
                .getOrElse { return badRequest("Invalid JSON") }
        return try {
            val entry =
                journalService.update(userId, entryId, updateReq)
                    ?: return notFound("Journal entry not found")
            ok(entry)
        } catch (e: IllegalArgumentException) {
            badRequest(e.message ?: "Validation error")
        }
    }

    /** DELETE /api/books/{bookId}/journal/{entryId} */
    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val entryId = extractLastUuid(req) ?: return badRequest("Invalid entry ID")
        return if (journalService.delete(userId, entryId)) {
            Response(Status.NO_CONTENT)
        } else {
            notFound("Journal entry not found")
        }
    }

    private fun extractBookId(req: Request): UUID? {
        // /api/books/{id}/journal  — third-to-last or second segment after "books"
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val booksIdx = parts.indexOf("books")
        return if (booksIdx >= 0 && booksIdx + 1 < parts.size) {
            runCatching { UUID.fromString(parts[booksIdx + 1]) }.getOrNull()
        } else {
            null
        }
    }

    private fun extractLastUuid(req: Request): UUID? =
        req.uri.path
            .split("/")
            .filter { it.isNotBlank() }
            .lastOrNull()
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun ok(body: Any) =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(body))

    private fun badRequest(msg: String) =
        Response(Status.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", msg)))

    private fun notFound(msg: String) =
        Response(Status.NOT_FOUND)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", msg)))
}
