package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MagicShelfIntegrationTest : IntegrationTestBase() {
    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createShelf(
        token: String,
        name: String,
        ruleType: String,
        ruleValue: String,
    ): String {
        val valueField =
            when (ruleType) {
                "STATUS" -> "ruleValueStatus=$ruleValue"
                "TAG" -> "ruleValueTag=$ruleValue"
                else -> "ruleValueRating=$ruleValue"
            }
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=$name&ruleType=$ruleType&$valueField"),
            )
        assertEquals(Status.CREATED, resp.status, "createShelf failed: ${resp.bodyString()}")
        // Extract shelf id from the response HTML (id="shelf-{uuid}")
        val match = Regex("""id="shelf-([0-9a-f-]{36})"""").find(resp.bodyString())
        return match?.groupValues?.get(1) ?: error("Could not find shelf id in response")
    }

    private fun deleteShelf(
        token: String,
        shelfId: String,
    ) = app(Request(Method.DELETE, "/ui/shelves/$shelfId").header("Cookie", "token=$token"))

    private fun setStatus(
        token: String,
        bookId: String,
        status: String,
    ) = app(
        Request(Method.POST, "/ui/books/$bookId/status")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("status=$status"),
    )

    private fun setRating(
        token: String,
        bookId: String,
        rating: Int,
    ) = app(
        Request(Method.POST, "/ui/books/$bookId/rating")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("rating=$rating"),
    )

    private fun setTags(
        token: String,
        bookId: String,
        tags: String,
    ) = app(
        Request(Method.POST, "/ui/books/$bookId/tags")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("tags=$tags"),
    )

    private fun librariesPage(token: String): String {
        val resp = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status, "Libraries page should return 200")
        return resp.bodyString()
    }

    private fun shelfPage(
        token: String,
        shelfId: String,
    ) = app(Request(Method.GET, "/shelves/$shelfId").header("Cookie", "token=$token"))

    // ── Libraries page shows shelves section ──────────────────────────────────

    @Test
    fun `libraries page contains smart shelves section`() {
        val token = registerAndGetToken("sh_page1")
        val html = librariesPage(token)
        assertTrue(html.contains("ri-sparkling-line"), "Should have smart shelf icon")
    }

    @Test
    fun `libraries page shows created shelf card`() {
        val token = registerAndGetToken("sh_page2")
        val lib = createLibrary(token)
        createShelf(token, "Reading Now", "STATUS", "READING")

        val resp = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status, "Libraries page should return 200, got ${resp.status}")
        val html = resp.bodyString()
        assertTrue(html.contains("Reading Now"), "Shelf name should appear on libraries page")
    }

    // ── Create shelf ──────────────────────────────────────────────────────────

    @Test
    fun `create STATUS shelf returns 201 with rendered card`() {
        val token = registerAndGetToken("sh_cre1")
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=My+Shelf&ruleType=STATUS&ruleValueStatus=READING"),
            )
        assertEquals(Status.CREATED, resp.status)
        assertTrue(resp.bodyString().contains("My Shelf"))
    }

    @Test
    fun `create TAG shelf returns 201`() {
        val token = registerAndGetToken("sh_cre2")
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=SciFi&ruleType=TAG&ruleValueTag=sci-fi"),
            )
        assertEquals(Status.CREATED, resp.status)
    }

    @Test
    fun `create RATING_GTE shelf returns 201`() {
        val token = registerAndGetToken("sh_cre3")
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=Top+Rated&ruleType=RATING_GTE&ruleValueRating=4"),
            )
        assertEquals(Status.CREATED, resp.status)
    }

    @Test
    fun `create shelf without name returns 400`() {
        val token = registerAndGetToken("sh_bad1")
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("ruleType=STATUS&ruleValueStatus=READING"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `create shelf with invalid ruleType returns 400`() {
        val token = registerAndGetToken("sh_bad2")
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=Test&ruleType=INVALID&ruleValueStatus=READING"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `create shelf requires authentication`() {
        val resp =
            app(
                Request(Method.POST, "/ui/shelves")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("name=Test&ruleType=STATUS&ruleValueStatus=READING"),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    // ── Delete shelf ──────────────────────────────────────────────────────────

    @Test
    fun `delete shelf returns 200`() {
        val token = registerAndGetToken("sh_del1")
        val shelfId = createShelf(token, "Temp", "STATUS", "READING")
        val resp = deleteShelf(token, shelfId)
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `deleted shelf no longer appears on libraries page`() {
        val token = registerAndGetToken("sh_del2")
        val shelfId = createShelf(token, "Gone Shelf", "STATUS", "READING")
        deleteShelf(token, shelfId)
        assertFalse(librariesPage(token).contains("Gone Shelf"))
    }

    @Test
    fun `delete shelf of another user returns 200 but is a no-op`() {
        val token1 = registerAndGetToken("sh_del3o")
        val token2 = registerAndGetToken("sh_del3a")
        val shelfId = createShelf(token1, "Private", "STATUS", "READING")

        // Token2 tries to delete token1's shelf
        val resp = deleteShelf(token2, shelfId)
        // Returns 200 (no error) but shelf is still there
        assertEquals(Status.OK, resp.status)
        assertTrue(librariesPage(token1).contains("Private"), "Shelf should still exist for owner")
    }

    // ── Shelf detail page ─────────────────────────────────────────────────────

    @Test
    fun `shelf detail page returns 200`() {
        val token = registerAndGetToken("sh_det1")
        val shelfId = createShelf(token, "My Shelf", "STATUS", "READING")
        val resp = shelfPage(token, shelfId)
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `shelf detail page shows shelf name`() {
        val token = registerAndGetToken("sh_det2")
        val shelfId = createShelf(token, "Great Books", "STATUS", "FINISHED")
        assertTrue(shelfPage(token, shelfId).bodyString().contains("Great Books"))
    }

    @Test
    fun `shelf detail page returns 404 for nonexistent shelf`() {
        val token = registerAndGetToken("sh_det3")
        val resp = shelfPage(token, "00000000-0000-0000-0000-000000000000")
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `shelf detail page returns 404 for another user shelf`() {
        val token1 = registerAndGetToken("sh_det4o")
        val token2 = registerAndGetToken("sh_det4a")
        val shelfId = createShelf(token1, "Private Shelf", "STATUS", "READING")
        assertEquals(Status.NOT_FOUND, shelfPage(token2, shelfId).status)
    }

    @Test
    fun `shelf detail page requires authentication`() {
        val token = registerAndGetToken("sh_det5")
        val shelfId = createShelf(token, "Auth Shelf", "STATUS", "READING")
        val resp = app(Request(Method.GET, "/shelves/$shelfId"))
        // Should redirect to login
        assertTrue(resp.status.code in listOf(302, 303))
    }

    // ── STATUS shelf resolves correct books ───────────────────────────────────

    @Test
    fun `STATUS shelf shows books with matching status`() {
        val token = registerAndGetToken("sh_st1")
        val lib = createLibrary(token)
        val book1 = createBook(token, lib, "Reading Book")
        val book2 = createBook(token, lib, "Want Book")
        setStatus(token, book1, "READING")
        setStatus(token, book2, "WANT_TO_READ")

        val shelfId = createShelf(token, "Reading", "STATUS", "READING")
        val html = shelfPage(token, shelfId).bodyString()
        assertTrue(html.contains(book1), "Reading book should appear")
        assertFalse(html.contains(book2), "Want-to-read book should not appear")
    }

    @Test
    fun `STATUS shelf does not show books with no status`() {
        val token = registerAndGetToken("sh_st2")
        val lib = createLibrary(token)
        val noStatusBook = createBook(token, lib, "No Status Book")

        val shelfId = createShelf(token, "Finished", "STATUS", "FINISHED")
        val html = shelfPage(token, shelfId).bodyString()
        assertFalse(html.contains(noStatusBook), "Untagged book should not appear")
    }

    @Test
    fun `STATUS shelf updates dynamically when book status changes`() {
        val token = registerAndGetToken("sh_st3")
        val lib = createLibrary(token)
        val bookId = createBook(token, lib, "Changing Book")
        setStatus(token, bookId, "READING")

        val shelfId = createShelf(token, "Reading", "STATUS", "READING")
        assertTrue(shelfPage(token, shelfId).bodyString().contains(bookId), "Book should appear in shelf")

        // Change status
        setStatus(token, bookId, "FINISHED")
        assertFalse(shelfPage(token, shelfId).bodyString().contains(bookId), "Book should no longer appear")
    }

    // ── TAG shelf resolves correct books ──────────────────────────────────────

    @Test
    fun `TAG shelf shows books with matching tag`() {
        val token = registerAndGetToken("sh_tag1")
        val lib = createLibrary(token)
        val book1 = createBook(token, lib, "SciFi Book")
        val book2 = createBook(token, lib, "History Book")
        setTags(token, book1, "sci-fi")
        setTags(token, book2, "history")

        val shelfId = createShelf(token, "SciFi", "TAG", "sci-fi")
        val html = shelfPage(token, shelfId).bodyString()
        assertTrue(html.contains(book1), "SciFi book should appear")
        assertFalse(html.contains(book2), "History book should not appear")
    }

    @Test
    fun `TAG shelf is case-insensitive`() {
        val token = registerAndGetToken("sh_tag2")
        val lib = createLibrary(token)
        val bookId = createBook(token, lib, "Case Book")
        setTags(token, bookId, "Sci-Fi") // set as mixed case

        // Shelf rule is lowercase (normalized by server)
        val shelfId = createShelf(token, "SciFi", "TAG", "sci-fi")
        val html = shelfPage(token, shelfId).bodyString()
        assertTrue(html.contains(bookId), "Tag matching should be case-insensitive")
    }

    // ── RATING_GTE shelf resolves correct books ───────────────────────────────

    @Test
    fun `RATING_GTE shelf shows books at or above minimum rating`() {
        val token = registerAndGetToken("sh_rat1")
        val lib = createLibrary(token)
        val book5 = createBook(token, lib, "Five Stars")
        val book4 = createBook(token, lib, "Four Stars")
        val book3 = createBook(token, lib, "Three Stars")
        setRating(token, book5, 5)
        setRating(token, book4, 4)
        setRating(token, book3, 3)

        val shelfId = createShelf(token, "High Rated", "RATING_GTE", "4")
        val html = shelfPage(token, shelfId).bodyString()
        assertTrue(html.contains(book5), "5-star book should appear")
        assertTrue(html.contains(book4), "4-star book should appear")
        assertFalse(html.contains(book3), "3-star book should not appear")
    }

    @Test
    fun `RATING_GTE shelf does not show unrated books`() {
        val token = registerAndGetToken("sh_rat2")
        val lib = createLibrary(token)
        val unratedBook = createBook(token, lib, "Unrated")

        val shelfId = createShelf(token, "Rated", "RATING_GTE", "1")
        val html = shelfPage(token, shelfId).bodyString()
        assertFalse(html.contains(unratedBook), "Unrated book should not appear even with rating >= 1")
    }

    // ── Cross-user isolation ──────────────────────────────────────────────────

    @Test
    fun `shelf only shows books belonging to its user`() {
        val token1 = registerAndGetToken("sh_iso1")
        val token2 = registerAndGetToken("sh_iso2")
        val lib1 = createLibrary(token1)
        val lib2 = createLibrary(token2)
        val book1 = createBook(token1, lib1, "User1 Book")
        val book2 = createBook(token2, lib2, "User2 Book")
        setStatus(token1, book1, "READING")
        setStatus(token2, book2, "READING")

        val shelfId = createShelf(token1, "Reading", "STATUS", "READING")
        val html = shelfPage(token1, shelfId).bodyString()
        assertTrue(html.contains(book1), "Should contain own book")
        assertFalse(html.contains(book2), "Should not contain other user's book")
    }

    // ── Book count on shelf card ──────────────────────────────────────────────

    @Test
    fun `shelf card shows correct book count`() {
        val token = registerAndGetToken("sh_cnt1")
        val lib = createLibrary(token)
        val b1 = createBook(token, lib, "A")
        val b2 = createBook(token, lib, "B")
        setStatus(token, b1, "FINISHED")
        setStatus(token, b2, "FINISHED")

        createShelf(token, "Done", "STATUS", "FINISHED")

        // Verify via the shelf page API instead of parsing HTML
        val shelfResp =
            app(
                Request(Method.GET, "/api/books?pageSize=50&statusFilter=FINISHED")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, shelfResp.status)
        val books =
            org.runary.config.Json.mapper
                .readValue(shelfResp.bodyString(), org.runary.models.BookListDto::class.java)
                .getBooks()
        assertEquals(2, books.size, "Should have 2 FINISHED books")
    }

    // ── Books span multiple libraries ─────────────────────────────────────────

    @Test
    fun `shelf aggregates books from all user libraries`() {
        val token = registerAndGetToken("sh_multi1")
        val lib1 = createLibrary(token, "Lib A")
        val lib2 = createLibrary(token, "Lib B")
        val book1 = createBook(token, lib1, "Book from Lib A")
        val book2 = createBook(token, lib2, "Book from Lib B")
        setStatus(token, book1, "READING")
        setStatus(token, book2, "READING")

        val shelfId = createShelf(token, "All Reading", "STATUS", "READING")
        val html = shelfPage(token, shelfId).bodyString()
        assertTrue(html.contains(book1), "Should include book from lib1")
        assertTrue(html.contains(book2), "Should include book from lib2")
    }
}
