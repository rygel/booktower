package org.booktower.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.booktower.config.Json
import org.booktower.models.LibraryDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpdsIntegrationTest : IntegrationTestBase() {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun basicAuth(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    /** Registers a user and returns (token, username, password). */
    private fun registerUser(prefix: String): Triple<String, String, String> {
        val username = "${prefix}_${System.nanoTime()}"
        val password = "password123"
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"$password"}"""),
        )
        val token = Json.mapper.readTree(response.bodyString()).get("token").asText()
        return Triple(token, username, password)
    }

    private fun minimalPdf(): ByteArray {
        val doc = PDDocument().also { it.addPage(PDPage()) }
        return ByteArrayOutputStream().also { doc.save(it); doc.close() }.toByteArray()
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    fun `catalog without auth returns 401 with WWW-Authenticate header`() {
        val resp = app(Request(Method.GET, "/opds/catalog"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
        assertTrue(resp.header("WWW-Authenticate")?.contains("Basic") == true)
        assertTrue(resp.header("WWW-Authenticate")?.contains("BookTower OPDS") == true)
    }

    @Test
    fun `catalog with wrong password returns 401`() {
        val (_, username, _) = registerUser("opds_auth1")
        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, "wrongpassword")),
        )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `library feed without auth returns 401`() {
        val resp = app(Request(Method.GET, "/opds/catalog/00000000-0000-0000-0000-000000000000"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `file download without auth returns 401`() {
        val resp = app(Request(Method.GET, "/opds/books/00000000-0000-0000-0000-000000000000/file"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `catalog with email credential also authenticates`() {
        val username = "opds_email_${System.nanoTime()}"
        val email = "$username@test.com"
        val password = "password123"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$email","password":"$password"}"""),
        )
        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(email, password)),
        )
        assertEquals(Status.OK, resp.status)
    }

    // ── Root catalog feed ─────────────────────────────────────────────────────

    @Test
    fun `catalog returns 200 with opds content-type`() {
        val (token, username, password) = registerUser("opds_ct")
        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("application/atom+xml") == true)
        assertTrue(resp.header("Content-Type")?.contains("opds-catalog") == true)
    }

    @Test
    fun `catalog returns valid atom xml with feed root element`() {
        val (_, username, password) = registerUser("opds_xml")
        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("<feed"), "Should contain Atom <feed> element")
        assertTrue(body.contains("xmlns=\"http://www.w3.org/2005/Atom\""), "Should have Atom namespace")
        assertTrue(body.contains("opds-catalog"), "Should reference opds-catalog")
    }

    @Test
    fun `catalog lists user libraries as entries`() {
        val (token, username, password) = registerUser("opds_libs")
        createLibrary(token, "SciFi Shelf")
        createLibrary(token, "Fantasy Shelf")

        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("SciFi Shelf"), "Should contain first library name")
        assertTrue(body.contains("Fantasy Shelf"), "Should contain second library name")
    }

    @Test
    fun `catalog entries contain subsection links to library feeds`() {
        val (token, username, password) = registerUser("opds_links")
        val libId = createLibrary(token, "My Shelf")

        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("/opds/catalog/$libId"), "Entry should link to library feed")
        assertTrue(body.contains("subsection"), "Link should have rel=subsection")
    }

    @Test
    fun `catalog does not expose another user's libraries`() {
        val (token1, u1, p1) = registerUser("opds_iso1")
        val (token2, u2, p2) = registerUser("opds_iso2")
        createLibrary(token1, "User1 Library")
        createLibrary(token2, "User2 Library")

        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(u1, p1)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("User1 Library"), "Should see own library")
        assertFalse(body.contains("User2 Library"), "Should not see other user's library")
    }

    @Test
    fun `catalog with no libraries returns empty feed`() {
        val (_, username, password) = registerUser("opds_empty")
        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.OK, resp.status)
        // Valid feed, just no <entry> elements
        assertTrue(resp.bodyString().contains("<feed"))
    }

    // ── Library acquisition feed ──────────────────────────────────────────────

    @Test
    fun `library feed returns 200 with acquisition content-type`() {
        val (token, username, password) = registerUser("opds_lib1")
        val libId = createLibrary(token)

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("acquisition") == true)
    }

    @Test
    fun `library feed lists books as entries`() {
        val (token, username, password) = registerUser("opds_bk1")
        val libId = createLibrary(token)
        createBook(token, libId, "The Hitchhiker's Guide")
        createBook(token, libId, "Dune")

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("The Hitchhiker&#39;s Guide") || body.contains("Hitchhiker"), "Should contain first book")
        assertTrue(body.contains("Dune"), "Should contain second book")
    }

    @Test
    fun `library feed book entries contain acquisition download links`() {
        val (token, username, password) = registerUser("opds_dl_link")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Linked Book")

        // Upload a file so the book has fileSize > 0
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "test.pdf")
                .body(ByteArrayInputStream(minimalPdf())),
        )

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("/opds/books/$bookId/file"), "Entry should have acquisition link")
        assertTrue(body.contains("http://opds-spec.org/acquisition"), "Should have correct rel")
    }

    @Test
    fun `library feed contains up-link to catalog`() {
        val (token, username, password) = registerUser("opds_up")
        val libId = createLibrary(token)

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("rel=\"up\""), "Should have up link")
        assertTrue(body.contains("/opds/catalog"), "Up link should point to catalog")
    }

    @Test
    fun `library feed returns 404 for nonexistent library`() {
        val (_, username, password) = registerUser("opds_404")
        val resp = app(
            Request(Method.GET, "/opds/catalog/00000000-0000-0000-0000-000000000000")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `library feed returns 404 for another user's library`() {
        val (token1, u1, p1) = registerUser("opds_xlib1")
        val (_, u2, p2) = registerUser("opds_xlib2")
        val libId = createLibrary(token1)

        // User 2 tries to fetch user 1's library feed
        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(u2, p2)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `library feed returns 404 for malformed library id`() {
        val (_, username, password) = registerUser("opds_malformed")
        val resp = app(
            Request(Method.GET, "/opds/catalog/not-a-uuid")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    // ── File download ─────────────────────────────────────────────────────────

    @Test
    fun `file download returns the uploaded file`() {
        val (token, username, password) = registerUser("opds_file1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val pdfBytes = minimalPdf()

        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "mybook.pdf")
                .body(ByteArrayInputStream(pdfBytes)),
        )

        val resp = app(
            Request(Method.GET, "/opds/books/$bookId/file")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("pdf") == true)
        assertTrue(resp.bodyString().isNotEmpty())
    }

    @Test
    fun `file download returns 404 when book has no file`() {
        val (token, username, password) = registerUser("opds_nofile")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.GET, "/opds/books/$bookId/file")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `file download returns 404 for nonexistent book`() {
        val (_, username, password) = registerUser("opds_fakebook")
        val resp = app(
            Request(Method.GET, "/opds/books/00000000-0000-0000-0000-000000000000/file")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `file download returns 404 when accessing another user's book`() {
        val (token1, u1, p1) = registerUser("opds_xfile1")
        val (_, u2, p2) = registerUser("opds_xfile2")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "secret.pdf")
                .body(ByteArrayInputStream(minimalPdf())),
        )

        // User 2 tries to download user 1's file
        val resp = app(
            Request(Method.GET, "/opds/books/$bookId/file")
                .header("Authorization", basicAuth(u2, p2)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    // ── XML correctness ───────────────────────────────────────────────────────

    @Test
    fun `special characters in library name are xml-escaped`() {
        val (token, username, password) = registerUser("opds_escape")
        createLibrary(token, "Books & More <Sci-Fi>")

        val resp = app(
            Request(Method.GET, "/opds/catalog")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("&amp;"), "& should be escaped as &amp;")
        assertTrue(body.contains("&lt;"), "< should be escaped as &lt;")
        assertFalse(body.contains("Books & More"), "Raw & should not appear")
    }

    @Test
    fun `special characters in book title are xml-escaped`() {
        val (token, username, password) = registerUser("opds_esc2")
        val libId = createLibrary(token)
        createBook(token, libId, "Alice & Bob's <Adventure>")

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("&amp;") || body.contains("&lt;"), "Special chars should be escaped")
    }
}
