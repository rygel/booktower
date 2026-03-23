package org.runary.integration

import org.runary.config.Json
import org.runary.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageIntegrationTest : IntegrationTestBase() {
    @Test
    fun `index page returns HTML`() {
        val response = app(Request(Method.GET, "/"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.startsWith("text/html") == true)
        assertTrue(response.bodyString().contains("<html"))
    }

    @Test
    fun `index page without auth shows login and register links`() {
        val response = app(Request(Method.GET, "/"))
        assertTrue(response.bodyString().contains("Sign in") || response.bodyString().contains("Login"))
        assertTrue(response.bodyString().contains("Sign up") || response.bodyString().contains("Sign Up"))
        assertFalse(response.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `index page with auth shows authenticated view`() {
        val username = "pageuser_${System.nanoTime()}"
        val regResponse =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        val token = Json.mapper.readValue(regResponse.bodyString(), LoginResponse::class.java).token

        val response = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `index page with invalid token shows unauthenticated view`() {
        val response = app(Request(Method.GET, "/").header("Cookie", "token=bogus"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Sign in") || response.bodyString().contains("Login"))
        assertFalse(response.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `login page returns HTML`() {
        val response = app(Request(Method.GET, "/login"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Login"))
    }

    @Test
    fun `register page returns HTML`() {
        val response = app(Request(Method.GET, "/register"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Register"))
    }

    @Test
    fun `authenticated index page shows user libraries`() {
        val username = "libpage_${System.nanoTime()}"
        val regResponse =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        val token = Json.mapper.readValue(regResponse.bodyString(), LoginResponse::class.java).token

        app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Page Test Library","path":"./data/test-page-${System.nanoTime()}"}"""),
        )

        val response = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertTrue(response.bodyString().contains("Page Test Library"))
    }

    @Test
    fun `health endpoint returns 200 with status ok`() {
        val response = app(Request(Method.GET, "/health"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("application/json") == true)
        assertTrue(response.bodyString().contains("ok"))
    }

    @Test
    fun `library page sort by PUBLISHED_DATE returns 200 and shows sort option`() {
        val token = registerAndGetToken("sortpd")
        val libId = createLibrary(token)

        val response =
            app(
                Request(Method.GET, "/libraries/$libId?sort=PUBLISHED_DATE")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        // The sort dropdown must include the PUBLISHED_DATE option
        assertTrue(
            response.bodyString().contains("PUBLISHED_DATE"),
            "Library page must render the PUBLISHED_DATE sort option in the dropdown",
        )
    }

    @Test
    fun `library page sort by PUBLISHED_DATE orders books by published date descending`() {
        val token = registerAndGetToken("sortpd2")
        val libId = createLibrary(token)

        // Create two books then set their published dates via the edit endpoint
        val book1 = createBook(token, libId, "Older Book")
        val book2 = createBook(token, libId, "Newer Book")

        fun editPublishedDate(
            bookId: String,
            date: String,
        ) {
            app(
                Request(Method.POST, "/ui/books/$bookId/meta")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("title=${if (bookId == book1) "Older+Book" else "Newer+Book"}&publishedDate=$date"),
            )
        }
        editPublishedDate(book1, "1990-01-01")
        editPublishedDate(book2, "2020-06-15")

        val html =
            app(
                Request(Method.GET, "/libraries/$libId?sort=PUBLISHED_DATE")
                    .header("Cookie", "token=$token"),
            ).bodyString()

        val idxOlder = html.indexOf("Older Book")
        val idxNewer = html.indexOf("Newer Book")
        assertTrue(
            idxNewer in 0..Int.MAX_VALUE && idxOlder in 0..Int.MAX_VALUE,
            "Both books must appear on the library page",
        )
        assertTrue(
            idxNewer < idxOlder,
            "Newer Book (2020) must appear before Older Book (1990) when sorted by PUBLISHED_DATE DESC",
        )
    }
}
