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

    // ── Audiobook (chapter-only) books ────────────────────────────────────────

    private fun minimalMp3() =
        byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)

    private fun uploadChapter(token: String, bookId: String, trackIndex: Int) {
        val mp3 = minimalMp3()
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-$trackIndex.mp3")
                .header("X-Track-Index", trackIndex.toString())
                .body(mp3.inputStream(), mp3.size.toLong()),
        )
    }

    @Test
    fun `chapter-only audiobook has no single-file acquisition link`() {
        val (token, username, password) = registerUser("opds_audio1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Spoken Word Book")

        // Upload a chapter — no single file path set
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "chapter-0.mp3")
                .header("X-Track-Index", "0")
                .body(mp3.inputStream(), mp3.size.toLong()),
        )

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertFalse(
            body.contains("/opds/books/$bookId/file"),
            "Chapter-only book must not emit single-file acquisition link",
        )
    }

    @Test
    fun `audiobook chapters appear as per-chapter acquisition links in library feed`() {
        val (token, username, password) = registerUser("opds_chs1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audiobook with Chapters")
        uploadChapter(token, bookId, 0)
        uploadChapter(token, bookId, 1)

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("/opds/books/$bookId/chapters/0"), "Should have chapter 0 acquisition link")
        assertTrue(body.contains("/opds/books/$bookId/chapters/1"), "Should have chapter 1 acquisition link")
        assertTrue(body.contains("audio/mpeg"), "Chapter links should use audio/mpeg type")
    }

    @Test
    fun `opds chapter stream returns 200 with audio content type`() {
        val (token, username, password) = registerUser("opds_stream1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val resp = app(
            Request(Method.GET, "/opds/books/$bookId/chapters/0")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("audio/") == true)
        assertTrue(resp.header("Accept-Ranges") == "bytes")
    }

    @Test
    fun `opds chapter stream returns 404 for nonexistent chapter`() {
        val (token, username, password) = registerUser("opds_stream404")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.GET, "/opds/books/$bookId/chapters/99")
                .header("Authorization", basicAuth(username, password)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `opds chapter stream without auth returns 401`() {
        val resp = app(Request(Method.GET, "/opds/books/00000000-0000-0000-0000-000000000000/chapters/0"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `user B cannot stream user A chapter via opds`() {
        val (tokenA, uA, pA) = registerUser("opds_xch1")
        val (_, uB, pB) = registerUser("opds_xch2")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)
        uploadChapter(tokenA, bookId, 0)

        val resp = app(
            Request(Method.GET, "/opds/books/$bookId/chapters/0")
                .header("Authorization", basicAuth(uB, pB)),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `m4b chapter acquisition link uses audio-mp4 mime type`() {
        val (token, username, password) = registerUser("opds_m4b")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "M4B Audiobook")
        // Upload a chapter as m4b format
        val fakeBytes = ByteArray(64) { it.toByte() }
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-0.m4b")
                .header("X-Track-Index", "0")
                .body(fakeBytes.inputStream(), fakeBytes.size.toLong()),
        )

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("audio/mp4"), "M4B chapter link should use audio/mp4 MIME type")
        assertFalse(body.contains("audio/mpeg"), "M4B chapter must not use audio/mpeg MIME type")
    }

    @Test
    fun `ogg chapter acquisition link uses audio-ogg mime type`() {
        val (token, username, password) = registerUser("opds_ogg")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "OGG Audiobook")
        val fakeBytes = ByteArray(64) { it.toByte() }
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-0.ogg")
                .header("X-Track-Index", "0")
                .body(fakeBytes.inputStream(), fakeBytes.size.toLong()),
        )

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        val body = resp.bodyString()
        assertTrue(body.contains("audio/ogg"), "OGG chapter link should use audio/ogg MIME type")
    }

    @Test
    fun `book with single file still emits acquisition link after chapter-only fix`() {
        val (token, username, password) = registerUser("opds_singlefile")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "PDF Book")
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "book.pdf")
                .body(ByteArrayInputStream(minimalPdf())),
        )

        val resp = app(
            Request(Method.GET, "/opds/catalog/$libId")
                .header("Authorization", basicAuth(username, password)),
        )
        assertTrue(
            resp.bodyString().contains("/opds/books/$bookId/file"),
            "Single-file book must still have acquisition link",
        )
    }
}
