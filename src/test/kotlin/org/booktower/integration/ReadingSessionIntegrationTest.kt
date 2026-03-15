package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.ReadingSessionDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingSessionIntegrationTest : IntegrationTestBase() {

    @Test
    fun `updating progress records a reading session`() {
        val token = registerAndGetToken("session")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Advance from page 0 to page 50
        val response = app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=50&totalPages=300"),
        )
        assertEquals(Status.OK, response.status)

        // Sessions endpoint should list the session
        val sessionsResponse = app(
            Request(Method.GET, "/api/books/$bookId/sessions")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, sessionsResponse.status)
        val sessions = Json.mapper.readValue(sessionsResponse.bodyString(), Array<ReadingSessionDto>::class.java)
        assertEquals(1, sessions.size)
        assertEquals(0, sessions[0].startPage)
        assertEquals(50, sessions[0].endPage)
        assertEquals(50, sessions[0].pagesRead)
    }

    @Test
    fun `multiple progress updates produce multiple sessions`() {
        val token = registerAndGetToken("session")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=20&totalPages=200"))

        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=40&totalPages=200"))

        val sessions = Json.mapper.readValue(
            app(Request(Method.GET, "/api/books/$bookId/sessions")
                .header("Cookie", "token=$token")).bodyString(),
            Array<ReadingSessionDto>::class.java,
        )
        assertEquals(2, sessions.size)
        // Newest first
        assertEquals(40, sessions[0].endPage)
        assertEquals(20, sessions[1].endPage)
    }

    @Test
    fun `no progress change does not record a session`() {
        val token = registerAndGetToken("session")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Set page to 30
        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=30&totalPages=200"))

        // Set same page again (no delta)
        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=30&totalPages=200"))

        val sessions = Json.mapper.readValue(
            app(Request(Method.GET, "/api/books/$bookId/sessions")
                .header("Cookie", "token=$token")).bodyString(),
            Array<ReadingSessionDto>::class.java,
        )
        assertEquals(1, sessions.size)
    }

    @Test
    fun `sessions endpoint returns 400 for invalid book id`() {
        val token = registerAndGetToken("session")
        val response = app(
            Request(Method.GET, "/api/books/not-a-uuid/sessions")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `sessions are isolated per user`() {
        val tokenA = registerAndGetToken("session")
        val tokenB = registerAndGetToken("session")
        val libA = createLibrary(tokenA)
        val libB = createLibrary(tokenB)
        val bookA = createBook(tokenA, libA)
        val bookB = createBook(tokenB, libB)

        app(Request(Method.POST, "/ui/books/$bookA/progress")
            .header("Cookie", "token=$tokenA")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=10&totalPages=100"))

        app(Request(Method.POST, "/ui/books/$bookB/progress")
            .header("Cookie", "token=$tokenB")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=25&totalPages=100"))

        val sessionsA = Json.mapper.readValue(
            app(Request(Method.GET, "/api/books/$bookA/sessions")
                .header("Cookie", "token=$tokenA")).bodyString(),
            Array<ReadingSessionDto>::class.java,
        )
        val sessionsB = Json.mapper.readValue(
            app(Request(Method.GET, "/api/books/$bookB/sessions")
                .header("Cookie", "token=$tokenB")).bodyString(),
            Array<ReadingSessionDto>::class.java,
        )

        assertEquals(1, sessionsA.size)
        assertEquals(1, sessionsB.size)
        assertEquals(10, sessionsA[0].endPage)
        assertEquals(25, sessionsB[0].endPage)
    }

    @Test
    fun `analytics page loads with session data`() {
        val token = registerAndGetToken("session")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=15&totalPages=100"))

        val response = app(
            Request(Method.GET, "/analytics")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("BookTower"))
    }

    @Test
    fun `sessions endpoint returns 401 without auth`() {
        val token = registerAndGetToken("session")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/api/books/$bookId/sessions"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }
}
