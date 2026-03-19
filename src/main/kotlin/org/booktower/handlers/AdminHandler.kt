package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.AuthenticatedUser
import org.booktower.model.ThemeCatalog
import org.booktower.models.ErrorResponse
import org.booktower.models.SuccessResponse
import org.booktower.services.AdminService
import org.booktower.services.AuditService
import org.booktower.services.ComicPageHashService
import org.booktower.services.DuplicateDetectionService
import org.booktower.services.EmailService
import org.booktower.services.LibraryAccessService
import org.booktower.services.PasswordResetService
import org.booktower.services.SeedService
import org.booktower.services.UserPermissions
import org.booktower.services.UserPermissionsService
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
    private val passwordResetService: PasswordResetService,
    private val seedService: SeedService,
    private val emailService: EmailService,
    private val appBaseUrl: String,
    private val duplicateDetectionService: DuplicateDetectionService? = null,
    private val auditService: AuditService? = null,
    private val userPermissionsService: UserPermissionsService? = null,
    private val libraryAccessService: LibraryAccessService? = null,
    private val comicPageHashService: ComicPageHashService? = null,
) {
    fun adminPage(req: Request): Response {
        val ctx = WebContext(req)
        val users = adminService.listUsers()
        val currentUserId = AuthenticatedUser.from(req).toString()
        val content =
            templateRenderer.render(
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

    /** POST /admin/seed — seeds demo public-domain book data for the current admin user */
    fun seed(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val ctx = WebContext(req)
        val result =
            seedService.seed(userId)
                ?: return Response(Status.CONFLICT)
                    .header("HX-Trigger", toast(ctx.i18n.translate("msg.seed.already"), "info"))
                    .body("")
        val msg =
            ctx.i18n
                .translate("msg.seed.done")
                .replace("{0}", result.libraries.toString())
                .replace("{1}", result.books.toString())
        return Response(Status.OK)
            .header("HX-Trigger", toast(msg))
            .body("")
    }

    /** POST /admin/seed/files — downloads EPUB files from Project Gutenberg for seeded books */
    fun seedFiles(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val ctx = WebContext(req)
        val result =
            seedService.seedFiles(userId)
                ?: return Response(Status.CONFLICT)
                    .header("HX-Trigger", toast(ctx.i18n.translate("msg.seed.files.no.seed"), "info"))
                    .body("")
        val msg =
            ctx.i18n
                .translate("msg.seed.files.queued")
                .replace("{0}", result.queued.toString())
        return Response(Status.OK)
            .header("HX-Trigger", toast(msg))
            .body("")
    }

    /** POST /admin/seed/librivox — creates LibriVox audiobooks library and queues chapter downloads */
    fun seedLibrivox(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val ctx = WebContext(req)
        val result =
            seedService.seedLibrivox(userId)
                ?: return Response(Status.CONFLICT)
                    .header("HX-Trigger", toast(ctx.i18n.translate("msg.seed.librivox.already"), "info"))
                    .body("")
        val msg =
            ctx.i18n
                .translate("msg.seed.librivox.queued")
                .replace("{0}", result.queued.toString())
        return Response(Status.OK)
            .header("HX-Trigger", toast(msg))
            .body("")
    }

    /** POST /admin/seed/full-demo — seeds demo data enriched with all features */
    fun seedFullDemo(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val ctx = WebContext(req)
        val result =
            seedService.seedFullDemo(userId)
                ?: return Response(Status.CONFLICT)
                    .header("HX-Trigger", toast(ctx.i18n.translate("msg.seed.already"), "info"))
                    .body("")
        val msg =
            ctx.i18n
                .translate("msg.seed.full-demo.done")
                .replace("{0}", result.libraries.toString())
                .replace("{1}", result.books.toString())
        return Response(Status.OK)
            .header("HX-Trigger", toast(msg))
            .body("")
    }

    /** POST /admin/seed/comics — creates Public Domain Comics library and queues CBZ downloads */
    fun seedComics(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val ctx = WebContext(req)
        val result =
            seedService.seedComics(userId)
                ?: return Response(Status.CONFLICT)
                    .header("HX-Trigger", toast(ctx.i18n.translate("msg.seed.comics.already"), "info"))
                    .body("")
        val msg =
            ctx.i18n
                .translate("msg.seed.comics.queued")
                .replace("{0}", result.queued.toString())
        return Response(Status.OK)
            .header("HX-Trigger", toast(msg))
            .body("")
    }

    fun resetDatabase(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val ctx = WebContext(req)
        adminService.resetDatabase(userId)
        return Response(Status.OK)
            .header("HX-Trigger", toast(ctx.i18n.translate("admin.danger.reset.done")))
            .header("HX-Refresh", "true")
            .body("")
    }

    /** GET /api/admin/password-reset-tokens — lists active (unused, unexpired) tokens for admin display */
    fun listResetTokens(req: Request): Response {
        val tokens = passwordResetService.listActiveTokens()
        val result =
            tokens.map { (id, username, expiresAt) ->
                mapOf("id" to id, "username" to username, "expiresAt" to expiresAt)
            }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(result))
    }

    /** POST /api/admin/users/{userId}/reset-password — generates a reset link for any user */
    fun generateResetLink(req: Request): Response {
        val actorId = AuthenticatedUser.from(req)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val user = adminService.getUserById(userId) ?: return notFound("User not found")
        val token =
            passwordResetService.createToken(user.email)
                ?: return notFound("User email not found")
        val resetLink = "$appBaseUrl/reset-password?token=$token"
        emailService.sendPasswordReset(user.email, resetLink)
        auditService?.record(actorId, actorName(req), "user.password_reset", "user", userId.toString(), null, clientIp(req))
        val payload =
            mapOf(
                "resetLink" to resetLink,
                "emailSent" to emailService.config.enabled,
            )
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(payload))
    }

    /** GET /api/admin/duplicates — returns duplicate groups across all of the authenticated user's libraries */
    fun findDuplicates(req: Request): Response {
        val svc =
            duplicateDetectionService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Duplicate detection not available"}""")
        val userId = AuthenticatedUser.from(req)
        val groups = svc.findDuplicates(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(groups))
    }

    /** GET /api/admin/comic-page-duplicates — returns pages that appear in more than one comic book */
    fun findComicPageDuplicates(req: Request): Response {
        val svc =
            comicPageHashService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Comic page duplicate detection not available"}""")
        val userId = AuthenticatedUser.from(req)
        val groups = svc.findDuplicatePages(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(groups))
    }

    /** GET /api/admin/audit — returns recent audit log entries (admin only) */
    fun listAuditLog(req: Request): Response {
        val svc =
            auditService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Audit log not available"}""")
        val limit = req.query("limit")?.toIntOrNull()?.coerceIn(1, 1000) ?: 200
        val userId = req.query("userId")
        val entries =
            if (userId != null) {
                runCatching { UUID.fromString(userId) }
                    .getOrNull()
                    ?.let { svc.listForUser(it, limit) }
                    ?: return badRequest("Invalid userId")
            } else {
                svc.listRecent(limit)
            }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(entries))
    }

    fun listUsers(req: Request): Response {
        val users = adminService.listUsers()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(users))
    }

    fun promote(req: Request): Response {
        val actorId = AuthenticatedUser.from(req)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val ok = adminService.setAdmin(userId, true)
        return if (ok) {
            auditService?.record(actorId, actorName(req), "user.promote", "user", userId.toString(), null, clientIp(req))
            val msg = WebContext(req).i18n.translate("msg.admin-granted")
            Response(Status.OK)
                .header("HX-Redirect", "/admin")
                .cookie(Cookie("flash_msg", msg, path = "/"))
                .cookie(Cookie("flash_type", "success", path = "/"))
        } else {
            notFound("User not found")
        }
    }

    fun demote(req: Request): Response {
        val actorId = AuthenticatedUser.from(req)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val ok = adminService.setAdmin(userId, false)
        return if (ok) {
            auditService?.record(actorId, actorName(req), "user.demote", "user", userId.toString(), null, clientIp(req))
            val msg = WebContext(req).i18n.translate("msg.admin-revoked")
            Response(Status.OK)
                .header("HX-Redirect", "/admin")
                .cookie(Cookie("flash_msg", msg, path = "/"))
                .cookie(Cookie("flash_type", "success", path = "/"))
        } else {
            notFound("User not found")
        }
    }

    fun deleteUser(req: Request): Response {
        val actorId = AuthenticatedUser.from(req)
        val userId = extractLastId(req) ?: return badRequest("Invalid user ID")
        return runCatching { adminService.deleteUser(actorId, userId) }
            .fold(
                onSuccess = { deleted ->
                    if (deleted) {
                        auditService?.record(actorId, actorName(req), "user.delete", "user", userId.toString(), null, clientIp(req))
                        val msg = WebContext(req).i18n.translate("msg.user-deleted")
                        Response(Status.OK)
                            .header("HX-Trigger", toast(msg))
                            .body("")
                    } else {
                        notFound("User not found")
                    }
                },
                onFailure = { e -> badRequest(e.message ?: "Cannot delete user") },
            )
    }

    private fun actorName(req: Request): String = req.header("X-Auth-Username") ?: AuthenticatedUser.from(req).toString()

    private fun clientIp(req: Request): String? =
        req
            .header("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?: req.header("X-Real-IP")

    private fun extractLastId(req: Request): UUID? =
        req.uri.path
            .split("/")
            .filter { it.isNotBlank() }
            .lastOrNull()
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun extractSecondToLastId(req: Request): UUID? {
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            runCatching { UUID.fromString(parts[parts.size - 2]) }.getOrNull()
        } else {
            null
        }
    }

    private fun toast(
        message: String,
        type: String = "success",
    ): String = """{"showToast":{"message":"${message.replace("\"", "\\\"")}","type":"$type"}}"""

    private fun ok(msg: String) =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(SuccessResponse(msg)))

    fun getLibraryAccess(req: Request): Response {
        val svc = libraryAccessService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val isRestricted = svc.isRestricted(userId)
        val grants = svc.listGrantedLibraries(userId)
        val result = mapOf("restricted" to isRestricted, "grants" to grants)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(result))
    }

    fun setLibraryRestricted(req: Request): Response {
        val svc = libraryAccessService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val body =
            try {
                Json.mapper.readTree(req.bodyString())
            } catch (_: Exception) {
                null
            }
                ?: return badRequest("Request body is required")
        val restricted = body.get("restricted")?.asBoolean() ?: return badRequest("restricted field required")
        svc.setRestricted(userId, restricted)
        return Response(Status.NO_CONTENT)
    }

    fun grantLibraryAccess(req: Request): Response {
        val svc = libraryAccessService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val body =
            try {
                Json.mapper.readTree(req.bodyString())
            } catch (_: Exception) {
                null
            }
                ?: return badRequest("Request body is required")
        val libraryId = body.get("libraryId")?.asText() ?: return badRequest("libraryId required")
        val libUuid =
            try {
                java.util.UUID.fromString(libraryId)
            } catch (_: Exception) {
                return badRequest("Invalid libraryId")
            }
        svc.grantAccess(userId, libUuid)
        return Response(Status.NO_CONTENT)
    }

    fun revokeLibraryAccess(req: Request): Response {
        val svc = libraryAccessService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        // path: /api/admin/users/{userId}/library-access/{libraryId}
        if (parts.size < 2) return badRequest("Invalid path")
        val userId =
            try {
                java.util.UUID.fromString(parts[parts.size - 3])
            } catch (_: Exception) {
                return badRequest("Invalid user ID")
            }
        val libraryId =
            try {
                java.util.UUID.fromString(parts.last())
            } catch (_: Exception) {
                return badRequest("Invalid libraryId")
            }
        svc.revokeAccess(userId, libraryId)
        return Response(Status.NO_CONTENT)
    }

    fun getPermissions(req: Request): Response {
        val svc = userPermissionsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val perms = svc.getPermissions(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(perms))
    }

    fun setPermissions(req: Request): Response {
        val svc = userPermissionsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = extractSecondToLastId(req) ?: return badRequest("Invalid user ID")
        val body = req.bodyString()
        if (body.isBlank()) return badRequest("Request body is required")
        val incoming =
            try {
                Json.mapper.readValue(body, UserPermissions::class.java)
            } catch (_: Exception) {
                return badRequest("Invalid JSON")
            }
        val saved = svc.setPermissions(userId, incoming)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(saved))
    }

    private fun notFound(msg: String) =
        Response(Status.NOT_FOUND)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", msg)))

    private fun badRequest(msg: String) =
        Response(Status.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", msg)))
}
