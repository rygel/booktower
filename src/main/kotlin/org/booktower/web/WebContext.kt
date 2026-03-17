package org.booktower.web

import org.booktower.i18n.I18nService
import org.booktower.model.ThemeCatalog
import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestKey
import java.util.Locale

class WebContext(val request: Request) {
    companion object {
        val KEY = RequestKey.required<WebContext>("web.context")

        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
        val SUPPORTED_LANGS = listOf("en", "fr", "de", "es", "pt", "it", "nl", "pl", "ja", "zh")
    }

    val lang: String by lazy {
        request.query("lang")
            ?: request.cookie(LANG_COOKIE)?.value
            ?: Locale.getDefault().language.lowercase().let { if (it in SUPPORTED_LANGS) it else "en" }
    }

    val theme: String by lazy {
        val value = request.query("theme") ?: request.cookie(THEME_COOKIE)?.value ?: "catppuccin-mocha"
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
