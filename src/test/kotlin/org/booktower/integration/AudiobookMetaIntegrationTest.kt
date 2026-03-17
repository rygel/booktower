package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudiobookMetaIntegrationTest : IntegrationTestBase() {

    private fun smallPng(): ByteArray = byteArrayOf(
        -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
        0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, -112, 119, 83, -34, 0,
        0, 0, 12, 73, 68, 65, 84, 8, -41, 99, -8, -49, -64, 0, 0, 0, 2,
        0, 1, -30, 33, -68, 51, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126,
    )

    @Test
    fun `GET audiobook-meta returns 404 when none set`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/audiobook-meta").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `PUT creates audiobook meta with narrator and abridged flag`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.PUT, "/api/books/$bookId/audiobook-meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"narrator":"John Smith","abridged":true,"durationSec":36000}"""),
        )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("John Smith", tree.get("narrator").asText())
        assertTrue(tree.get("abridged").asBoolean())
        assertEquals(36000, tree.get("durationSec").asInt())
    }

    @Test
    fun `GET returns previously set meta`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/audiobook-meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"narrator":"Jane Doe","abridged":false}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId/audiobook-meta").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Jane Doe", tree.get("narrator").asText())
        assertFalse(tree.get("abridged").asBoolean())
    }

    @Test
    fun `PUT updates existing meta`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/audiobook-meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"narrator":"Old Narrator","abridged":false}"""),
        )
        app(
            Request(Method.PUT, "/api/books/$bookId/audiobook-meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"narrator":"New Narrator","abridged":true}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId/audiobook-meta").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("New Narrator", tree.get("narrator").asText())
        assertTrue(tree.get("abridged").asBoolean())
    }

    @Test
    fun `DELETE removes meta`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/audiobook-meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"narrator":"Someone","abridged":false}"""),
        )

        val delResp = app(Request(Method.DELETE, "/api/books/$bookId/audiobook-meta").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val getResp = app(Request(Method.GET, "/api/books/$bookId/audiobook-meta").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, getResp.status)
    }

    @Test
    fun `POST audiobook-cover uploads and GET retrieves it`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val uploadResp = app(
            Request(Method.POST, "/api/books/$bookId/audiobook-cover")
                .header("Cookie", "token=$token")
                .header("X-Filename", "cover.png")
                .body(String(smallPng(), Charsets.ISO_8859_1)),
        )
        assertEquals(Status.OK, uploadResp.status)
        val tree = Json.mapper.readTree(uploadResp.bodyString())
        assertTrue(tree.get("coverUrl").asText().contains(bookId))

        val getResp = app(Request(Method.GET, "/api/books/$bookId/audiobook-cover").header("Cookie", "token=$token"))
        assertEquals(Status.OK, getResp.status)
        assertEquals("image/png", getResp.header("Content-Type"))
    }

    @Test
    fun `audiobook-meta endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/audiobook-meta"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
