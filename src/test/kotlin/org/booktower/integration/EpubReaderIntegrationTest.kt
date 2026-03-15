package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubReaderIntegrationTest : IntegrationTestBase() {

    @Test
    fun `reader page requires authentication`() {
        val token = registerAndGetToken("er1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read"))
        // Should redirect to login (no cookie)
        assertEquals(Status.SEE_OTHER, response.status)
        assertTrue(response.header("Location")?.contains("login") == true)
    }

    @Test
    fun `reader page returns 200 for authenticated user`() {
        val token = registerAndGetToken("er2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `reader page returns 404 for nonexistent book`() {
        val token = registerAndGetToken("er3")

        val response = app(Request(Method.GET, "/books/00000000-0000-0000-0000-000000000000/read")
            .header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `reader page shows no-file state when no file uploaded`() {
        val token = registerAndGetToken("er4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("id=\"no-file\""), "Should show no-file div when no file uploaded")
        assertFalse(body.contains("id=\"pdf-canvas\""), "Should not show PDF canvas when no file uploaded")
        assertFalse(body.contains("id=\"epub-viewer\""), "Should not show EPUB viewer when no file uploaded")
    }

    @Test
    fun `reader page contains book title in toolbar`() {
        val token = registerAndGetToken("er5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My Test EPUB Book")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("My Test EPUB Book"), "Toolbar should contain the book title")
    }

    @Test
    fun `reader page has back link to book detail`() {
        val token = registerAndGetToken("er6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("/books/$bookId"), "Should have link back to book detail page")
    }

    @Test
    fun `reader page returns 404 for book belonging to different user`() {
        val token1 = registerAndGetToken("er7a")
        val token2 = registerAndGetToken("er7b")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token2"))
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
