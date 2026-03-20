package org.booktower.web

import org.booktower.i18n.I18nService
import org.booktower.model.ThemeCatalog
import org.booktower.model.ThemeDefinition
import org.booktower.services.AuthService
import org.booktower.services.JwtService
import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import java.security.MessageDigest
import java.util.UUID

/**
 * Common page context passed to all templates that use the layout wrapper.
 * Bundles user info, theme, language, and i18n into one object so handlers
 * can't accidentally forget to pass username or gravatarHash.
 *
 * Create via [PageContext.from] in handlers.
 */
data class PageContext(
    val username: String?,
    val gravatarHash: String,
    val themeCss: String,
    val currentTheme: String,
    val lang: String,
    val themes: List<ThemeDefinition>,
    val i18n: I18nService,
    val isAdmin: Boolean,
) {
    companion object {
        /**
         * Build a PageContext from a request. Looks up the authenticated user
         * and resolves theme/language preferences.
         */
        fun from(
            req: Request,
            jwtService: JwtService,
            authService: AuthService,
        ): PageContext {
            val ctx = WebContext(req)
            val userId = extractUserId(req, jwtService)
            val user = userId?.let { authService.getUserById(it) }
            val gravatarHash =
                user
                    ?.email
                    ?.trim()
                    ?.lowercase()
                    ?.let { md5(it) } ?: ""
            val isAdmin = req.header("X-Auth-Is-Admin")?.toBoolean() ?: false

            return PageContext(
                username = user?.username,
                gravatarHash = gravatarHash,
                themeCss = ctx.themeCss,
                currentTheme = ctx.theme,
                lang = ctx.lang,
                themes = ThemeCatalog.allThemes(),
                i18n = ctx.i18n,
                isAdmin = isAdmin,
            )
        }

        private fun extractUserId(
            req: Request,
            jwtService: JwtService,
        ): UUID? {
            // Try the header set by jwtAuthFilter first
            req.header("X-Auth-User-Id")?.let {
                return try {
                    UUID.fromString(it)
                } catch (_: Exception) {
                    null
                }
            }
            // Fall back to cookie
            val token = req.cookie("token")?.value ?: return null
            return jwtService.extractUserId(token)
        }

        private fun md5(input: String): String =
            MessageDigest
                .getInstance("MD5")
                .digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    /** Convert to a map for template rendering — merges with page-specific params. */
    fun toMap(vararg extra: Pair<String, Any?>): Map<String, Any?> =
        mapOf(
            "username" to username,
            "gravatarHash" to gravatarHash,
            "themeCss" to themeCss,
            "currentTheme" to currentTheme,
            "lang" to lang,
            "themes" to themes,
            "i18n" to i18n,
            "isAdmin" to isAdmin,
        ) + extra.toMap()
}
