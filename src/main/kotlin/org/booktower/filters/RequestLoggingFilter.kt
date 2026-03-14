package org.booktower.filters

import org.http4k.core.Filter
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.access")

fun RequestLoggingFilter(): Filter = Filter { next ->
    { req ->
        val start = System.currentTimeMillis()
        val response = next(req)
        val duration = System.currentTimeMillis() - start
        logger.info("${req.method} ${req.uri} -> ${response.status.code} (${duration}ms)")
        response
    }
}
