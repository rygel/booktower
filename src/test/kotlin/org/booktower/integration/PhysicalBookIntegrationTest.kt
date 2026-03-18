package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicalBookIntegrationTest : IntegrationTestBase() {
    @Test
    fun `create physical book returns bookFormat=PHYSICAL`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(
                        """{"title":"Physical Book","author":"Author","description":null,"libraryId":"$libId","bookFormat":"PHYSICAL"}""",
                    ),
            )
        assertEquals(Status.CREATED, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals("PHYSICAL", body.get("bookFormat").asText())
    }

    @Test
    fun `create ebook book returns bookFormat=EBOOK by default`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Digital Book","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals("EBOOK", body.get("bookFormat").asText())
    }

    @Test
    fun `create audiobook returns bookFormat=AUDIOBOOK`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Audiobook Title","author":null,"description":null,"libraryId":"$libId","bookFormat":"AUDIOBOOK"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        assertEquals(
            "AUDIOBOOK",
            Json.mapper
                .readTree(resp.bodyString())
                .get("bookFormat")
                .asText(),
        )
    }

    @Test
    fun `filter books by format=PHYSICAL returns only physical books`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ebook","author":null,"description":null,"libraryId":"$libId","bookFormat":"EBOOK"}"""),
        )
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Physical","author":null,"description":null,"libraryId":"$libId","bookFormat":"PHYSICAL"}"""),
        )

        val resp =
            app(
                Request(Method.GET, "/api/books?libraryId=$libId&format=PHYSICAL")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val books = Json.mapper.readTree(resp.bodyString())
        val bookList = books.get("books") ?: books
        assertTrue(bookList.all { it.get("bookFormat").asText() == "PHYSICAL" })
        assertEquals(1, bookList.size())
    }

    @Test
    fun `filter books by format=EBOOK excludes physical books`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ebook","author":null,"description":null,"libraryId":"$libId","bookFormat":"EBOOK"}"""),
        )
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Physical","author":null,"description":null,"libraryId":"$libId","bookFormat":"PHYSICAL"}"""),
        )

        val resp =
            app(
                Request(Method.GET, "/api/books?libraryId=$libId&format=EBOOK")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val bookList = Json.mapper.readTree(resp.bodyString()).let { it.get("books") ?: it }
        assertEquals(1, bookList.size())
        assertEquals("EBOOK", bookList[0].get("bookFormat").asText())
    }

    @Test
    fun `get book returns bookFormat field`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val createResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"My Physical Book","author":null,"description":null,"libraryId":"$libId","bookFormat":"PHYSICAL"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val getResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, getResp.status)
        assertEquals(
            "PHYSICAL",
            Json.mapper
                .readTree(getResp.bodyString())
                .get("bookFormat")
                .asText(),
        )
    }

    @Test
    fun `no format filter returns all formats`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ebook","author":null,"description":null,"libraryId":"$libId","bookFormat":"EBOOK"}"""),
        )
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Physical","author":null,"description":null,"libraryId":"$libId","bookFormat":"PHYSICAL"}"""),
        )
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Audio","author":null,"description":null,"libraryId":"$libId","bookFormat":"AUDIOBOOK"}"""),
        )

        val resp = app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val bookList = Json.mapper.readTree(resp.bodyString()).let { it.get("books") ?: it }
        assertEquals(3, bookList.size())
    }
}
