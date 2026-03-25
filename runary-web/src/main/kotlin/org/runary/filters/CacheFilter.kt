package org.runary.filters

import org.http4k.core.Filter

fun staticCacheFilter(): Filter =
    Filter { next ->
        { req ->
            val response = next(req)
            val path = req.uri.path
            when {
                // Vendor assets (htmx, remixicon) — rarely change
                path.startsWith("/static/vendor/") -> {
                    response
                        .header("Cache-Control", "public, max-age=604800, immutable")
                        .header("Vary", "Accept-Encoding")
                }

                // Images and icons — stable across releases
                path.startsWith("/static/images/") || path.startsWith("/static/icons/") -> {
                    response
                        .header("Cache-Control", "public, max-age=604800")
                        .header("Vary", "Accept-Encoding")
                }

                // App CSS and JS — may change between releases
                path.startsWith("/static/css/") || path.startsWith("/static/js/") -> {
                    response
                        .header("Cache-Control", "public, max-age=86400")
                        .header("Vary", "Accept-Encoding")
                }

                // Other static paths and cover images
                path.startsWith("/static") || path.startsWith("/covers") -> {
                    response
                        .header("Cache-Control", "public, max-age=86400")
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
