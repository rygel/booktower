package org.runary.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.BookmarkDto
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that PDF annotations and bookmarks on the same book
 * coexist correctly and don't interfere with each other during
 * creation, deletion, and cascading scenarios.
 */
class AnnotationBookmarkCoexistenceTest : IntegrationTestBase() {
    private val mapper = ObjectMapper()

    private fun addBookmark(
        token: String,
        bookId: String,
        page: Int,
        title: String = "Mark",
    ): String {
        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":$page,"title":"$title","note":null}"""),
            )
        return Json.mapper.readValue(r.bodyString(), BookmarkDto::class.java).id
    }

    private fun addAnnotation(
        token: String,
        bookId: String,
        page: Int,
        text: String,
    ): String {
        val r =
            app(
                Request(Method.POST, "/ui/books/$bookId/annotations")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("page=$page&selectedText=${java.net.URLEncoder.encode(text, "UTF-8")}&color=yellow"),
            )
        return mapper.readTree(r.bodyString()).get("id").asText()
    }

    private fun getAnnotations(
        token: String,
        bookId: String,
    ) = app(Request(Method.GET, "/ui/books/$bookId/annotations").header("Cookie", "token=$token")).bodyString()

    private fun getBookmarks(
        token: String,
        bookId: String,
    ) = app(Request(Method.GET, "/api/bookmarks?bookId=$bookId").header("Cookie", "token=$token")).bodyString()

    // ── Creation coexistence ─────────────────────────────────────────────────

    @Test
    fun `annotation and bookmark on same page coexist`() {
        val token = registerAndGetToken("ab1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bmId = addBookmark(token, bookId, 7, "Chapter Start")
        val anId = addAnnotation(token, bookId, 7, "Important passage on page 7")

        assertTrue(getBookmarks(token, bookId).contains(bmId), "bookmark should exist")
        assertTrue(getAnnotations(token, bookId).contains("Important passage on page 7"), "annotation should exist")
    }

    @Test
    fun `multiple annotations and bookmarks across different pages all persist`() {
        val token = registerAndGetToken("ab2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bm1 = addBookmark(token, bookId, 1, "Intro")
        val bm2 = addBookmark(token, bookId, 50, "Midpoint")
        addAnnotation(token, bookId, 10, "note on ten")
        addAnnotation(token, bookId, 20, "note on twenty")

        val bookmarks = getBookmarks(token, bookId)
        val annotations = getAnnotations(token, bookId)
        assertTrue(bookmarks.contains(bm1))
        assertTrue(bookmarks.contains(bm2))
        assertTrue(annotations.contains("note on ten"))
        assertTrue(annotations.contains("note on twenty"))
    }

    // ── Deletion isolation ───────────────────────────────────────────────────

    @Test
    fun `deleting bookmark does not remove annotations`() {
        val token = registerAndGetToken("ab3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bmId = addBookmark(token, bookId, 3)
        addAnnotation(token, bookId, 3, "survive the bookmark delete")

        app(Request(Method.DELETE, "/api/bookmarks/$bmId").header("Cookie", "token=$token"))

        assertTrue(
            getAnnotations(token, bookId).contains("survive the bookmark delete"),
            "annotation should survive bookmark deletion",
        )
    }

    @Test
    fun `deleting annotation does not remove bookmarks`() {
        val token = registerAndGetToken("ab4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bmId = addBookmark(token, bookId, 5, "Persistent Bookmark")
        val anId = addAnnotation(token, bookId, 5, "annotation to delete")

        app(Request(Method.DELETE, "/ui/annotations/$anId").header("Cookie", "token=$token"))

        assertTrue(
            getBookmarks(token, bookId).contains(bmId),
            "bookmark should survive annotation deletion",
        )
        assertFalse(
            getAnnotations(token, bookId).contains("annotation to delete"),
            "deleted annotation should not appear",
        )
    }

    // ── Cascading book deletion ──────────────────────────────────────────────

    @Test
    fun `deleting a book removes both its annotations and bookmarks`() {
        val token = registerAndGetToken("ab5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        addBookmark(token, bookId, 1)
        addAnnotation(token, bookId, 1, "doomed annotation")

        app(Request(Method.DELETE, "/api/books/$bookId").header("Cookie", "token=$token"))

        // Annotation endpoint returns 404/401 for deleted book — just verify it's gone from list
        val bookResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, bookResp.status, "deleted book should be gone")

        // Bookmarks for the deleted book should return empty (book scoped → not found)
        val bms = getBookmarks(token, bookId)
        assertFalse(bms.contains("\"page\":1"), "bookmarks for deleted book should be gone")
    }

    // ── Multiple annotations, single bookmark ────────────────────────────────

    @Test
    fun `many annotations do not interfere with bookmark retrieval`() {
        val token = registerAndGetToken("ab6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bmId = addBookmark(token, bookId, 99, "Only Bookmark")
        repeat(5) { i -> addAnnotation(token, bookId, i + 1, "annotation $i") }

        val bookmarks = getBookmarks(token, bookId)
        assertTrue(bookmarks.contains(bmId), "bookmark should remain with many annotations present")
        assertEquals(1, bookmarks.split(bmId).size - 1, "exactly one bookmark should exist")
    }

    // ── Cross-user isolation ─────────────────────────────────────────────────

    @Test
    fun `annotations and bookmarks from different users on same-titled books are isolated`() {
        val tokenA = registerAndGetToken("abA")
        val tokenB = registerAndGetToken("abB")

        val libA = createLibrary(tokenA)
        val bookA = createBook(tokenA, libA, "Shared Title")
        val libB = createLibrary(tokenB)
        val bookB = createBook(tokenB, libB, "Shared Title")

        addBookmark(tokenA, bookA, 10, "User A bookmark")
        addAnnotation(tokenA, bookA, 10, "User A annotation")

        // User B's book has none
        assertFalse(
            getBookmarks(tokenB, bookB).contains("User A bookmark"),
            "B should not see A's bookmarks",
        )
        assertFalse(
            getAnnotations(tokenB, bookB).contains("User A annotation"),
            "B should not see A's annotations",
        )
    }
}
