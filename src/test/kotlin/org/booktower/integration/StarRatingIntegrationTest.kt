package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarRatingIntegrationTest : IntegrationTestBase() {

    private fun setRating(token: String, bookId: String, rating: Int?) {
        val body = if (rating != null) "rating=$rating" else "rating="
        app(Request(Method.POST, "/ui/books/$bookId/rating")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(body))
    }

    @Test
    fun `rating endpoint returns 200 for valid rating`() {
        val token = registerAndGetToken("sr1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val response = app(Request(Method.POST, "/ui/books/$bookId/rating")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("rating=4"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `book detail page shows star rating widget`() {
        val token = registerAndGetToken("sr2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("star-rating"), "Book detail should show star rating widget")
        assertTrue(body.contains("star-btn"), "Book detail should show star buttons")
    }

    @Test
    fun `rating is reflected on book detail page after setting`() {
        val token = registerAndGetToken("sr3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setRating(token, bookId, 3)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("currentRating = 3"), "Rating of 3 should be reflected on detail page")
    }

    @Test
    fun `clearing rating removes it from book detail page`() {
        val token = registerAndGetToken("sr4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setRating(token, bookId, 5)
        setRating(token, bookId, null)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("currentRating = 0"), "Rating should be 0 after clearing")
    }

    @Test
    fun `rating badge appears on library card after setting rating`() {
        val token = registerAndGetToken("sr5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Rated Book")
        setRating(token, bookId, 4)
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("book-rating-badge"), "Library card should show rating badge")
    }

    @Test
    fun `no rating badge on library card when no rating set`() {
        val token = registerAndGetToken("sr6")
        val libId = createLibrary(token)
        createBook(token, libId, "Unrated Book")
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("book-rating-badge"), "No rating badge should appear when no rating")
    }

    @Test
    fun `updating rating changes it`() {
        val token = registerAndGetToken("sr7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setRating(token, bookId, 2)
        setRating(token, bookId, 5)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("currentRating = 5"), "Rating should be updated to 5")
    }

    @Test
    fun `rating is per-user`() {
        val token1 = registerAndGetToken("sr8a")
        val token2 = registerAndGetToken("sr8b")
        val libId1 = createLibrary(token1)
        val bookId = createBook(token1, libId1, "Shared Book")
        setRating(token1, bookId, 5)

        // user2 creates their own book with different rating
        val libId2 = createLibrary(token2)
        val bookId2 = createBook(token2, libId2, "User2 Book")
        setRating(token2, bookId2, 1)

        // user1's rating should still be 5
        val body1 = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token1")).bodyString()
        assertTrue(body1.contains("currentRating = 5"), "User1's rating should be preserved as 5")
    }

    @Test
    fun `rating out of range is ignored`() {
        val token = registerAndGetToken("sr9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        // Set valid rating first
        setRating(token, bookId, 3)
        // Try to set invalid rating (6)
        app(Request(Method.POST, "/ui/books/$bookId/rating")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("rating=6"))
        // Rating should be cleared (6 is treated as out of range → delete)
        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("currentRating = 0"), "Out-of-range rating (6) should clear the rating")
    }

    @Test
    fun `rating requires authentication`() {
        val response = app(Request(Method.POST, "/ui/books/some-id/rating")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("rating=3"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }
}
