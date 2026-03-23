package org.runary.integration

import org.runary.config.Json
import org.runary.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the HTMX UI layer:
 * - GET page routes (/libraries, /libraries/{id}, /books/{id}, /search)
 * - POST/DELETE HTMX mutation endpoints (/ui/...)
 * - Login form accepting email address
 */
class UiIntegrationTest : IntegrationTestBase() {
    // ── Auth: login by email ────────────────────────────────────────────────

    @Test
    fun `login with email via JSON API returns token`() {
        val username = "emailusr_${System.nanoTime()}"
        val knownEmail = "$username@test.com"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$knownEmail","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$knownEmail","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.OK, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
    }

    @Test
    fun `login with email via form redirects with cookie`() {
        val username = "emailfrm_${System.nanoTime()}"
        val email = "$username@test.com"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$email","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("username=${email.replace("@", "%40")}&password=${org.runary.TestPasswords.DEFAULT}"),
            )
        assertEquals(Status.SEE_OTHER, response.status)
        assertNotNull(response.cookies().find { it.name == "token" && it.value.isNotBlank() })
    }

    // ── Page routes: auth required ──────────────────────────────────────────

    @Test
    fun `GET libraries without auth redirects to login`() {
        val response = app(Request(Method.GET, "/libraries"))
        assertEquals(Status.SEE_OTHER, response.status)
        assertEquals("/login", response.header("Location"))
    }

    @Test
    fun `GET libraries with auth returns HTML page`() {
        val token = registerAndGetToken("lib")
        val response = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("text/html") == true)
        assertTrue(response.bodyString().contains("My Libraries"))
    }

    @Test
    fun `GET library detail without auth redirects to login`() {
        val response = app(Request(Method.GET, "/libraries/00000000-0000-0000-0000-000000000000"))
        assertEquals(Status.SEE_OTHER, response.status)
    }

    @Test
    fun `GET library detail for own library returns HTML`() {
        val token = registerAndGetToken("libdet")
        val libId = createLibrary(token)

        val response = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Add Book"))
    }

    @Test
    fun `GET library detail for unknown id returns 404`() {
        val token = registerAndGetToken("lib404")
        val response =
            app(
                Request(Method.GET, "/libraries/00000000-0000-0000-0000-000000000000")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `GET book detail without auth redirects to login`() {
        val response = app(Request(Method.GET, "/books/00000000-0000-0000-0000-000000000000"))
        assertEquals(Status.SEE_OTHER, response.status)
    }

    @Test
    fun `GET book detail for own book returns HTML`() {
        val token = registerAndGetToken("bkdet")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("Upload File"))
        assertTrue(body.contains("Reading Progress"))
        assertTrue(body.contains("Bookmarks"))
    }

    @Test
    fun `GET book detail for unknown id returns 404`() {
        val token = registerAndGetToken("bk404")
        val response =
            app(
                Request(Method.GET, "/books/00000000-0000-0000-0000-000000000000")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `GET search without auth redirects to login`() {
        val response = app(Request(Method.GET, "/search"))
        assertEquals(Status.SEE_OTHER, response.status)
    }

    @Test
    fun `GET search with auth returns HTML page`() {
        val token = registerAndGetToken("srchui")
        val response = app(Request(Method.GET, "/search").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Search"))
    }

    @Test
    fun `GET search with query returns results`() {
        val token = registerAndGetToken("srchq")
        val libId = createLibrary(token)
        createBook(token, libId, "Unique Quantum Title")

        val response =
            app(
                Request(Method.GET, "/search?q=Quantum")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Unique Quantum Title"))
    }

    // ── HTMX mutations ──────────────────────────────────────────────────────

    @Test
    fun `POST ui-libraries creates library and returns HTML card`() {
        val token = registerAndGetToken("uicreatelib")
        val response =
            app(
                Request(Method.POST, "/ui/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=My+UI+Library&path=./data/test-ui-${System.nanoTime()}"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("My UI Library"))
        assertTrue(body.contains("hx-delete="))
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)
    }

    @Test
    fun `POST ui-libraries without auth returns 401`() {
        val response =
            app(
                Request(Method.POST, "/ui/libraries")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=Hack&path=./data/hack"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `DELETE ui-libraries removes library`() {
        val token = registerAndGetToken("uideletelib")
        val libId = createLibrary(token)

        val response =
            app(
                Request(Method.DELETE, "/ui/libraries/$libId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)

        val list = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token"))
        assertFalse(list.bodyString().contains(libId))
    }

    @Test
    fun `POST ui-libraries-books creates book and returns HTML card`() {
        val token = registerAndGetToken("uicreatebk")
        val libId = createLibrary(token)

        val response =
            app(
                Request(Method.POST, "/ui/libraries/$libId/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("title=UI+Test+Book&author=Test+Author"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("UI Test Book"))
        assertTrue(body.contains("hx-delete="))
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)
    }

    @Test
    fun `DELETE ui-books removes book`() {
        val token = registerAndGetToken("uideletebk")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.DELETE, "/ui/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)
    }

    @Test
    fun `POST ui-books-progress updates reading progress`() {
        val token = registerAndGetToken("uiprog")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/ui/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("currentPage=42"),
            )
        assertEquals(Status.OK, response.status)

        val bookResponse =
            app(
                Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"),
            )
        assertTrue(bookResponse.bodyString().contains("42"))
    }

    @Test
    fun `POST ui-books-bookmarks creates bookmark and returns HTML fragment`() {
        val token = registerAndGetToken("uibm")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/ui/books/$bookId/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("page=7&title=Chapter+One&note=Important+section"),
            )
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("7"))
        assertTrue(body.contains("Chapter One"))
        assertTrue(body.contains("hx-delete="))
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)
    }

    @Test
    fun `DELETE ui-bookmarks removes bookmark`() {
        val token = registerAndGetToken("uibmdel")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bmResponse =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":3,"title":"To delete","note":null}"""),
            )
        val bmId =
            Json.mapper
                .readTree(bmResponse.bodyString())
                .get("id")
                .asText()

        val response =
            app(
                Request(Method.DELETE, "/ui/bookmarks/$bmId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)
    }

    @Test
    fun `book detail page shows cover when available`() {
        val token = registerAndGetToken("bkcover")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Book")

        val response = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        // No cover yet -- styled placeholder should appear
        assertTrue(response.bodyString().contains("ri-book-3-fill"))
    }

    @Test
    fun `library page shows books belonging to that library`() {
        val token = registerAndGetToken("libbooks")
        val libId = createLibrary(token)
        createBook(token, libId, "Library Page Book")

        val response = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Library Page Book"))
    }

    @Test
    fun `user B cannot view user A book page`() {
        val tokenA = registerAndGetToken("uibkA")
        val tokenB = registerAndGetToken("uibkB")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)

        val response = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$tokenB"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    // ── Persistent header search bar ────────────────────────────────────────

    @Test
    fun `header search bar is present on libraries page`() {
        val token = registerAndGetToken("srchbar1")
        val body = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""action="/search""""))
        assertTrue(body.contains("""name="q""""))
    }

    @Test
    fun `header search bar is present on library detail page`() {
        val token = registerAndGetToken("srchbar2")
        val libId = createLibrary(token)
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""action="/search""""))
    }

    @Test
    fun `header search bar is present on book detail page`() {
        val token = registerAndGetToken("srchbar3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""action="/search""""))
    }

    @Test
    fun `search page pre-populates header search bar with current query`() {
        val token = registerAndGetToken("srchbar4")
        val body =
            app(
                Request(Method.GET, "/search?q=tolkien").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("""value="tolkien""""))
    }

    @Test
    fun `header search bar is present on search page itself`() {
        val token = registerAndGetToken("srchbar5")
        val body = app(Request(Method.GET, "/search").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""action="/search""""))
        assertTrue(body.contains("""name="q""""))
    }

    @Test
    fun `header search bar uses GET method`() {
        val token = registerAndGetToken("srchbar6")
        val body = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""method="GET""""))
    }

    @Test
    fun `header search bar uses type search`() {
        val token = registerAndGetToken("srchbar7")
        val body = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""type="search""""))
    }

    @Test
    fun `header search bar value is empty on non-search pages`() {
        val token = registerAndGetToken("srchbar8")
        val body = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""value="""""))
        assertFalse(body.contains("""value="t"""")) // no stale query leaking in
    }

    @Test
    fun `header search bar is absent on login page`() {
        val body = app(Request(Method.GET, "/login")).bodyString()
        assertFalse(body.contains("""name="q""""))
    }

    @Test
    fun `header search bar is absent on register page`() {
        val body = app(Request(Method.GET, "/register")).bodyString()
        assertFalse(body.contains("""name="q""""))
    }

    @Test
    fun `header search bar is absent on unauthenticated home page`() {
        val body = app(Request(Method.GET, "/")).bodyString()
        assertFalse(body.contains("""name="q""""))
    }

    @Test
    fun `reader page does not have header search bar`() {
        val token = registerAndGetToken("srchbar9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("""action="/search""""))
    }

    @Test
    fun `multi-word query is pre-populated in search bar`() {
        val token = registerAndGetToken("srchbar10")
        val body =
            app(
                Request(Method.GET, "/search?q=lord+of+the+rings").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("lord of the rings"))
    }

    @Test
    fun `HTML special chars in query are escaped in search bar value`() {
        val token = registerAndGetToken("srchbar11")
        val body =
            app(
                Request(Method.GET, "/search?q=%3Cscript%3E").header("Cookie", "token=$token"),
            ).bodyString()
        // The value attribute must not contain the raw unescaped tag
        assertFalse(body.contains("""value="<script>""""), "Raw <script> tag must not appear unescaped in value attribute")
        // JTE should have emitted the escaped form
        assertTrue(body.contains("&lt;script&gt;"), "Query should appear HTML-escaped in the search input value")
    }

    @Test
    fun `search bar end-to-end finds a book matching the query`() {
        val token = registerAndGetToken("srchbar12")
        val libId = createLibrary(token)
        createBook(token, libId, "The Hobbit")

        val body =
            app(
                Request(Method.GET, "/search?q=Hobbit").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("The Hobbit"))
        assertTrue(body.contains("""value="Hobbit""""))
    }

    // ── Reader page ─────────────────────────────────────────────────────────

    @Test
    fun `GET books-id-read returns HTML reader page`() {
        val token = registerAndGetToken("reader1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My PDF Book")

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("text/html") == true)
        val body = response.bodyString()
        assertTrue(body.contains("My PDF Book"))
    }

    @Test
    fun `reader page without auth redirects to login`() {
        val token = registerAndGetToken("reader2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read"))
        assertEquals(Status.SEE_OTHER, response.status)
        assertEquals("/login", response.header("Location"))
    }

    @Test
    fun `reader page shows no-file message when book has no uploaded file`() {
        val token = registerAndGetToken("reader3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Unuploaded Book")

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        // No PDF.js script loaded when no file
        assertFalse(body.contains("pdf.min.js"))
    }

    @Test
    fun `reader page for non-existent book returns 404`() {
        val token = registerAndGetToken("reader4")
        val fakeId = "00000000-0000-0000-0000-000000000000"
        val response = app(Request(Method.GET, "/books/$fakeId/read").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `reader page contains PDF-js toolbar controls`() {
        val token = registerAndGetToken("reader5")
        val libId = createLibrary(token)
        // Simulate a book with a file by checking via fileSize — we can't upload in unit tests,
        // so we verify the toolbar is always rendered (PDF.js only loads when fileSize > 0)
        val bookId = createBook(token, libId, "Reader Toolbar Test")

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        val body = response.bodyString()
        // Toolbar elements always present
        assertTrue(body.contains("btn-prev"))
        assertTrue(body.contains("btn-next"))
        assertTrue(body.contains("btn-bookmarks"))
        assertTrue(body.contains("btn-zoom-in"))
    }

    @Test
    fun `reader page includes back link to book detail`() {
        val token = registerAndGetToken("reader6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Back Link Book")

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertTrue(response.bodyString().contains("/books/$bookId"))
    }

    @Test
    fun `reader page reflects current theme`() {
        val token = registerAndGetToken("reader7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.GET, "/books/$bookId/read")
                    .header("Cookie", "token=$token; app_theme=dracula"),
            )
        assertTrue(response.bodyString().contains("data-theme=\"dracula\""))
    }

    @Test
    fun `reader page shows bookmark list when bookmarks exist`() {
        val token = registerAndGetToken("reader8")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Bookmarked Book")
        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":5,"title":"Chapter 1","note":null}"""),
        )

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        val body = response.bodyString()
        assertTrue(body.contains("Chapter 1"))
        assertTrue(body.contains("jumpToPage(5)"))
    }
}
