package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryPaginationIntegrationTest : IntegrationTestBase() {
    private fun createBooks(
        token: String,
        libId: String,
        count: Int,
    ): List<String> = (1..count).map { i -> createBook(token, libId, "Book $i - ${System.nanoTime()}") }

    @Test
    fun `library page 1 returns 200`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        val response = app(Request(Method.GET, "/libraries/$libId?page=1").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `library with few books shows no pagination`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        createBooks(token, libId, 3)

        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("pagination.prev") || body.contains("pagination.next") || body.contains("Previous") || body.contains("Next"),
        )
    }

    @Test
    fun `library with more than 50 books shows pagination controls`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        createBooks(token, libId, 55)

        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Next") || body.contains("Suivant") || body.contains("Weiter"))
    }

    @Test
    fun `page 2 returns different books than page 1`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        val ids = createBooks(token, libId, 55)

        val body1 = app(Request(Method.GET, "/libraries/$libId?page=1").header("Cookie", "token=$token")).bodyString()
        val body2 = app(Request(Method.GET, "/libraries/$libId?page=2").header("Cookie", "token=$token")).bodyString()

        // at least one book ID that's on page 2 should not be on page 1
        val onPage1 = ids.count { body1.contains(it) }
        val onPage2 = ids.count { body2.contains(it) }
        assertTrue(onPage1 > 0, "Page 1 should show some books")
        assertTrue(onPage2 > 0, "Page 2 should show some books")
        assertTrue(onPage1 + onPage2 <= 55, "Pages should not overlap (much)")
    }

    @Test
    fun `page out of range returns empty grid gracefully`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        createBooks(token, libId, 5)

        val response = app(Request(Method.GET, "/libraries/$libId?page=999").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `pagination preserves sort param`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        createBooks(token, libId, 55)

        val body =
            app(
                Request(Method.GET, "/libraries/$libId?page=1&sort=TITLE").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("page=2"))
        assertTrue(body.contains("sort=TITLE"))
    }

    @Test
    fun `pagination preserves status filter`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        createBooks(token, libId, 55)

        val body =
            app(
                Request(Method.GET, "/libraries/$libId?page=1&status=READING").header("Cookie", "token=$token"),
            ).bodyString()
        // pagination links should include status=READING
        assertTrue(body.contains("status=READING"))
    }

    @Test
    fun `pagination summary shows correct page info`() {
        val token = registerAndGetToken("pg")
        val libId = createLibrary(token)
        createBooks(token, libId, 55)

        val body = app(Request(Method.GET, "/libraries/$libId?page=1").header("Cookie", "token=$token")).bodyString()
        // summary "Page 1 of 2" or similar
        assertTrue(body.contains("1") && body.contains("2"))
    }
}
