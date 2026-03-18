package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagsIntegrationTest : IntegrationTestBase() {
    private fun setTags(
        token: String,
        bookId: String,
        tags: String,
    ) {
        app(
            Request(Method.POST, "/ui/books/$bookId/tags")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("tags=${java.net.URLEncoder.encode(tags, "UTF-8")}"),
        )
    }

    @Test
    fun `set tags returns 200`() {
        val token = registerAndGetToken("tg1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val response =
            app(
                Request(Method.POST, "/ui/books/$bookId/tags")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("tags=sci-fi"),
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `book detail shows tags after setting`() {
        val token = registerAndGetToken("tg2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setTags(token, bookId, "fantasy, adventure")
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("fantasy"), "Book detail should show tag 'fantasy'")
        assertTrue(body.contains("adventure"), "Book detail should show tag 'adventure'")
    }

    @Test
    fun `book detail shows tags editor widget`() {
        val token = registerAndGetToken("tg3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("tags-editor") || body.contains("tags-input"), "Book detail should show tags editor")
    }

    @Test
    fun `tags appear on library card`() {
        val token = registerAndGetToken("tg4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Tagged Book")
        setTags(token, bookId, "mystery")
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("mystery"), "Library card should show tag 'mystery'")
    }

    @Test
    fun `tag filter shows only matching books`() {
        val token = registerAndGetToken("tg5")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Sci-Fi Book")
        val book2 = createBook(token, libId, "History Book")
        setTags(token, book1, "sci-fi")
        setTags(token, book2, "history")
        val body =
            app(
                Request(Method.GET, "/libraries/$libId?tag=sci-fi")
                    .header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("Sci-Fi Book"), "Tag filter should show Sci-Fi Book")
        assertFalse(body.contains("History Book"), "Tag filter should not show History Book")
    }

    @Test
    fun `tag filter dropdown shows user tags`() {
        val token = registerAndGetToken("tg6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setTags(token, bookId, "thriller")
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("thriller"), "Library page tag filter should show user's tags")
    }

    @Test
    fun `updating tags replaces previous tags`() {
        val token = registerAndGetToken("tg7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setTags(token, bookId, "old-tag")
        setTags(token, bookId, "new-tag")
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("new-tag"), "New tag should be present")
        assertFalse(body.contains("old-tag"), "Old tag should be gone after update")
    }

    @Test
    fun `tags are per-user`() {
        val token1 = registerAndGetToken("tg8a")
        val token2 = registerAndGetToken("tg8b")
        val libId1 = createLibrary(token1)
        val book1 = createBook(token1, libId1, "Book1")
        setTags(token1, book1, "user1tag")
        val libId2 = createLibrary(token2)
        val book2 = createBook(token2, libId2, "Book2")
        setTags(token2, book2, "user2tag")
        val body2 = app(Request(Method.GET, "/libraries/$libId2").header("Cookie", "token=$token2")).bodyString()
        assertFalse(body2.contains("user1tag"), "User2 should not see user1's tags")
        assertTrue(body2.contains("user2tag"), "User2 should see their own tags")
    }

    @Test
    fun `tags require authentication`() {
        val response =
            app(
                Request(Method.POST, "/ui/books/some-id/tags")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("tags=sci-fi"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `empty tags clears all tags`() {
        val token = registerAndGetToken("tg9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setTags(token, bookId, "to-clear")
        setTags(token, bookId, "")
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("to-clear"), "Tags should be cleared when empty string sent")
    }
}
