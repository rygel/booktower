package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibrarySortIntegrationTest : IntegrationTestBase() {
    @Test
    fun `library page renders with default title sort`() {
        val token = registerAndGetToken("sort1")
        val libId = createLibrary(token)
        createBook(token, libId, "Zebra Book")
        createBook(token, libId, "Apple Book")

        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertEquals(Status.OK, app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).status)
        // Title sort: Apple before Zebra
        val applePos = body.indexOf("Apple Book")
        val zebraPos = body.indexOf("Zebra Book")
        assertTrue(applePos < zebraPos, "Apple should appear before Zebra with title sort")
    }

    @Test
    fun `library page sort=ADDED renders most recently added first`() {
        val token = registerAndGetToken("sort2")
        val libId = createLibrary(token)
        createBook(token, libId, "First Added")
        Thread.sleep(10)
        createBook(token, libId, "Second Added")

        val body =
            app(
                Request(Method.GET, "/libraries/$libId?sort=ADDED")
                    .header("Cookie", "token=$token"),
            ).bodyString()
        val firstPos = body.indexOf("First Added")
        val secondPos = body.indexOf("Second Added")
        // ADDED sort is DESC, so Second Added should appear before First Added
        assertTrue(secondPos < firstPos, "Most recently added book should appear first")
    }

    @Test
    fun `library page sort=AUTHOR sorts by author name`() {
        val token = registerAndGetToken("sort3")
        val libId = createLibrary(token)
        // Create via API to set authors
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Book By Zara","author":"Zara","description":null,"libraryId":"$libId"}"""),
        )
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Book By Alex","author":"Alex","description":null,"libraryId":"$libId"}"""),
        )

        val body =
            app(
                Request(Method.GET, "/libraries/$libId?sort=AUTHOR")
                    .header("Cookie", "token=$token"),
            ).bodyString()
        val alexPos = body.indexOf("Book By Alex")
        val zaraPos = body.indexOf("Book By Zara")
        assertTrue(alexPos < zaraPos, "Alex should appear before Zara in author sort")
    }

    @Test
    fun `sort dropdown is rendered in library page`() {
        val token = registerAndGetToken("sort4")
        val libId = createLibrary(token)
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("value=\"TITLE\""), "Sort dropdown should render TITLE option")
        assertTrue(body.contains("value=\"ADDED\""), "Sort dropdown should render ADDED option")
        assertTrue(body.contains("value=\"AUTHOR\""), "Sort dropdown should render AUTHOR option")
    }

    @Test
    fun `invalid sort param falls back to title sort`() {
        val token = registerAndGetToken("sort5")
        val libId = createLibrary(token)
        createBook(token, libId, "Only Book")

        val response =
            app(
                Request(Method.GET, "/libraries/$libId?sort=INVALID")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Only Book"))
    }

    @Test
    fun `sort param is case insensitive`() {
        val token = registerAndGetToken("sort6")
        val libId = createLibrary(token)
        createBook(token, libId, "Case Test Book")

        val response =
            app(
                Request(Method.GET, "/libraries/$libId?sort=added")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Case Test Book"))
    }

    @Test
    fun `sort selected option reflects current sort`() {
        val token = registerAndGetToken("sort7")
        val libId = createLibrary(token)

        val body =
            app(
                Request(Method.GET, "/libraries/$libId?sort=AUTHOR")
                    .header("Cookie", "token=$token"),
            ).bodyString()
        // The AUTHOR option should have selected attribute nearby
        val authorOptionIdx = body.indexOf("value=\"AUTHOR\"")
        assertTrue(authorOptionIdx >= 0, "AUTHOR option must be present")
        val snippet = body.substring(authorOptionIdx, minOf(authorOptionIdx + 40, body.length))
        assertTrue(snippet.contains("selected"), "AUTHOR option should be marked selected: $snippet")
    }
}
