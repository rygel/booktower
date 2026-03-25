package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.LoginResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreferencesIntegrationTest : IntegrationTestBase() {
    // ── Login preference sync ──────────────────────────────────────────────────

    @Test
    fun `login applies stored theme and language as cookies`() {
        val username = "prefsync_${System.nanoTime()}"
        val password = org.runary.TestPasswords.DEFAULT
        val regResponse =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"$password"}"""),
            )
        val token = Json.mapper.readValue(regResponse.bodyString(), LoginResponse::class.java).token

        // Store preferences
        app(Request(Method.PUT, "/api/settings/theme").header("Cookie", "token=$token").body("nord"))
        app(Request(Method.PUT, "/api/settings/language").header("Cookie", "token=$token").body("de"))

        // Login again
        val loginResponse =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"$password"}"""),
            )

        val themeCookie = loginResponse.cookies().find { it.name == "app_theme" }
        val langCookie = loginResponse.cookies().find { it.name == "app_lang" }
        assertNotNull(themeCookie, "Login should set app_theme cookie from stored settings")
        assertEquals("nord", themeCookie!!.value)
        assertNotNull(langCookie, "Login should set app_lang cookie from stored settings")
        assertEquals("de", langCookie!!.value)
    }

    @Test
    fun `login without stored preferences sets no preference cookies`() {
        val username = "noprefs_${System.nanoTime()}"
        val password = org.runary.TestPasswords.DEFAULT
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"$password"}"""),
        )

        val loginResponse =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"$password"}"""),
            )

        val themeCookie = loginResponse.cookies().find { it.name == "app_theme" }
        val langCookie = loginResponse.cookies().find { it.name == "app_lang" }
        assertEquals(null, themeCookie, "No theme cookie should be set when user has no stored theme")
        assertEquals(null, langCookie, "No lang cookie should be set when user has no stored language")
    }

    // ── Theme endpoint ─────────────────────────────────────────────────────────

    @Test
    fun `POST preferences-theme returns 200 with style element`() {
        val response =
            app(
                Request(Method.POST, "/preferences/theme")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("theme=nord"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("<style"))
        assertTrue(body.contains("id=\"theme-style\""))
        assertTrue(body.contains("data-theme=\"nord\""))
    }

    @Test
    fun `POST preferences-theme returns CSS variables in body`() {
        val response =
            app(
                Request(Method.POST, "/preferences/theme")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("theme=dracula"),
            )
        val body = response.bodyString()
        assertTrue(body.contains(":root"))
        assertTrue(body.contains("--bt-"))
    }

    @Test
    fun `POST preferences-theme sets app_theme cookie`() {
        val response =
            app(
                Request(Method.POST, "/preferences/theme")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("theme=tokyo-night"),
            )
        val cookie = response.cookies().find { it.name == "app_theme" }
        assertNotNull(cookie)
        assertEquals("tokyo-night", cookie!!.value)
    }

    @Test
    fun `POST preferences-theme cookie has long max-age`() {
        val response =
            app(
                Request(Method.POST, "/preferences/theme")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("theme=nord"),
            )
        val cookie = response.cookies().find { it.name == "app_theme" }
        assertNotNull(cookie)
        assertTrue((cookie!!.maxAge ?: 0) > 86400, "Cookie maxAge should be at least one day")
    }

    @Test
    fun `POST preferences-theme with unknown theme defaults to catppuccin-mocha`() {
        val response =
            app(
                Request(Method.POST, "/preferences/theme")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("theme=does-not-exist"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("catppuccin-mocha"))
    }

    @Test
    fun `POST preferences-theme sets HTML content type`() {
        val response =
            app(
                Request(Method.POST, "/preferences/theme")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("theme=everforest"),
            )
        assertTrue(response.header("Content-Type")?.startsWith("text/html") == true)
    }

    @Test
    fun `all themes render valid CSS`() {
        val themes =
            org.runary.model.ThemeCatalog
                .allThemes()
                .map { it.id }
        for (themeId in themes) {
            val response =
                app(
                    Request(Method.POST, "/preferences/theme")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .body("theme=$themeId"),
                )
            assertEquals(Status.OK, response.status, "Theme $themeId should return 200")
            assertTrue(response.bodyString().contains(":root"), "Theme $themeId should have CSS variables")
            assertTrue(response.bodyString().contains(themeId), "Response should reference theme $themeId")
        }
    }

    // ── Theme round-trip ───────────────────────────────────────────────────────

    @Test
    fun `theme cookie is read on subsequent page load`() {
        val token = registerAndGetToken("themetest")
        // Load landing page with a theme cookie — the injected <style> should reflect it
        val response =
            app(
                Request(Method.GET, "/")
                    .header("Cookie", "app_theme=nord"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("theme-style"))
        // Nord theme CSS should be injected (nord uses a blue accent)
        assertTrue(response.bodyString().contains(":root"))
    }

    @Test
    fun `library page renders with theme from cookie`() {
        val token = registerAndGetToken("themelib")
        val response =
            app(
                Request(Method.GET, "/libraries")
                    .header("Cookie", "token=$token; app_theme=dracula"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("data-theme=\"dracula\""))
    }

    @Test
    fun `default theme is catppuccin-mocha when no cookie present`() {
        val response = app(Request(Method.GET, "/"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("data-theme=\"catppuccin-mocha\""))
    }

    // ── Language endpoint ──────────────────────────────────────────────────────

    @Test
    fun `POST preferences-lang returns 200`() {
        val response =
            app(
                Request(Method.POST, "/preferences/lang")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("lang=fr"),
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `POST preferences-lang sets app_lang cookie`() {
        val response =
            app(
                Request(Method.POST, "/preferences/lang")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("lang=de"),
            )
        val cookie = response.cookies().find { it.name == "app_lang" }
        assertNotNull(cookie)
        assertEquals("de", cookie!!.value)
    }

    @Test
    fun `POST preferences-lang sends HX-Refresh true`() {
        val response =
            app(
                Request(Method.POST, "/preferences/lang")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("lang=fr"),
            )
        assertEquals("true", response.header("HX-Refresh"))
    }

    @Test
    fun `POST preferences-lang cookie has long max-age`() {
        val response =
            app(
                Request(Method.POST, "/preferences/lang")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("lang=en"),
            )
        val cookie = response.cookies().find { it.name == "app_lang" }
        assertNotNull(cookie)
        assertTrue((cookie!!.maxAge ?: 0) > 86400, "Cookie maxAge should be at least one day")
    }

    @Test
    fun `POST preferences-lang with unsupported language defaults to en`() {
        val response =
            app(
                Request(Method.POST, "/preferences/lang")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("lang=xx"),
            ) // "xx" is not a supported language code
        val cookie = response.cookies().find { it.name == "app_lang" }
        assertNotNull(cookie)
        assertEquals("en", cookie!!.value)
    }

    @Test
    fun `all supported languages set cookie correctly`() {
        for (lang in listOf("en", "fr", "de")) {
            val response =
                app(
                    Request(Method.POST, "/preferences/lang")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .body("lang=$lang"),
                )
            val cookie = response.cookies().find { it.name == "app_lang" }
            assertNotNull(cookie, "Cookie should be set for lang=$lang")
            assertEquals(lang, cookie!!.value, "Cookie value should be $lang")
        }
    }

    // ── Language round-trip ────────────────────────────────────────────────────

    @Test
    fun `library page renders in German when lang cookie is de`() {
        val token = registerAndGetToken("langde")
        val response =
            app(
                Request(Method.GET, "/libraries")
                    .header("Cookie", "token=$token; app_lang=de"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        // German translation for "page.libraries.title" = "Meine Bibliotheken"
        assertTrue(body.contains("Meine Bibliotheken"), "Page should be in German")
    }

    @Test
    fun `library page renders in French when lang cookie is fr`() {
        val token = registerAndGetToken("langfr")
        val response =
            app(
                Request(Method.GET, "/libraries")
                    .header("Cookie", "token=$token; app_lang=fr"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        // French translation for "page.libraries.title" = "Mes bibliothèques"
        assertTrue(body.contains("Mes biblioth"), "Page should be in French")
    }

    @Test
    fun `library page renders in English by default`() {
        val token = registerAndGetToken("langen")
        val response =
            app(
                Request(Method.GET, "/libraries")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("My Libraries"))
    }

    @Test
    fun `html lang attribute reflects active language`() {
        val token = registerAndGetToken("htmllang")
        val response =
            app(
                Request(Method.GET, "/libraries")
                    .header("Cookie", "token=$token; app_lang=de"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("lang=\"de\""))
    }

    @Test
    fun `logout button text is translated in German`() {
        val token = registerAndGetToken("logoutde")
        val response =
            app(
                Request(Method.GET, "/libraries")
                    .header("Cookie", "token=$token; app_lang=de"),
            )
        assertEquals(Status.OK, response.status)
        // German translation for "auth.logout" = "Abmelden"
        assertTrue(response.bodyString().contains("Abmelden"))
    }
}
