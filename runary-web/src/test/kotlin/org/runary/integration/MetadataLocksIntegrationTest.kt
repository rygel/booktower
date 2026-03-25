package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class MetadataLocksIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET metadata-locks returns empty list by default`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/metadata-locks")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("lockedFields")?.isArray == true)
        assertEquals(0, tree.get("lockedFields")?.size())
    }

    @Test
    fun `PUT metadata-locks sets locked fields`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/metadata-locks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"lockedFields":["title","author","isbn"]}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        val fields = tree.get("lockedFields").map { it.asText() }.toSet()
        assertTrue("title" in fields)
        assertTrue("author" in fields)
        assertTrue("isbn" in fields)
    }

    @Test
    fun `GET metadata-locks returns previously set locks`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/metadata-locks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"lockedFields":["title","description"]}"""),
        )

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/metadata-locks")
                    .header("Cookie", "token=$token"),
            )
        val tree = Json.mapper.readTree(resp.bodyString())
        val fields = tree.get("lockedFields").map { it.asText() }.toSet()
        assertTrue("title" in fields)
        assertTrue("description" in fields)
    }

    @Test
    fun `PUT metadata-locks replaces previous locks`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/metadata-locks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"lockedFields":["title","author"]}"""),
        )
        app(
            Request(Method.PUT, "/api/books/$bookId/metadata-locks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"lockedFields":["isbn"]}"""),
        )

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/metadata-locks")
                    .header("Cookie", "token=$token"),
            )
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, tree.get("lockedFields").size())
        assertEquals("isbn", tree.get("lockedFields").get(0).asText())
    }

    @Test
    fun `GET book includes lockedFields`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/metadata-locks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"lockedFields":["publisher"]}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        val locked = tree.get("lockedFields")?.map { it.asText() }?.toSet() ?: emptySet<String>()
        assertTrue("publisher" in locked)
    }

    @Test
    fun `metadata-locks endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/metadata-locks"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
