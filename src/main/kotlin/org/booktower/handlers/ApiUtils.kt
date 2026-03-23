package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.models.ErrorResponse
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.UUID

/** Build a JSON response with the given status and body. */
fun jsonResponse(
    status: Status,
    data: Any,
): Response =
    Response(status)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(data))

/** Build a JSON error response. */
fun jsonError(
    status: Status,
    code: String,
    message: String,
): Response =
    Response(status)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(ErrorResponse(code, message)))

/** Extract a UUID from the last path segment (e.g., /api/books/{id}). */
fun Request.lastPathUuid(): UUID? =
    uri.path
        .split("/")
        .lastOrNull()
        ?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

/** Extract a UUID from the second-to-last path segment (e.g., /api/books/{id}/progress). */
fun Request.secondToLastPathUuid(): UUID? {
    val parts = uri.path.split("/").filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        try {
            UUID.fromString(parts[parts.size - 2])
        } catch (_: IllegalArgumentException) {
            null
        }
    } else {
        null
    }
}
