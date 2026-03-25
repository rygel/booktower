package org.runary.filters

import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * Rejects requests with bodies exceeding the configured size limits.
 * JSON/form endpoints use [jsonLimit], file upload endpoints use [uploadLimit].
 */
fun bodyLimitFilter(
    jsonLimit: Long = 1_048_576L,
    uploadLimit: Long = 524_288_000L,
): Filter =
    Filter { next ->
        { req ->
            val contentLength = req.header("Content-Length")?.toLongOrNull()
            val isUpload =
                req.uri.path.contains("/upload") ||
                    req.uri.path.contains("/chapters") ||
                    req.uri.path.contains("/cover")
            val limit = if (isUpload) uploadLimit else jsonLimit

            if (contentLength != null && contentLength > limit) {
                Response(Status(413, "Payload Too Large"))
                    .header("Content-Type", "application/json")
                    .body("""{"error":"PAYLOAD_TOO_LARGE","message":"Request body exceeds ${limit / 1024}KB limit"}""")
            } else {
                next(req)
            }
        }
    }
