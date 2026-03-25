package org.runary.web

import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestKey
import org.runary.i18n.I18nService
import org.runary.model.ThemeCatalog
import java.util.Locale

class WebContext(
    val request: Request,
) {
    companion object {
        val KEY = RequestKey.required<WebContext>("web.context")

        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
        val SUPPORTED_LANGS = listOf("en", "fr", "de", "es", "pt", "it", "nl", "pl", "ja", "zh")

        /** Set at startup to enable loading user preferences from DB. */
        @Volatile var settingsProvider: ((java.util.UUID, String) -> String?)? = null
    }

    /** Extracts user ID from the auth header (set by jwtAuthFilter). */
    private val userId: java.util.UUID? by lazy {
        request.header("X-Auth-User-Id")?.let {
            try {
                java.util.UUID.fromString(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun userSetting(key: String): String? = userId?.let { settingsProvider?.invoke(it, key) }

    val lang: String by lazy {
        request.query("lang")
            ?: userSetting("pref.lang")
            ?: request.cookie(LANG_COOKIE)?.value
            ?: Locale
                .getDefault()
                .language
                .lowercase()
                .let { if (it in SUPPORTED_LANGS) it else "en" }
    }

    val theme: String by lazy {
        val value =
            request.query("theme")
                ?: userSetting("pref.theme")
                ?: request.cookie(THEME_COOKIE)?.value
                ?: "catppuccin-mocha"
        if (ThemeCatalog.allThemes().any { it.id == value }) value else "catppuccin-mocha"
    }

    val i18n: I18nService by lazy {
        I18nService.create("messages").also {
            it.setLocale(Locale.of(lang))
        }
    }

    val themeCss: String by lazy {
        ThemeCatalog.toCssVariables(theme)
    }

    fun url(path: String): String = path
}
