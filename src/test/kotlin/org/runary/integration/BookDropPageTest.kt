package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookDropPageTest : IntegrationTestBase() {
    @Test
    fun `book drop page requires authentication`() {
        val resp = app(Request(Method.GET, "/book-drop"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `book drop page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/book-drop").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("bookdrop-list"), "Should have bookdrop list container")
    }

    @Test
    fun `sidebar contains book drop link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/book-drop\""), "Sidebar should have /book-drop link")
    }
}
