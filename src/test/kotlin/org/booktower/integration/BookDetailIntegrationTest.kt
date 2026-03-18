package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookDetailIntegrationTest : IntegrationTestBase() {
    // ── Breadcrumb ─────────────────────────────────────────────────────────────

    @Test
    fun `book detail page breadcrumb shows library name`() {
        val token = registerAndGetToken("bdet1")
        val libId = createLibrary(token, "My Shelf")
        val bookId = createBook(token, libId, "Breadcrumb Book")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("My Shelf"), "Breadcrumb should show library name")
    }

    @Test
    fun `book detail page breadcrumb library name links to library`() {
        val token = registerAndGetToken("bdet2")
        val libId = createLibrary(token, "Linked Library")
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("/libraries/$libId"), "Breadcrumb should link to library page")
    }

    @Test
    fun `book detail page breadcrumb shows Libraries link`() {
        val token = registerAndGetToken("bdet3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("href=\"/libraries\""), "Breadcrumb should link to /libraries")
    }

    // ── Delete button ──────────────────────────────────────────────────────────

    @Test
    fun `book detail page has delete button`() {
        val token = registerAndGetToken("bdet4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Deletable Book")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("hx-delete=\"/ui/books/$bookId\""),
            "Book detail page should have delete button wired to HTMX delete",
        )
    }

    @Test
    fun `DELETE ui-books from detail page removes book`() {
        val token = registerAndGetToken("bdet5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "To Delete")

        val response = app(Request(Method.DELETE, "/ui/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("showToast") == true)

        // Verify book is gone
        val getResp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, getResp.status)
    }

    @Test
    fun `book detail delete button contains redirect target for library`() {
        val token = registerAndGetToken("bdet6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        // The delete button redirects to the library page after deletion
        assertTrue(body.contains("/libraries/$libId"), "Delete redirect should target the book's library")
    }

    @Test
    fun `book detail page shows library id in breadcrumb link even with no library name`() {
        val token = registerAndGetToken("bdet7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        // Page must render without error
        assertTrue(response.bodyString().contains("/libraries/$libId"))
    }
}
