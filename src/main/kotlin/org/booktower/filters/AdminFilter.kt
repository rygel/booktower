package org.booktower.filters

import org.booktower.config.Json
import org.booktower.models.ErrorResponse
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status

fun AdminFilter(): Filter = Filter { next ->
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
