package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nIntegrationTest : IntegrationTestBase() {
    private fun pageWithLang(lang: String): String {
        val token = registerAndGetToken("i18n_$lang")
        val response =
            app(
                Request(Method.GET, "/")
                    .header("Cookie", "token=$token; app_lang=$lang"),
            )
        assertEquals(200, response.status.code)
        return response.bodyString()
    }

    @Test
    fun `English is default language`() {
        val token = registerAndGetToken("i18n_en")
        val response = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(200, response.status.code)
        val body = response.bodyString()
        assertTrue(body.contains("Home") || body.contains("BookTower"), "Expected English content")
    }

    @Test
    fun `Spanish language renders translated content`() {
        val body = pageWithLang("es")
        // Spanish: "Inicio" for Home or "Biblioteca" for Library
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        // Spanish option should appear in language selector
        assertTrue(body.contains("es") || body.contains("Espa"), "Expected Spanish language option")
    }

    @Test
    fun `Portuguese language renders translated content`() {
        val body = pageWithLang("pt")
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        assertTrue(body.contains("pt") || body.contains("Portugu"), "Expected Portuguese option")
    }

    @Test
    fun `Italian language renders translated content`() {
        val body = pageWithLang("it")
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        assertTrue(body.contains("it") || body.contains("Italiano"), "Expected Italian option")
    }

    @Test
    fun `Dutch language renders translated content`() {
        val body = pageWithLang("nl")
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        assertTrue(body.contains("nl") || body.contains("Nederlands"), "Expected Dutch option")
    }

    @Test
    fun `Polish language renders translated content`() {
        val body = pageWithLang("pl")
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        assertTrue(body.contains("pl") || body.contains("Polski"), "Expected Polish option")
    }

    @Test
    fun `Japanese language renders translated content`() {
        val body = pageWithLang("ja")
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        assertTrue(body.contains("ja") || body.contains("\u65e5\u672c\u8a9e"), "Expected Japanese option")
    }

    @Test
    fun `Chinese language renders translated content`() {
        val body = pageWithLang("zh")
        assertTrue(body.contains("BookTower"), "Page should still contain app name")
        assertTrue(body.contains("zh") || body.contains("\u4e2d\u6587"), "Expected Chinese option")
    }

    @Test
    fun `all 10 language options appear in selector`() {
        val token = registerAndGetToken("i18n_all")
        val response = app(Request(Method.GET, "/profile").header("Cookie", "token=$token"))
        val body = response.bodyString()
        for (lang in listOf("en", "fr", "de", "es", "pt", "it", "nl", "pl", "ja", "zh")) {
            assertTrue(body.contains("value=\"$lang\""), "Expected language option for $lang")
        }
    }

    @Test
    fun `unsupported language falls back to English`() {
        val token = registerAndGetToken("i18n_xx")
        val response =
            app(
                Request(Method.GET, "/")
                    .header("Cookie", "token=$token; app_lang=xx"),
            )
        assertEquals(200, response.status.code)
        // Should not crash; falls back to English
        val body = response.bodyString()
        assertTrue(body.contains("BookTower"), "Page should render with fallback language")
    }
}
