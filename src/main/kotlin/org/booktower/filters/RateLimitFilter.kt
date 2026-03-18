package org.booktower.filters

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("booktower.RateLimitFilter")

/**
 * Sliding-window rate limiter keyed by client IP.
 *
 * Requests with no resolvable IP (in-process tests where [Request.source] is null
 * and no X-Forwarded-For header is present) are passed through unchanged so that
 * integration tests are not affected.
 */
class RateLimitFilter(
    private val maxRequests: Int = 10,
    private val windowSeconds: Long = 60,
) : Filter {
    companion object {
        private const val MS_PER_SECOND = 1000L
    }

    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()

    override fun invoke(next: HttpHandler): HttpHandler =
        { request ->
            val ip =
                request
                    .header("X-Forwarded-For")
                    ?.split(",")
                    ?.firstOrNull()
                    ?.trim()
                    ?: request.source?.address

            if (ip == null || ip == "127.0.0.1" || ip == "::1" || ip == "0:0:0:0:0:0:0:1") {
                next(request)
            } else {
                val now = System.currentTimeMillis()
                val windowMs = windowSeconds * MS_PER_SECOND
                val bucket = buckets.getOrPut(ip) { ArrayDeque() }

                val allowed =
                    synchronized(bucket) {
                        while (bucket.isNotEmpty() && bucket.peekFirst() < now - windowMs) {
                            bucket.pollFirst()
                        }
                        if (bucket.size < maxRequests) {
                            bucket.addLast(now)
                            true
                        } else {
                            false
                        }
                    }

                if (allowed) {
                    next(request)
                } else {
                    logger.warn("Rate limit exceeded for IP $ip")
                    Response(Status.TOO_MANY_REQUESTS)
                        .header("Content-Type", "application/json")
                        .header("Retry-After", windowSeconds.toString())
                        .body("""{"error":"TOO_MANY_REQUESTS","message":"Too many requests. Please try again later."}""")
                }
            }
        }
}
