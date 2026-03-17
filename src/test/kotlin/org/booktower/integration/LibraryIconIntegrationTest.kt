package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryIconIntegrationTest : IntegrationTestBase() {

    private fun smallPng(): ByteArray {
        // Minimal valid 1x1 PNG (67 bytes)
        return byteArrayOf(
            -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
            0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, -112, 119, 83, -34, 0,
            0, 0, 12, 73, 68, 65, 84, 8, -41, 99, -8, -49, -64, 0, 0, 0, 2,
            0, 1, -30, 33, -68, 51, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
        )
    }

    @Test
    fun `GET icon returns 404 when none set`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp = app(Request(Method.GET, "/api/libraries/$libId/icon").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `POST icon uploads successfully`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp = app(
            Request(Method.POST, "/api/libraries/$libId/icon")
                .header("Cookie", "token=$token")
                .header("X-Filename", "icon.png")
                .body(String(smallPng(), Charsets.ISO_8859_1)),
        )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("iconUrl").asText().contains(libId))
    }

    @Test
    fun `GET icon returns image after upload`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        app(
            Request(Method.POST, "/api/libraries/$libId/icon")
                .header("Cookie", "token=$token")
                .header("X-Filename", "icon.png")
                .body(String(smallPng(), Charsets.ISO_8859_1)),
        )

        val resp = app(Request(Method.GET, "/api/libraries/$libId/icon").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals("image/png", resp.header("Content-Type"))
    }

    @Test
    fun `POST icon rejects unsupported extension`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp = app(
            Request(Method.POST, "/api/libraries/$libId/icon")
                .header("Cookie", "token=$token")
                .header("X-Filename", "icon.bmp")
                .body("dummy"),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST icon without X-Filename returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp = app(
            Request(Method.POST, "/api/libraries/$libId/icon")
                .header("Cookie", "token=$token")
                .body("dummy"),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `DELETE icon removes it`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        app(
            Request(Method.POST, "/api/libraries/$libId/icon")
                .header("Cookie", "token=$token")
                .header("X-Filename", "icon.png")
                .body(String(smallPng(), Charsets.ISO_8859_1)),
        )

        val delResp = app(Request(Method.DELETE, "/api/libraries/$libId/icon").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val getResp = app(Request(Method.GET, "/api/libraries/$libId/icon").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, getResp.status)
    }

    @Test
    fun `POST icon for nonexistent library returns 404`() {
        val token = registerAndGetToken()

        val resp = app(
            Request(Method.POST, "/api/libraries/00000000-0000-0000-0000-000000000000/icon")
                .header("Cookie", "token=$token")
                .header("X-Filename", "icon.png")
                .body(String(smallPng(), Charsets.ISO_8859_1)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `icon endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/libraries/00000000-0000-0000-0000-000000000000/icon"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
