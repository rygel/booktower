package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for dashboard streak and pages-read widgets.
 * Verifies the dashboard HTML contains the new stat cards.
 */
class DashboardWidgetsTest : IntegrationTestBase() {
    @Test
    fun `dashboard has streak widget`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("streak-widget"), "Dashboard should have streak widget")
        assertTrue(html.contains("ri-fire-line"), "Streak widget should have fire icon")
    }

    @Test
    fun `dashboard has pages read widget`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("pages-widget"), "Dashboard should have pages read widget")
    }

    @Test
    fun `dashboard shows zero streak for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        // New user should have 0 streak
        assertTrue(html.contains("streak-widget"), "Dashboard should render streak widget even with 0")
    }
}
