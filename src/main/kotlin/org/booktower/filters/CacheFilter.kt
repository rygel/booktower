package org.booktower.filters

import org.http4k.core.Filter

fun staticCacheFilter(): Filter =
    Filter { next ->
        { req ->
            val response = next(req)
            if (req.uri.path.startsWith("/static")) {
                response
                    .header("Cache-Control", "public, max-age=86400, immutable")
                    .header("Vary", "Accept-Encoding")
            } else {
                response
            }
        }
    }
