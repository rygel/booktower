package org.runary.handlers

import org.runary.services.AuthService
import org.runary.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import java.util.UUID

/** Shared utilities for page handlers. */

fun pageAuth(
    req: Request,
    jwtService: JwtService,
    authService: AuthService,
): UUID? {
    val token = req.cookie("token")?.value ?: return null
    val userId = jwtService.extractUserId(token) ?: return null
    return if (authService.getUserById(userId) != null) userId else null
}

fun pageAuthIsAdmin(
    req: Request,
    jwtService: JwtService,
): Boolean {
    val token = req.cookie("token")?.value ?: return false
    return jwtService.extractIsAdmin(token)
}

fun pageContext(
    req: Request,
    jwtService: JwtService,
    authService: AuthService,
): org.runary.web.PageContext = org.runary.web.PageContext.from(req, jwtService, authService)

fun redirectToLogin(): Response =
    Response(Status.SEE_OTHER)
        .header("Location", "/login")
        .cookie(Cookie(name = "token", value = "", path = "/", maxAge = 0))

fun htmlOk(html: String): Response = Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(html)

fun toast(
    message: String,
    type: String = "success",
): String = """{"showToast":{"message":"${message.replace("\"", "\\\"")}","type":"$type"}}"""

fun Request.lastPathSegment(): String? =
    uri.path
        .split("/")
        .filter { it.isNotBlank() }
        .lastOrNull()

fun Request.secondToLastPathSegment(): String? {
    val parts = uri.path.split("/").filter { it.isNotBlank() }
    return if (parts.size >= 2) parts[parts.size - 2] else null
}

fun String?.toUuidOrNull(): UUID? =
    if (this == null) {
        null
    } else {
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
