package org.booktower.routers

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * Shared HTTP filters reused across all domain routers.
 * Constructed once in KoinModule / test setup and passed to each router.
 */
data class FilterSet(
    val auth: Filter,
    val admin: Filter,
    val authRateLimit: Filter,
    /** Sets auth headers when a valid token exists but does not reject guests. */
    val optionalAuth: Filter,
)

/** Returns the handler if non-null, or a 503 SERVICE_UNAVAILABLE fallback. */
fun optionalHandler(handler: HttpHandler?): HttpHandler = handler ?: { _ -> Response(Status.SERVICE_UNAVAILABLE) }

/** Returns the handler if non-null, or a 404 NOT_FOUND with a JSON error body. */
fun optionalOr404(
    handler: HttpHandler?,
    errorJson: String = """{"error":"Not enabled"}""",
): HttpHandler = handler ?: { _ -> Response(Status.NOT_FOUND).body(errorJson) }

/** Extracts a book UUID from the path segment after "books". Shared across routers. */
fun extractBookIdFromPath(req: org.http4k.core.Request): java.util.UUID? =
    req.uri.path
        .split("/")
        .filter { it.isNotBlank() }
        .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
        ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
