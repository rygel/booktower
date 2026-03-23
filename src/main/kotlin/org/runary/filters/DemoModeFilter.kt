package org.runary.filters

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

// Blocks all state-mutating API requests when demo mode is active.
// Auth endpoints (/auth/login, /auth/register) are always allowed.
class DemoModeFilter(
    private val enabled: Boolean,
) : Filter {
    private val demoBlockedResponse =
        Response(Status.FORBIDDEN)
            .header("Content-Type", "application/json")
            .body("""{"error":"DEMO_MODE","message":"This is a demo instance. Modifications are disabled."}""")

    private val mutatingMethods = setOf(Method.POST, Method.PUT, Method.DELETE, Method.PATCH)

    private val allowedPostPaths = setOf("/auth/login", "/auth/register")

    override fun invoke(next: HttpHandler): HttpHandler =
        { req: Request ->
            if (enabled &&
                req.method in mutatingMethods &&
                req.uri.path.startsWith("/api/") &&
                req.uri.path !in allowedPostPaths
            ) {
                demoBlockedResponse
            } else {
                next(req)
            }
        }
}
