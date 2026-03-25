package org.runary.filters

import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status
import org.runary.config.Json
import org.runary.models.ErrorResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("runary.ErrorFilter")

fun globalErrorFilter(): Filter =
    Filter { next ->
        { req ->
            try {
                next(req)
            } catch (e: IllegalArgumentException) {
                logger.warn("Bad request on ${req.method} ${req.uri}: ${e.message}")
                Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(
                        Json.mapper.writeValueAsString(
                            ErrorResponse("BAD_REQUEST", e.message ?: "Invalid request"),
                        ),
                    )
            } catch (e: IllegalStateException) {
                logger.error("State error on ${req.method} ${req.uri}: ${e.message}")
                Response(Status.CONFLICT)
                    .header("Content-Type", "application/json")
                    .body(
                        Json.mapper.writeValueAsString(
                            ErrorResponse("CONFLICT", e.message ?: "Conflict"),
                        ),
                    )
            } catch (e: Exception) {
                logger.error("Unhandled error on ${req.method} ${req.uri}", e)
                Response(Status.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(
                        Json.mapper.writeValueAsString(
                            ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"),
                        ),
                    )
            }
        }
    }
