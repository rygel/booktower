package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingSpeedPageTest : IntegrationTestBase() {
    @Test
    fun `reading speed page requires authentication`() {
        val resp = app(Request(Method.GET, "/reading-speed"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `reading speed page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/reading-speed").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("speed-avg-card"), "Should have avg speed card")
        assertTrue(html.contains("speed-total-card"), "Should have total time card")
        assertTrue(html.contains("session-list"), "Should have session list")
    }

    @Test
    fun `sidebar contains reading speed link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/reading-speed\""), "Sidebar should have /reading-speed link")
    }
}
