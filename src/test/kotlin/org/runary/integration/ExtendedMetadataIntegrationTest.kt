package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtendedMetadataIntegrationTest : IntegrationTestBase() {
    @Test
    fun `PUT extended-metadata sets subtitle and language`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/extended-metadata")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"subtitle":"A Novel","language":"en","contentRating":"PG","ageRating":"13+"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("A Novel", tree.get("subtitle")?.asText())
        assertEquals("en", tree.get("language")?.asText())
    }

    @Test
    fun `GET book returns extended metadata fields`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/extended-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"subtitle":"Subtitle Here","language":"fr","contentRating":"PG-13","ageRating":"16+"}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Subtitle Here", tree.get("subtitle")?.asText())
        assertEquals("fr", tree.get("language")?.asText())
        assertEquals("PG-13", tree.get("contentRating")?.asText())
        assertEquals("16+", tree.get("ageRating")?.asText())
    }

    @Test
    fun `PUT moods sets moods on book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/moods")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"moods":["dark","mysterious"]}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("moods")?.size() == 2)
    }

    @Test
    fun `GET book returns moods`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/moods")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"moods":["hopeful"]}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("hopeful", tree.get("moods")?.get(0)?.asText())
    }

    @Test
    fun `PUT moods replaces previous moods`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/moods")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"moods":["sad","melancholic"]}"""),
        )
        app(
            Request(Method.PUT, "/api/books/$bookId/moods")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"moods":["joyful"]}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, tree.get("moods")?.size())
        assertEquals("joyful", tree.get("moods")?.get(0)?.asText())
    }

    @Test
    fun `PUT extended-metadata on nonexistent book returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/extended-metadata")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"subtitle":"x"}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `extended-metadata endpoints require authentication`() {
        val resp = app(Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/extended-metadata"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
