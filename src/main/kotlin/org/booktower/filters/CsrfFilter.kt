package org.booktower.filters

import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger("booktower.CsrfFilter")

fun csrfFilter(allowedHosts: Set<String>): Filter =
    Filter { next ->
        { req ->
            if (req.method in listOf(Method.POST, Method.PUT, Method.DELETE, Method.PATCH)) {
                val origin = req.header("Origin")
                val referer = req.header("Referer")

                val sourceHost: String? =
                    when {
                        origin != null ->
                            try {
                                URI(origin).host
                            } catch (e: Exception) {
                                origin
                            }
                        referer != null ->
                            try {
                                URI(referer).host
                            } catch (e: Exception) {
                                null
                            }
                        else -> null
                    }

                if (sourceHost == null || sourceHost in allowedHosts) {
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
