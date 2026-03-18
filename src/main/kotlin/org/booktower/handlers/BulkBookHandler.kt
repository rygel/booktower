package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.ErrorResponse
import org.booktower.services.BookService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.UUID

internal data class BulkIdsRequest(
    val bookIds: List<String> = emptyList(),
)

internal data class BulkMoveRequest(
    val bookIds: List<String> = emptyList(),
    val targetLibraryId: String = "",
)

internal data class BulkTagRequest(
    val bookIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

internal data class BulkStatusRequest(
    val bookIds: List<String> = emptyList(),
    val status: String = "",
)

class BulkBookHandler(
    private val bookService: BookService,
) {
    fun move(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching { Json.mapper.readValue(req.bodyString(), BulkMoveRequest::class.java) }
                .getOrElse { return badRequest("Invalid request body") }
        if (body.bookIds.isEmpty()) return badRequest("bookIds is required")
        val targetId =
            runCatching { UUID.fromString(body.targetLibraryId) }
                .getOrElse { return badRequest("Invalid targetLibraryId") }
        val bookUuids = body.bookIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (bookUuids.isEmpty()) return badRequest("No valid book IDs")
        val count = bookService.bulkMove(userId, bookUuids, targetId)
        return ok(mapOf("moved" to count))
    }

    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching { Json.mapper.readValue(req.bodyString(), BulkIdsRequest::class.java) }
                .getOrElse { return badRequest("Invalid request body") }
        if (body.bookIds.isEmpty()) return badRequest("bookIds is required")
        val bookUuids = body.bookIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (bookUuids.isEmpty()) return badRequest("No valid book IDs")
        val count = bookService.bulkDelete(userId, bookUuids)
        return ok(mapOf("deleted" to count))
    }

    fun tag(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching { Json.mapper.readValue(req.bodyString(), BulkTagRequest::class.java) }
                .getOrElse { return badRequest("Invalid request body") }
        if (body.bookIds.isEmpty()) return badRequest("bookIds is required")
        val bookUuids = body.bookIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (bookUuids.isEmpty()) return badRequest("No valid book IDs")
        val tags = body.tags.map { it.trim() }.filter { it.isNotBlank() }
        val count = bookService.bulkTag(userId, bookUuids, tags)
        return ok(mapOf("updated" to count))
    }

    fun status(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching { Json.mapper.readValue(req.bodyString(), BulkStatusRequest::class.java) }
                .getOrElse { return badRequest("Invalid request body") }
        if (body.bookIds.isEmpty()) return badRequest("bookIds is required")
        val bookUuids = body.bookIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (bookUuids.isEmpty()) return badRequest("No valid book IDs")
        val count = bookService.bulkStatus(userId, bookUuids, body.status)
        return ok(mapOf("updated" to count))
    }

    private fun ok(body: Any) =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(body))

    private fun badRequest(msg: String) =
        Response(Status.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", msg)))
}
