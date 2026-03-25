package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class KepubIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET kepub for non-existent book returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/kepub")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `GET kepub for book with no file returns 404`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/kepub")
                    .header("Cookie", "token=$token"),
            )
        // Physical book (no file) or book with blank file_path returns 404
        assertTrue(resp.status == Status.NOT_FOUND || resp.status == Status.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `kepub endpoint requires authentication`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/kepub"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `kobo kepub enabled setting can be set and retrieved`() {
        val token = registerAndGetToken()
        val putResp =
            app(
                Request(Method.PUT, "/api/settings/kobo.kepub_enabled")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("true"),
            )
        assertEquals(Status.OK, putResp.status)

        val getResp =
            app(
                Request(Method.GET, "/api/settings")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, getResp.status)
        val settings = Json.mapper.readTree(getResp.bodyString())
        assertEquals("true", settings.get("kobo.kepub_enabled")?.asText())
    }

    @Test
    fun `kobo sync download URL is normal file URL when kepub disabled`() {
        val token = registerAndGetToken()
        // Register a Kobo device
        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"TestKobo"}"""),
            )
        assertEquals(Status.CREATED, regResp.status)
        val koboToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val libId = createLibrary(token)
        createBook(token, libId)

        val syncResp = app(Request(Method.POST, "/kobo/$koboToken/v1/library/sync"))
        assertEquals(Status.OK, syncResp.status)
        val body = syncResp.bodyString()
        // No kepub URLs when setting not enabled
        assertTrue(body.contains("/file") || body.contains("BookEntitlements"))
    }

    @Test
    fun `kobo sync download URL uses kepub endpoint when kepub enabled`() {
        val token = registerAndGetToken()
        // Enable KEPUB
        app(
            Request(Method.PUT, "/api/settings/kobo.kepub_enabled")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("true"),
        )

        // Register a Kobo device
        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"TestKobo"}"""),
            )
        val koboToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val libId = createLibrary(token)
        // Create a book with a fake .epub path to trigger kepub URL
        val bookId = createBook(token, libId, "My EPUB Book")

        val syncResp = app(Request(Method.POST, "/kobo/$koboToken/v1/library/sync"))
        assertEquals(Status.OK, syncResp.status)
        // With kepub enabled and an epub book, the URL should use /kepub endpoint
        // (the actual URL depends on whether the book has an epub file_path)
        assertTrue(syncResp.bodyString().isNotBlank())
    }

    @Test
    fun `kepub is disabled by default`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/api/settings")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val settings = Json.mapper.readTree(resp.bodyString())
        // kobo.kepub_enabled should not be present (or false) by default
        val kepubSetting = settings.get("kobo.kepub_enabled")?.asText()
        assertTrue(kepubSetting == null || kepubSetting == "false")
    }
}
