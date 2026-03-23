package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagBrowserIntegrationTest : IntegrationTestBase() {
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
    fun `tag list page returns 200`() {
        val token = registerAndGetToken("tag")
        val response = app(Request(Method.GET, "/tags").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Runary"))
    }

    @Test
    fun `tag list shows tag after book is tagged`() {
        val token = registerAndGetToken("tag")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Dune")
        setTags(token, bookId, "sci-fi, classic")

        val body = app(Request(Method.GET, "/tags").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("sci-fi"))
        assertTrue(body.contains("classic"))
    }

    @Test
    fun `tag list is empty when no books have tags`() {
        val token = registerAndGetToken("tag")
        createBook(token, createLibrary(token), "No Tag Book")

        val body = app(Request(Method.GET, "/tags").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("No Tag Book"))
    }

    @Test
    fun `tag detail page returns 200`() {
        val token = registerAndGetToken("tag")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Foundation")
        setTags(token, bookId, "scifi")

        val response =
            app(
                Request(Method.GET, "/tags/scifi").header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("scifi"))
    }

    @Test
    fun `tag detail page lists all books with that tag`() {
        val token = registerAndGetToken("tag")
        val libId = createLibrary(token)
        val b1 = createBook(token, libId, "Book One")
        val b2 = createBook(token, libId, "Book Two")
        val b3 = createBook(token, libId, "Other Tag Book")
        setTags(token, b1, "fantasy")
        setTags(token, b2, "fantasy")
        setTags(token, b3, "mystery")

        val body =
            app(
                Request(Method.GET, "/tags/fantasy").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains(b1))
        assertTrue(body.contains(b2))
        assertFalse(body.contains(b3))
    }

    @Test
    fun `tags are isolated between users`() {
        val tokenA = registerAndGetToken("tag")
        val tokenB = registerAndGetToken("tag")
        val libA = createLibrary(tokenA)
        val bookA = createBook(tokenA, libA, "Private Book")
        setTags(tokenA, bookA, "privatetag")

        val bodyB = app(Request(Method.GET, "/tags").header("Cookie", "token=$tokenB")).bodyString()
        assertFalse(bodyB.contains("privatetag"))
    }

    @Test
    fun `tag list page requires authentication`() {
        val response = app(Request(Method.GET, "/tags"))
        assertTrue(response.status.code in listOf(302, 303, 401))
    }

    @Test
    fun `tag detail page requires authentication`() {
        val response = app(Request(Method.GET, "/tags/scifi"))
        assertTrue(response.status.code in listOf(302, 303, 401))
    }

    @Test
    fun `multiple tags appear in list`() {
        val token = registerAndGetToken("tag")
        val libId = createLibrary(token)
        val b1 = createBook(token, libId, "B1")
        val b2 = createBook(token, libId, "B2")
        setTags(token, b1, "alpha")
        setTags(token, b2, "beta")

        val body = app(Request(Method.GET, "/tags").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("alpha"))
        assertTrue(body.contains("beta"))
    }

    @Test
    fun `tag with URL-encoded name works`() {
        val token = registerAndGetToken("tag")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Some Book")
        setTags(token, bookId, "must read")

        val response =
            app(
                Request(Method.GET, "/tags/must%20read").header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("must read"))
    }

    @Test
    fun `book with multiple tags appears in each tag page`() {
        val token = registerAndGetToken("tag")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Multi Tag Book")
        setTags(token, bookId, "alpha2, beta2")

        val bodyAlpha = app(Request(Method.GET, "/tags/alpha2").header("Cookie", "token=$token")).bodyString()
        val bodyBeta = app(Request(Method.GET, "/tags/beta2").header("Cookie", "token=$token")).bodyString()
        assertTrue(bodyAlpha.contains(bookId))
        assertTrue(bodyBeta.contains(bookId))
    }
}
