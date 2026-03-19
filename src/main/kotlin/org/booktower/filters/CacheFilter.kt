package org.booktower.filters

import org.http4k.core.Filter

fun staticCacheFilter(): Filter =
    Filter { next ->
        { req ->
            val response = next(req)
            val path = req.uri.path
            when {
                path.startsWith("/static") || path.startsWith("/covers") -> {
                    response
                        .header("Cache-Control", "public, max-age=86400, immutable")
                        .header("Vary", "Accept-Encoding")
                }

                // HTML pages: allow caching but require revalidation so browser
                // always checks for fresh content after template changes
                response.header("Content-Type")?.contains("text/html") == true -> {
                    response
                        .header("Cache-Control", "no-cache")
                }

                else -> {
                    response
                }
            }
        }
    }
