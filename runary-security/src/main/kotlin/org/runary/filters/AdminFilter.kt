package org.runary.filters

import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status
import org.runary.config.Json
import org.runary.models.ErrorResponse

fun adminFilter(): Filter =
    Filter { next ->
        { req ->
            if (AuthenticatedUser.isAdmin(req)) {
                next(req)
            } else {
                Response(Status.FORBIDDEN)
                    .header("Content-Type", "application/json")
                    .body(
                        Json.mapper.writeValueAsString(
                            ErrorResponse("FORBIDDEN", "Admin access required"),
                        ),
                    )
            }
        }
    }
