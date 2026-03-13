package org.booktower.web

import org.booktower.i18n.I18nService
import org.booktower.model.ThemeCatalog
import org.http4k.core.Request
import org.http4k.core.cookie.cookie
import org.http4k.lens.RequestKey
import org.slf4j.LoggerFactory
import java.util.Locale

class WebContext(val request: Request) {
    private val logger = LoggerFactory.getLogger(WebContext::class.java)

    companion object {
        val KEY = RequestKey.required<WebContext>("web.context")

        const val LANG_COOKIE = "app_lang"
        const val THEME_COOKIE = "app_theme"
    }

    val lang: String by lazy {
        request.query("lang")
            ?: request.cookie(LANG_COOKIE)?.value
            ?: Locale.getDefault().language.lowercase().let { if (it == "fr") "fr" else "en" }
    }

    val theme: String by lazy {
        val value = request.query("theme") ?: request.cookie(THEME_COOKIE)?.value ?: "dark"
        if (ThemeCatalog.allThemes().any { it.id == value }) value else "dark"
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
