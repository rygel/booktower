package org.booktower.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveBookIntegrationTest : IntegrationTestBase() {
    private fun move(
        token: String,
        bookId: String,
        targetLibId: String,
    ) = app(
        Request(Method.POST, "/ui/books/$bookId/move")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("targetLibraryId=$targetLibId"),
    )

    private fun booksInLib(
        token: String,
        libId: String,
    ): String = app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token")).bodyString()

    private fun minimalPdf(): ByteArray {
        val doc = PDDocument().also { it.addPage(PDPage()) }
        return ByteArrayOutputStream()
            .also {
                doc.save(it)
                doc.close()
            }.toByteArray()
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `move book returns 200 with HX-Redirect to target library`() {
        val token = registerAndGetToken("mv1")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        val resp = move(token, bookId, lib2)
        assertEquals(Status.OK, resp.status)
        assertTrue(
            resp.header("HX-Redirect")?.contains("/libraries/$lib2") == true,
            "Should redirect to target library",
        )
    }

    @Test
    fun `book appears in target library after move`() {
        val token = registerAndGetToken("mv2")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1, "Travelling Book")

        move(token, bookId, lib2)

        assertTrue(booksInLib(token, lib2).contains(bookId), "book should appear in target library")
    }

    @Test
    fun `book disappears from source library after move`() {
        val token = registerAndGetToken("mv3")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        move(token, bookId, lib2)

        assertFalse(booksInLib(token, lib1).contains(bookId), "book should be gone from source library")
    }

    @Test
    fun `book detail page shows new library after move`() {
        val token = registerAndGetToken("mv4")
        val lib1 = createLibrary(token, "Origin Library")
        val lib2 = createLibrary(token, "Destination Library")
        val bookId = createBook(token, lib1)

        move(token, bookId, lib2)

        // Fetch book via API to confirm libraryId updated
        val body = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains(lib2), "book DTO should reference target library id")
        assertFalse(body.contains(lib1), "book DTO should no longer reference source library id")
    }

    // ── Data integrity after move ─────────────────────────────────────────────

    @Test
    fun `bookmarks survive a move`() {
        val token = registerAndGetToken("mv5")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        val bmResp =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":42,"title":"Survive Move","note":null}"""),
            )
        val bmId =
            Json.mapper
                .readTree(bmResp.bodyString())
                .get("id")
                .asText()

        move(token, bookId, lib2)

        val bms =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(bms.contains(bmId), "bookmark should survive the move")
    }

    @Test
    fun `reading progress survives a move`() {
        val token = registerAndGetToken("mv6")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":77}"""),
        )

        move(token, bookId, lib2)

        val body = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("77"), "progress should survive the move")
    }

    @Test
    fun `uploaded file is still downloadable after move`() {
        val token = registerAndGetToken("mv7")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "test.pdf")
                .body(ByteArrayInputStream(minimalPdf())),
        )

        move(token, bookId, lib2)

        val dl = app(Request(Method.GET, "/api/books/$bookId/file").header("Cookie", "token=$token"))
        assertEquals(Status.OK, dl.status, "file should still be downloadable after move")
    }

    @Test
    fun `read status survives a move`() {
        val token = registerAndGetToken("mv8")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=READING"),
        )

        move(token, bookId, lib2)

        val body = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("READING"), "read status should survive the move")
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    fun `move requires authentication`() {
        val token = registerAndGetToken("mv9")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/move")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("targetLibraryId=$lib2"),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `move to nonexistent library returns 404`() {
        val token = registerAndGetToken("mv10")
        val lib1 = createLibrary(token)
        val bookId = createBook(token, lib1)
        val fakeLibId = "00000000-0000-0000-0000-000000000000"

        val resp = move(token, bookId, fakeLibId)
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `move nonexistent book returns 404`() {
        val token = registerAndGetToken("mv11")
        val lib2 = createLibrary(token)
        val fakeBookId = "00000000-0000-0000-0000-000000000000"

        val resp = move(token, fakeBookId, lib2)
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `move to another user library returns 404`() {
        val ownerToken = registerAndGetToken("mv12o")
        val attackerToken = registerAndGetToken("mv12a")
        val lib1 = createLibrary(ownerToken)
        val bookId = createBook(ownerToken, lib1)
        val attackerLib = createLibrary(attackerToken)

        // Attacker tries to move owner's book to their own library
        val resp = move(attackerToken, bookId, attackerLib)
        assertEquals(Status.NOT_FOUND, resp.status, "attacker cannot move owner's book")
    }

    @Test
    fun `owner cannot move book to another user library`() {
        val ownerToken = registerAndGetToken("mv13o")
        val otherToken = registerAndGetToken("mv13x")
        val lib1 = createLibrary(ownerToken)
        val bookId = createBook(ownerToken, lib1)
        val otherLib = createLibrary(otherToken)

        val resp = move(ownerToken, bookId, otherLib)
        assertEquals(Status.NOT_FOUND, resp.status, "cannot move book into another user's library")
    }

    @Test
    fun `move with missing targetLibraryId returns 400`() {
        val token = registerAndGetToken("mv14")
        val lib1 = createLibrary(token)
        val bookId = createBook(token, lib1)

        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/move")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `move with malformed targetLibraryId returns 400`() {
        val token = registerAndGetToken("mv15")
        val lib1 = createLibrary(token)
        val bookId = createBook(token, lib1)

        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/move")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("targetLibraryId=not-a-uuid"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    // ── UI: book detail page shows Move button when multiple libraries exist ──

    @Test
    fun `book detail page shows move button when multiple libraries exist`() {
        val token = registerAndGetToken("mv16")
        val lib1 = createLibrary(token)
        createLibrary(token) // second library needed for button to appear
        val bookId = createBook(token, lib1)

        val html = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            html.contains("move-panel") || html.contains("ri-folder-transfer-line"),
            "Move button should appear when user has multiple libraries",
        )
    }

    @Test
    fun `book detail page does not show move button when user has only one library`() {
        val token = registerAndGetToken("mv17")
        val lib1 = createLibrary(token)
        val bookId = createBook(token, lib1)

        val html = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            html.contains("move-panel"),
            "Move button should not appear when user has only one library",
        )
    }

    // ── Move between multiple libraries ──────────────────────────────────────

    @Test
    fun `book can be moved multiple times`() {
        val token = registerAndGetToken("mv18")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val lib3 = createLibrary(token)
        val bookId = createBook(token, lib1, "Nomadic Book")

        move(token, bookId, lib2)
        assertTrue(booksInLib(token, lib2).contains(bookId))
        assertFalse(booksInLib(token, lib1).contains(bookId))

        move(token, bookId, lib3)
        assertTrue(booksInLib(token, lib3).contains(bookId))
        assertFalse(booksInLib(token, lib2).contains(bookId))
    }

    @Test
    fun `library book counts update after move`() {
        val token = registerAndGetToken("mv19")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val bookId = createBook(token, lib1)

        move(token, bookId, lib2)

        val libs = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token")).bodyString()
        val tree = Json.mapper.readTree(libs)
        val lib1Count = tree.first { it.get("id").asText() == lib1 }.get("bookCount").asInt()
        val lib2Count = tree.first { it.get("id").asText() == lib2 }.get("bookCount").asInt()
        assertEquals(0, lib1Count, "source library should have 0 books after move")
        assertEquals(1, lib2Count, "target library should have 1 book after move")
    }
}
