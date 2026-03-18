package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookNotebookIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET notebooks returns empty list for new book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/notebooks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }

    @Test
    fun `POST creates a notebook`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/notebooks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Research Notes","content":"Chapter 1 is really interesting..."}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Research Notes", tree.get("title").asText())
        assertEquals("Chapter 1 is really interesting...", tree.get("content").asText())
    }

    @Test
    fun `GET specific notebook returns it`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/notebooks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"My Notes","content":"some notes"}"""),
            )
        val notebookId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val getResp = app(Request(Method.GET, "/api/books/$bookId/notebooks/$notebookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, getResp.status)
        assertEquals(
            "My Notes",
            Json.mapper
                .readTree(getResp.bodyString())
                .get("title")
                .asText(),
        )
    }

    @Test
    fun `PUT updates a notebook`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/notebooks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Initial","content":"initial content"}"""),
            )
        val notebookId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val putResp =
            app(
                Request(Method.PUT, "/api/books/$bookId/notebooks/$notebookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Updated Title","content":"updated content"}"""),
            )
        assertEquals(Status.OK, putResp.status)
        val tree = Json.mapper.readTree(putResp.bodyString())
        assertEquals("Updated Title", tree.get("title").asText())
        assertEquals("updated content", tree.get("content").asText())
    }

    @Test
    fun `DELETE removes notebook`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/notebooks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"To Delete","content":""}"""),
            )
        val notebookId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val delResp = app(Request(Method.DELETE, "/api/books/$bookId/notebooks/$notebookId").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val getResp = app(Request(Method.GET, "/api/books/$bookId/notebooks/$notebookId").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, getResp.status)
    }

    @Test
    fun `users only see their own notebooks`() {
        val token1 = registerAndGetToken("user1")
        val token2 = registerAndGetToken("user2")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)

        app(
            Request(Method.POST, "/api/books/$bookId/notebooks")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"title":"User1 Notes","content":""}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId/notebooks").header("Cookie", "token=$token2"))
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }

    @Test
    fun `POST with blank title returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/notebooks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"","content":"something"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `notebooks endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/notebooks"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
