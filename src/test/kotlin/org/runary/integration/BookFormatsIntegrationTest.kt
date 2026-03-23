package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookFormatsIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET formats returns empty list for new book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/formats").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.isArray)
        assertEquals(0, tree.size())
    }

    @Test
    fun `POST formats adds a file entry`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/formats")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"filePath":"/tmp/book.epub","label":"ePub version","isPrimary":false}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("epub", tree.get("format").asText())
        assertEquals("ePub version", tree.get("label").asText())
        assertEquals(bookId, tree.get("bookId").asText())
    }

    @Test
    fun `GET formats lists all added files`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, "/api/books/$bookId/formats")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"filePath":"/tmp/book.epub","label":"ePub"}"""),
        )
        app(
            Request(Method.POST, "/api/books/$bookId/formats")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"filePath":"/tmp/book.mobi","label":"Mobi"}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId/formats").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(2, tree.size())
    }

    @Test
    fun `DELETE formats removes a file entry`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val addResp =
            app(
                Request(Method.POST, "/api/books/$bookId/formats")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"filePath":"/tmp/book.pdf","label":"PDF"}"""),
            )
        val fileId =
            Json.mapper
                .readTree(addResp.bodyString())
                .get("id")
                .asText()

        val delResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/formats/$fileId").header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, delResp.status)

        val listResp = app(Request(Method.GET, "/api/books/$bookId/formats").header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(listResp.bodyString()).size())
    }

    @Test
    fun `DELETE formats for unknown id returns 404`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/formats/00000000-0000-0000-0000-000000000000")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `marking file as primary demotes previous primary`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, "/api/books/$bookId/formats")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"filePath":"/tmp/book.epub","label":"ePub","isPrimary":true}"""),
        )
        app(
            Request(Method.POST, "/api/books/$bookId/formats")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"filePath":"/tmp/book.pdf","label":"PDF","isPrimary":true}"""),
        )

        val listResp = app(Request(Method.GET, "/api/books/$bookId/formats").header("Cookie", "token=$token"))
        val files = Json.mapper.readTree(listResp.bodyString())
        val primaries = (0 until files.size()).count { files[it].get("isPrimary").asBoolean() }
        assertEquals(1, primaries, "Only one file should be marked as primary")
    }

    @Test
    fun `formats endpoints require authentication`() {
        val getResp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/formats"))
        assertEquals(Status.UNAUTHORIZED, getResp.status)
        val postResp = app(Request(Method.POST, "/api/books/00000000-0000-0000-0000-000000000000/formats"))
        assertEquals(Status.UNAUTHORIZED, postResp.status)
    }
}
