package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.BookListDto
import org.booktower.models.LibraryDto
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that users cannot access, modify, or delete resources belonging to other users.
 * Tests cross-user isolation across libraries, books, bookmarks, reading sessions, and files.
 */
class PermissionBoundaryIntegrationTest : IntegrationTestBase() {
    private fun createLibraryAndBook(
        token: String,
        libName: String = "Lib ${System.nanoTime()}",
        bookTitle: String = "Book ${System.nanoTime()}",
    ): Pair<String, String> {
        val libId = createLibrary(token, libName)
        val bookId = createBook(token, libId, bookTitle)
        return libId to bookId
    }

    private fun uploadFile(
        token: String,
        bookId: String,
        filename: String,
        bytes: ByteArray,
    ) {
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", filename)
                .header("Content-Type", "application/octet-stream")
                .body(Body(ByteArrayInputStream(bytes), bytes.size.toLong())),
        )
    }

    private fun minimalEpubBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.setMethod(ZipOutputStream.STORED)
            val mimeBytes = "application/epub+zip".toByteArray()
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.size = mimeBytes.size.toLong()
            mimeEntry.compressedSize = mimeBytes.size.toLong()
            mimeEntry.crc = CRC32().also { it.update(mimeBytes) }.value
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()
            zip.setMethod(ZipOutputStream.DEFLATED)
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write("""<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title><dc:identifier id="id">t</dc:identifier></metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/c.xhtml"))
            zip.write("""<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><body><p>Test</p></body></html>""".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    // ── Library isolation ─────────────────────────────────────────────────

    @Test
    fun `user cannot see other user's libraries`() {
        val token1 = registerAndGetToken("perm_lib1")
        val token2 = registerAndGetToken("perm_lib2")
        createLibrary(token1, "User1 Private Library")

        val resp = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token2"))
        assertEquals(Status.OK, resp.status)
        val libs = Json.mapper.readValue(resp.bodyString(), Array<LibraryDto>::class.java)
        assertTrue(libs.none { it.name == "User1 Private Library" }, "User2 should not see User1's library")
    }

    @Test
    fun `user cannot delete other user's library`() {
        val token1 = registerAndGetToken("perm_dlib1")
        val token2 = registerAndGetToken("perm_dlib2")
        val libId = createLibrary(token1, "User1 Lib To Delete")

        val resp = app(Request(Method.DELETE, "/api/libraries/$libId").header("Cookie", "token=$token2"))
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Deleting another user's library should fail, got ${resp.status}",
        )
    }

    // ── Book isolation ────────────────────────────────────────────────────

    @Test
    fun `user cannot see other user's books`() {
        val token1 = registerAndGetToken("perm_book1")
        val token2 = registerAndGetToken("perm_book2")
        createLibraryAndBook(token1, bookTitle = "Secret Book")

        val resp = app(Request(Method.GET, "/api/books?pageSize=100").header("Cookie", "token=$token2"))
        assertEquals(Status.OK, resp.status)
        val books = Json.mapper.readValue(resp.bodyString(), BookListDto::class.java).getBooks()
        assertTrue(books.none { it.title == "Secret Book" }, "User2 should not see User1's books")
    }

    @Test
    fun `user cannot access other user's book by ID`() {
        val token1 = registerAndGetToken("perm_bookid1")
        val token2 = registerAndGetToken("perm_bookid2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token2"))
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Accessing another user's book should fail, got ${resp.status}",
        )
    }

    @Test
    fun `user cannot delete other user's book`() {
        val token1 = registerAndGetToken("perm_delbook1")
        val token2 = registerAndGetToken("perm_delbook2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp = app(Request(Method.DELETE, "/api/books/$bookId").header("Cookie", "token=$token2"))
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Deleting another user's book should fail, got ${resp.status}",
        )
    }

    @Test
    fun `user cannot update other user's book`() {
        val token1 = registerAndGetToken("perm_upbook1")
        val token2 = registerAndGetToken("perm_upbook2")
        val (_, bookId) = createLibraryAndBook(token1, bookTitle = "Original Title")

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token2")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Hijacked Title","author":null,"description":null}"""),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Updating another user's book should fail, got ${resp.status}",
        )
    }

    // ── File isolation ────────────────────────────────────────────────────

    @Test
    fun `user cannot download other user's book file`() {
        val token1 = registerAndGetToken("perm_file1")
        val token2 = registerAndGetToken("perm_file2")
        val (_, bookId) = createLibraryAndBook(token1)
        uploadFile(token1, bookId, "secret.epub", minimalEpubBytes())

        val resp = app(Request(Method.GET, "/api/books/$bookId/file").header("Cookie", "token=$token2"))
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Downloading another user's file should fail, got ${resp.status}",
        )
    }

    @Test
    fun `user cannot upload to other user's book`() {
        val token1 = registerAndGetToken("perm_upload1")
        val token2 = registerAndGetToken("perm_upload2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token2")
                    .header("X-Filename", "malicious.epub")
                    .header("Content-Type", "application/octet-stream")
                    .body(Body(ByteArrayInputStream(minimalEpubBytes()), 100)),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Uploading to another user's book should fail, got ${resp.status}",
        )
    }

    // ── Bookmark isolation ────────────────────────────────────────────────

    @Test
    fun `user cannot create bookmark on other user's book`() {
        val token1 = registerAndGetToken("perm_bm1")
        val token2 = registerAndGetToken("perm_bm2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/bookmarks")
                    .header("Cookie", "token=$token2")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":42,"title":"Hacked","note":null}"""),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN || resp.status == Status.BAD_REQUEST,
            "Creating bookmark on another user's book should fail, got ${resp.status}",
        )
    }

    // ── Reading status isolation ──────────────────────────────────────────

    @Test
    fun `user cannot set status on other user's book`() {
        val token1 = registerAndGetToken("perm_stat1")
        val token2 = registerAndGetToken("perm_stat2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/status")
                    .header("Cookie", "token=$token2")
                    .header("Content-Type", "application/json")
                    .body("""{"status":"FINISHED"}"""),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN,
            "Setting status on another user's book should fail, got ${resp.status}",
        )
    }

    @Test
    fun `user cannot set rating on other user's book`() {
        val token1 = registerAndGetToken("perm_rate1")
        val token2 = registerAndGetToken("perm_rate2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/rating")
                    .header("Cookie", "token=$token2")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("rating=5"),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN || resp.status == Status.UNAUTHORIZED,
            "Setting rating on another user's book should fail, got ${resp.status}",
        )
    }

    // ── Tag isolation ─────────────────────────────────────────────────────

    @Test
    fun `user cannot set tags on other user's book`() {
        val token1 = registerAndGetToken("perm_tag1")
        val token2 = registerAndGetToken("perm_tag2")
        val (_, bookId) = createLibraryAndBook(token1)

        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/tags")
                    .header("Cookie", "token=$token2")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("tags=hacked"),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.FORBIDDEN || resp.status == Status.UNAUTHORIZED,
            "Setting tags on another user's book should fail, got ${resp.status}",
        )
    }

    // ── Notification isolation ────────────────────────────────────────────

    @Test
    fun `user cannot see other user's notifications`() {
        val token1 = registerAndGetToken("perm_notif1")
        val token2 = registerAndGetToken("perm_notif2")

        // Publish a notification for user1 directly
        val jdbi =
            org.booktower.TestFixture.database
                .getJdbi()
        val svc = org.booktower.services.NotificationService(jdbi)
        val jwt =
            com.auth0.jwt.JWT
                .decode(token1)
        val userId1 = java.util.UUID.fromString(jwt.subject)
        svc.publish(userId1, "secret", "User1's secret notification")

        // User2 should not see it
        val resp = app(Request(Method.GET, "/api/notifications").header("Cookie", "token=$token2"))
        assertEquals(Status.OK, resp.status)
        val items = Json.mapper.readTree(resp.bodyString())
        assertTrue(items.size() == 0 || items.none { it.get("title")?.asText() == "User1's secret notification" })
    }

    // ── Admin boundaries ──────────────────────────────────────────────────

    @Test
    fun `non-admin cannot access admin API`() {
        val token = registerAndGetToken("perm_noadmin")

        val endpoints =
            listOf(
                Request(Method.GET, "/api/admin/users"),
                Request(Method.GET, "/api/admin/tasks"),
                Request(Method.POST, "/admin/seed"),
                Request(Method.POST, "/admin/seed/files"),
                Request(Method.POST, "/admin/seed/comics"),
                Request(Method.POST, "/admin/seed/librivox"),
            )

        endpoints.forEach { req ->
            val resp = app(req.header("Cookie", "token=$token"))
            assertTrue(
                resp.status == Status.FORBIDDEN || resp.status == Status.UNAUTHORIZED,
                "Non-admin accessing ${req.uri.path} should be rejected, got ${resp.status}",
            )
        }
    }
}
