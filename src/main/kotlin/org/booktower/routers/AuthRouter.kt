package org.booktower.routers

import org.booktower.handlers.AuthHandler2
import org.http4k.core.Method
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class AuthRouter(
    private val authHandler: AuthHandler2,
    private val filters: FilterSet,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            "/auth/register" bind Method.POST to filters.authRateLimit.then(authHandler::register),
            "/auth/login" bind Method.POST to filters.authRateLimit.then(authHandler::login),
            "/auth/logout" bind Method.POST to authHandler::logout,
            "/auth/forgot-password" bind Method.POST to filters.authRateLimit.then(authHandler::forgotPassword),
            "/auth/reset-password" bind Method.POST to filters.authRateLimit.then(authHandler::resetPassword),
            "/auth/refresh" bind Method.POST to filters.authRateLimit.then(authHandler::refresh),
            "/auth/revoke" bind Method.POST to authHandler::revokeToken,
            "/api/auth/change-password" bind Method.POST to filters.authRateLimit.then(filters.auth.then(authHandler::changePassword)),
            "/api/auth/change-email" bind Method.POST to filters.authRateLimit.then(filters.auth.then(authHandler::changeEmail)),
        )
}
