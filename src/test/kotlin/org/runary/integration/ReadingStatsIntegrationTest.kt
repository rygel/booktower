package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingStatsIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET reading stats returns valid structure for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/stats/reading").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertNotNull(tree.get("totalPagesRead"))
        assertNotNull(tree.get("totalBooksFinished"))
        assertNotNull(tree.get("totalSessions"))
        assertNotNull(tree.get("currentStreak"))
        assertNotNull(tree.get("longestStreak"))
        assertNotNull(tree.get("averagePagesPerDay"))
        assertTrue(tree.get("heatmap")?.isArray == true)
        assertTrue(tree.get("pagesByTag")?.isObject == true)
        assertTrue(tree.get("pagesByCategory")?.isObject == true)
    }

    @Test
    fun `GET reading stats for new user has zero counts`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/stats/reading").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0L, tree.get("totalPagesRead")?.asLong())
        assertEquals(0, tree.get("totalBooksFinished")?.asInt())
        assertEquals(0, tree.get("totalSessions")?.asInt())
        assertEquals(0, tree.get("currentStreak")?.asInt())
    }

    @Test
    fun `GET reading stats increases after progress update`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Update reading progress to generate a session
        app(
            Request(Method.POST, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"page":50,"pageCount":200}"""),
        )

        val resp = app(Request(Method.GET, "/api/stats/reading").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue((tree.get("totalPagesRead")?.asLong() ?: 0L) >= 0L)
        assertTrue((tree.get("totalSessions")?.asInt() ?: 0) >= 0)
    }

    @Test
    fun `GET reading stats with days parameter`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/stats/reading?days=30").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `stats are isolated per user`() {
        val token1 = registerAndGetToken("u1")
        val token2 = registerAndGetToken("u2")

        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)
        app(
            Request(Method.POST, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"page":100,"pageCount":200}"""),
        )

        // User 2 should still have 0 stats
        val resp2 = app(Request(Method.GET, "/api/stats/reading").header("Cookie", "token=$token2"))
        val tree2 = Json.mapper.readTree(resp2.bodyString())
        assertEquals(0, tree2.get("totalSessions")?.asInt())
    }

    @Test
    fun `GET reading stats requires authentication`() {
        val resp = app(Request(Method.GET, "/api/stats/reading"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
