package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryHealthPageTest : IntegrationTestBase() {
    @Test
    fun `library health page requires authentication`() {
        val resp = app(Request(Method.GET, "/library-health"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `library health page renders for authenticated user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/library-health").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("health-summary"), "Should have health summary section")
        assertTrue(html.contains("health-library-list"), "Should have library list")
    }

    @Test
    fun `sidebar contains library health link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/library-health\""), "Sidebar should have /library-health link")
    }
}
