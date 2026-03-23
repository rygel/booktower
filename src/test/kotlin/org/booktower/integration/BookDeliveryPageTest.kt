package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookDeliveryPageTest : IntegrationTestBase() {
    @Test
    fun `book delivery page requires authentication`() {
        val resp = app(Request(Method.GET, "/book-delivery"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `book delivery page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/book-delivery").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-recipient-btn"), "Should have add recipient button")
        assertTrue(html.contains("recipient-list"), "Should have recipient list")
    }

    @Test
    fun `sidebar contains book delivery link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/book-delivery\""), "Sidebar should have /book-delivery link")
    }
}
