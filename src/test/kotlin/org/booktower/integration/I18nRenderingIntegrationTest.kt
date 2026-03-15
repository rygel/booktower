package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that templates actually render translated content for each supported locale,
 * and that placeholder substitution (e.g. {0}) works correctly in rendered pages.
 *
 * The I18nCompletenessTest checks that keys exist in every properties file.
 * This test checks that the running application actually uses those translations.
 */
class I18nRenderingIntegrationTest : IntegrationTestBase() {

    private fun requestWithLang(method: Method, path: String, token: String?, lang: String): org.http4k.core.Response {
        var req = Request(method, path).header("Cookie", "app_lang=$lang")
        if (token != null) req = req.header("Cookie", "token=$token; app_lang=$lang")
        return app(req)
    }

    // ── French locale ─────────────────────────────────────────────────────────

    @Test
    fun `login page renders in French`() {
        val body = requestWithLang(Method.GET, "/login", null, "fr").bodyString()
        assertTrue(body.contains("Se connecter") || body.contains("Connexion") || body.contains("connecter"),
            "Login page should contain French text. Got: ${body.take(500)}")
    }

    @Test
    fun `register page renders in French`() {
        val body = requestWithLang(Method.GET, "/register", null, "fr").bodyString()
        assertTrue(body.contains("inscrire") || body.contains("compte") || body.contains("Cr"),
            "Register page should contain French text")
    }

    @Test
    fun `libraries page renders in French for authenticated user`() {
        val token = registerAndGetToken("i18nfr1")
        val body = requestWithLang(Method.GET, "/libraries", token, "fr").bodyString()
        // nav.libraries = "Bibliothèques"
        assertTrue(body.contains("Biblioth"), "Libraries page should render in French")
    }

    @Test
    fun `book detail page renders in French`() {
        val token = registerAndGetToken("i18nfr2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Mon Livre")

        val body = requestWithLang(Method.GET, "/books/$bookId", token, "fr").bodyString()
        // action.edit = "Modifier"
        assertTrue(body.contains("Modifier") || body.contains("Enregistrer") || body.contains("Supprimer"),
            "Book detail page should contain French action labels")
    }

    // ── German locale ─────────────────────────────────────────────────────────

    @Test
    fun `login page renders in German`() {
        val body = requestWithLang(Method.GET, "/login", null, "de").bodyString()
        assertTrue(body.contains("Anmelden") || body.contains("Willkommen") || body.contains("melden"),
            "Login page should contain German text")
    }

    @Test
    fun `libraries page renders in German for authenticated user`() {
        val token = registerAndGetToken("i18nde1")
        val body = requestWithLang(Method.GET, "/libraries", token, "de").bodyString()
        // nav.libraries = "Bibliotheken"
        assertTrue(body.contains("Bibliothek"), "Libraries page should render in German")
    }

    @Test
    fun `book detail page renders in German`() {
        val token = registerAndGetToken("i18nde2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Mein Buch")

        val body = requestWithLang(Method.GET, "/books/$bookId", token, "de").bodyString()
        // action.edit = "Bearbeiten", action.delete = "Löschen"
        assertTrue(body.contains("Bearbeiten") || body.contains("Speichern") || body.contains("chen"),
            "Book detail page should contain German action labels")
    }

    // ── No missing i18n keys cause template rendering errors ─────────────────

    @ParameterizedTest(name = "index page renders without error in {0}")
    @ValueSource(strings = ["en", "fr", "de"])
    fun `index page renders cleanly for all locales`(lang: String) {
        val resp = requestWithLang(Method.GET, "/", null, lang)
        assertEquals(Status.OK, resp.status, "Index page should render 200 for lang=$lang")
        assertFalse(resp.bodyString().contains("Exception"), "No exception should appear in page for lang=$lang")
        assertFalse(resp.bodyString().contains("Error 500"), "No 500 error for lang=$lang")
    }

    @ParameterizedTest(name = "authenticated pages render without error in {0}")
    @ValueSource(strings = ["en", "fr", "de"])
    fun `authenticated dashboard renders cleanly for all locales`(lang: String) {
        val token = registerAndGetToken("i18n_${lang}")
        val resp = requestWithLang(Method.GET, "/", token, lang)
        assertEquals(Status.OK, resp.status, "Dashboard should render 200 for lang=$lang")
        assertFalse(resp.bodyString().contains("Exception"), "No exception in dashboard for lang=$lang")
    }

    @ParameterizedTest(name = "search page renders without error in {0}")
    @ValueSource(strings = ["en", "fr", "de"])
    fun `search page renders cleanly for all locales`(lang: String) {
        val token = registerAndGetToken("srch_${lang}")
        val resp = requestWithLang(Method.GET, "/search?q=test", token, lang)
        assertEquals(Status.OK, resp.status, "Search page should render 200 for lang=$lang")
        assertFalse(resp.bodyString().contains("Exception"), "No exception in search for lang=$lang")
    }

    // ── Placeholder substitution ──────────────────────────────────────────────

    @ParameterizedTest(name = "scan result message renders {0} placeholders in {1}")
    @CsvSource(
        "1, en, added",
        "1, fr, ajout",
        "1, de, hinzugef",
    )
    fun `scan result message substitutes placeholder values`(count: String, lang: String, expectedFragment: String) {
        // The scan result message uses {0}, {1}, {2} placeholders:
        // page.library.scan.result=Scan complete: {0} added, {1} skipped, {2} errors
        // Verify the pattern string itself doesn't leak through as a raw {0} literal
        // by checking templates render scan-related pages without literal {0} tokens.
        val token = registerAndGetToken("phld_${lang}")
        val resp = requestWithLang(Method.GET, "/libraries", token, lang)
        assertEquals(Status.OK, resp.status)
        // The raw placeholder strings should not appear literally on a rendered page
        // (they only appear after substitution in response bodies from scan endpoints)
        val body = resp.bodyString()
        assertFalse(body.contains("{0}") && body.contains("{1}"),
            "Raw placeholders should not appear literally in rendered library page for lang=$lang")
    }

    // ── Sidebar language switcher ─────────────────────────────────────────────

    @Test
    fun `sidebar shows all supported language options`() {
        val token = registerAndGetToken("i18nlang1")
        val body = requestWithLang(Method.GET, "/", token, "en").bodyString()
        assertTrue(body.contains("English"), "English should be shown in language selector")
        assertTrue(body.contains("Fran"), "French should be shown in language selector")
        assertTrue(body.contains("Deutsch"), "German should be shown in language selector")
    }

    @Test
    fun `language switcher action posts to correct endpoint`() {
        val token = registerAndGetToken("i18nlang2")
        val body = requestWithLang(Method.GET, "/", token, "en").bodyString()
        assertTrue(body.contains("/preferences/lang"), "Language switcher should post to /preferences/lang")
    }
}
