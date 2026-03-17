package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComicMetadataIntegrationTest : IntegrationTestBase() {

    @Test
    fun `GET comic-metadata returns empty defaults for new book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/comic-metadata").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("issueNumber").isNull)
        assertTrue(tree.get("characters").isArray && tree.get("characters").size() == 0)
        assertTrue(tree.get("teams").isArray && tree.get("teams").size() == 0)
    }

    @Test
    fun `PUT comic-metadata stores basic fields`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.PUT, "/api/books/$bookId/comic-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"issueNumber":"#42","volumeNumber":"Vol. 1","comicSeries":"Amazing Stories","coverDate":"2024-01","storyArc":"The Beginning"}"""),
        )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("#42", tree.get("issueNumber").asText())
        assertEquals("Vol. 1", tree.get("volumeNumber").asText())
        assertEquals("Amazing Stories", tree.get("comicSeries").asText())
        assertEquals("2024-01", tree.get("coverDate").asText())
        assertEquals("The Beginning", tree.get("storyArc").asText())
    }

    @Test
    fun `PUT comic-metadata stores characters list`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/comic-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"characters":["Spider-Man","Iron Man","Thor"]}"""),
        )

        val getResp = app(Request(Method.GET, "/api/books/$bookId/comic-metadata").header("Cookie", "token=$token"))
        val chars = Json.mapper.readTree(getResp.bodyString()).get("characters")
        assertEquals(3, chars.size())
        val names = (0 until chars.size()).map { chars[it].asText() }.toSet()
        assertTrue("Spider-Man" in names)
        assertTrue("Iron Man" in names)
        assertTrue("Thor" in names)
    }

    @Test
    fun `PUT comic-metadata stores teams list`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/comic-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"teams":["Avengers","X-Men"]}"""),
        )

        val getResp = app(Request(Method.GET, "/api/books/$bookId/comic-metadata").header("Cookie", "token=$token"))
        val teams = Json.mapper.readTree(getResp.bodyString()).get("teams")
        assertEquals(2, teams.size())
    }

    @Test
    fun `PUT comic-metadata replaces characters on subsequent update`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/comic-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"characters":["Old Character"]}"""),
        )
        app(
            Request(Method.PUT, "/api/books/$bookId/comic-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"characters":["New Character"]}"""),
        )

        val getResp = app(Request(Method.GET, "/api/books/$bookId/comic-metadata").header("Cookie", "token=$token"))
        val chars = Json.mapper.readTree(getResp.bodyString()).get("characters")
        assertEquals(1, chars.size())
        assertEquals("New Character", chars[0].asText())
    }

    @Test
    fun `PUT locations stores locations list`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.PUT, "/api/books/$bookId/comic-metadata")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"locations":["New York","Wakanda"]}"""),
        )
        assertEquals(Status.OK, resp.status)
        val locs = Json.mapper.readTree(resp.bodyString()).get("locations")
        assertEquals(2, locs.size())
    }

    @Test
    fun `comic-metadata endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/comic-metadata"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
