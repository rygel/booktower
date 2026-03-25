package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class ListeningStatsIntegrationTest : IntegrationTestBase() {
    private fun listenBody(
        startPos: Int,
        endPos: Int,
        totalSec: Int? = null,
    ): String =
        if (totalSec != null) {
            """{"startPosSec":$startPos,"endPosSec":$endPos,"totalSec":$totalSec}"""
        } else {
            """{"startPosSec":$startPos,"endPosSec":$endPos}"""
        }

    @Test
    fun `GET listening stats returns valid structure for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/stats/listening").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertNotNull(tree.get("totalSecondsListened"))
        assertNotNull(tree.get("totalBooksFinished"))
        assertNotNull(tree.get("totalSessions"))
        assertNotNull(tree.get("currentStreak"))
        assertNotNull(tree.get("longestStreak"))
        assertNotNull(tree.get("averageSecondsPerDay"))
        assertTrue(tree.get("heatmap")?.isArray == true)
        assertTrue(tree.get("secondsByTag")?.isObject == true)
        assertTrue(tree.get("secondsByCategory")?.isObject == true)
    }

    @Test
    fun `GET listening stats for new user has zero counts`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/stats/listening").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0L, tree.get("totalSecondsListened")?.asLong())
        assertEquals(0, tree.get("totalSessions")?.asInt())
        assertEquals(0, tree.get("currentStreak")?.asInt())
    }

    @Test
    fun `POST listen session records and stats reflect it`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val postResp =
            app(
                Request(Method.POST, "/api/books/$bookId/listen")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(listenBody(0, 3600, 7200)),
            )
        assertEquals(Status.NO_CONTENT, postResp.status)

        val stats =
            Json.mapper.readTree(
                app(Request(Method.GET, "/api/stats/listening").header("Cookie", "token=$token")).bodyString(),
            )
        assertTrue((stats.get("totalSecondsListened")?.asLong() ?: 0L) >= 3600L)
        assertTrue((stats.get("totalSessions")?.asInt() ?: 0) >= 1)
    }

    @Test
    fun `PUT listen-progress stores position and GET retrieves it`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val putResp =
            app(
                Request(Method.PUT, "/api/books/$bookId/listen-progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"positionSec":1800,"totalSec":7200}"""),
            )
        assertEquals(Status.NO_CONTENT, putResp.status)

        val getResp = app(Request(Method.GET, "/api/books/$bookId/listen-progress").header("Cookie", "token=$token"))
        assertEquals(Status.OK, getResp.status)
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertEquals(1800, tree.get("positionSec")?.asInt())
        assertEquals(7200, tree.get("totalSec")?.asInt())
    }

    @Test
    fun `GET listen-progress returns 404 when no progress exists`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/listen-progress").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `POST listen session updates progress to end position`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, "/api/books/$bookId/listen")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body(listenBody(0, 2700, 5400)),
        )

        val progress =
            Json.mapper.readTree(
                app(Request(Method.GET, "/api/books/$bookId/listen-progress").header("Cookie", "token=$token")).bodyString(),
            )
        assertEquals(2700, progress.get("positionSec")?.asInt())
        assertEquals(5400, progress.get("totalSec")?.asInt())
    }

    @Test
    fun `GET recent listen sessions returns sessions`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, "/api/books/$bookId/listen")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body(listenBody(0, 1800)),
        )

        val resp = app(Request(Method.GET, "/api/listen-sessions").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray)
        assertTrue(arr.size() >= 1)
        assertEquals(bookId, arr[0].get("bookId")?.asText())
        assertEquals(1800, arr[0].get("secondsListened")?.asInt())
    }

    @Test
    fun `stats are isolated per user`() {
        val token1 = registerAndGetToken("ls1")
        val token2 = registerAndGetToken("ls2")

        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)
        app(
            Request(Method.POST, "/api/books/$bookId/listen")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body(listenBody(0, 3600)),
        )

        val resp2 = app(Request(Method.GET, "/api/stats/listening").header("Cookie", "token=$token2"))
        val tree2 = Json.mapper.readTree(resp2.bodyString())
        assertEquals(0, tree2.get("totalSessions")?.asInt())
    }

    @Test
    fun `GET listening stats requires authentication`() {
        val resp = app(Request(Method.GET, "/api/stats/listening"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
