package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.AuthenticatedUser
import org.booktower.model.ThemeCatalog
import org.booktower.models.ErrorResponse
import org.booktower.models.SuccessResponse
import org.booktower.services.AdminService
import org.booktower.web.WebContext
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import java.util.UUID

class AdminHandler(
    private val adminService: AdminService,
    private val templateRenderer: TemplateRenderer,
) {
    fun adminPage(req: Request): Response {
        val ctx = WebContext(req)
        val users = adminService.listUsers()
        val currentUserId = AuthenticatedUser.from(req).toString()
        val content = templateRenderer.render(
            "admin.kte",
            mapOf(
                "users" to users,
                "currentUserId" to currentUserId,
                "themeCss" to ctx.themeCss,
                "currentTheme" to ctx.theme,
                "lang" to ctx.lang,
                "themes" to ThemeCatalog.allThemes(),
                "i18n" to ctx.i18n,
                "isAdmin" to true,
            ),
        )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    fun listUsers(req: Request): Response {
        val users = adminService.listUsers()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(users))
    }

    fun promote(req: Request): Response {
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val ok = adminService.setAdmin(userId, true)
        return if (ok) {
            Response(Status.OK)
                .header("HX-Redirect", "/admin")
                .cookie(Cookie("flash_msg", "Admin status granted", path = "/"))
                .cookie(Cookie("flash_type", "success", path = "/"))
        } else notFound("User not found")
    }

    fun demote(req: Request): Response {
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val ok = adminService.setAdmin(userId, false)
        return if (ok) {
            Response(Status.OK)
                .header("HX-Redirect", "/admin")
                .cookie(Cookie("flash_msg", "Admin status revoked", path = "/"))
                .cookie(Cookie("flash_type", "success", path = "/"))
        } else notFound("User not found")
    }

    fun deleteUser(req: Request): Response {
        val actorId = AuthenticatedUser.from(req)
        val userId = extractLastId(req) ?: return badRequest("Invalid user ID")
        return runCatching { adminService.deleteUser(actorId, userId) }
            .fold(
                onSuccess = { deleted ->
                    if (deleted) Response(Status.OK)
                        .header("HX-Trigger", """{"showToast":{"message":"User deleted","type":"success"}}""")
                        .body("")
                    else notFound("User not found")
                },
                onFailure = { e -> badRequest(e.message ?: "Cannot delete user") },
            )
    }

    private fun extractLastId(req: Request): UUID? =
        req.uri.path.split("/").filter { it.isNotBlank() }.lastOrNull()
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun extractSecondToLastId(req: Request): UUID? {
        val parts = req.uri.path.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            runCatching { UUID.fromString(parts[parts.size - 2]) }.getOrNull()
        } else {
            null
        }
    }

    private fun ok(msg: String) = Response(Status.OK)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(SuccessResponse(msg)))

    private fun notFound(msg: String) = Response(Status.NOT_FOUND)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", msg)))

    private fun badRequest(msg: String) = Response(Status.BAD_REQUEST)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", msg)))
}
