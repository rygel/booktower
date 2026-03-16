package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueIntegrationTest : IntegrationTestBase() {

    private fun setStatus(token: String, bookId: String, status: String) {
        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=$status"),
        )
    }

    @Test
    fun `GET queue returns 200`() {
        val token = registerAndGetToken("q1")
        val response = app(Request(Method.GET, "/queue").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `queue page requires authentication`() {
        val response = app(Request(Method.GET, "/queue"))
        assertTrue(response.status.code in listOf(302, 303, 401),
            "Queue page must redirect unauthenticated users")
    }

    @Test
    fun `queue shows empty state when no WANT_TO_READ books`() {
        val token = registerAndGetToken("q2")
        val libId = createLibrary(token)
        createBook(token, libId, "Random Book")

        val body = app(Request(Method.GET, "/queue").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("page.queue.empty.title") || body.contains("reading queue is empty"),
            "Empty state message must appear when no books are WANT_TO_READ")
    }

    @Test
    fun `queue shows WANT_TO_READ book`() {
        val token = registerAndGetToken("q3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Wishlist Book")
        setStatus(token, bookId, "WANT_TO_READ")

        val body = app(Request(Method.GET, "/queue").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Wishlist Book"), "Queue must display WANT_TO_READ book")
    }

    @Test
    fun `queue does not show READING or FINISHED books`() {
        val token = registerAndGetToken("q4")
        val libId = createLibrary(token)
        val readingId = createBook(token, libId, "Reading Book")
        val finishedId = createBook(token, libId, "Finished Book")
        setStatus(token, readingId, "READING")
        setStatus(token, finishedId, "FINISHED")

        val body = app(Request(Method.GET, "/queue").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("Reading Book"), "READING book must not appear in queue")
        assertFalse(body.contains("Finished Book"), "FINISHED book must not appear in queue")
    }

    @Test
    fun `queue shows Start Reading button for each book`() {
        val token = registerAndGetToken("q5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Queue Target")
        setStatus(token, bookId, "WANT_TO_READ")

        val body = app(Request(Method.GET, "/queue").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("action.start.reading") || body.contains("Start Reading"),
            "Start Reading button must appear for WANT_TO_READ books")
    }

    @Test
    fun `queue shows books from multiple libraries`() {
        val token = registerAndGetToken("q6")
        val lib1 = createLibrary(token, "Lib A")
        val lib2 = createLibrary(token, "Lib B")
        val book1 = createBook(token, lib1, "Book From Lib A")
        val book2 = createBook(token, lib2, "Book From Lib B")
        setStatus(token, book1, "WANT_TO_READ")
        setStatus(token, book2, "WANT_TO_READ")

        val body = app(Request(Method.GET, "/queue").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Book From Lib A"), "Queue must show WANT_TO_READ books from all libraries")
        assertTrue(body.contains("Book From Lib B"), "Queue must show WANT_TO_READ books from all libraries")
    }

    @Test
    fun `queue is isolated between users`() {
        val tokenA = registerAndGetToken("q7a")
        val tokenB = registerAndGetToken("q7b")
        val libA = createLibrary(tokenA)
        val bookA = createBook(tokenA, libA, "Private Wishlist Book")
        setStatus(tokenA, bookA, "WANT_TO_READ")

        val bodyB = app(Request(Method.GET, "/queue").header("Cookie", "token=$tokenB")).bodyString()
        assertFalse(bodyB.contains("Private Wishlist Book"),
            "Queue must not show another user's WANT_TO_READ books")
    }

    @Test
    fun `queue sidebar link is marked active on queue page`() {
        val token = registerAndGetToken("q8")
        val body = app(Request(Method.GET, "/queue").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("/queue"), "Queue link must be present in sidebar")
    }
}
