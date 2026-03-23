package org.runary.filters

import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger("runary.CsrfFilter")

/** Extracts host:port from URI, omitting default ports (80/443). */
private fun extractHostWithPort(uri: URI): String? {
    val host = uri.host ?: return null
    val port = uri.port
    return if (port > 0 && port != 80 && port != 443) "$host:$port" else host
}

fun csrfFilter(allowedHosts: Set<String>): Filter =
    Filter { next ->
        { req ->
            if (req.method in listOf(Method.POST, Method.PUT, Method.DELETE, Method.PATCH)) {
                val origin = req.header("Origin")
                val referer = req.header("Referer")

                val sourceHost: String? =
                    when {
                        origin != null -> {
                            try {
                                extractHostWithPort(URI(origin))
                            } catch (_: Exception) {
                                origin
                            }
                        }

                        referer != null -> {
                            try {
                                extractHostWithPort(URI(referer))
                            } catch (_: Exception) {
                                null
                            }
                        }

                        else -> {
                            null
                        }
                    }

                val hostOnly = sourceHost?.substringBefore(':')
                if (sourceHost == null || sourceHost in allowedHosts || hostOnly in allowedHosts) {
                    next(req)
                } else {
                    logger.warn("CSRF blocked: ${req.method} ${req.uri} from origin=$origin referer=$referer")
                    Response(Status.FORBIDDEN)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"CSRF_FAILED","message":"Cross-site request blocked"}""")
                }
            } else {
                next(req)
            }
        }
    }
