package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the OPDS settings page.
 */
class OpdsSettingsPageTest : IntegrationTestBase() {
    @Test
    fun `opds settings page requires authentication`() {
        val resp = app(Request(Method.GET, "/opds-settings"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `opds settings page renders unconfigured state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/opds-settings").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("opds-save-btn"), "Should have save button")
        assertTrue(html.contains("opds-user"), "Should have username input")
        assertTrue(html.contains("opds-pass"), "Should have password input")
    }

    @Test
    fun `opds settings page shows configured state after saving`() {
        val token = registerAndGetToken()
        // Configure OPDS credentials via API
        app(
            Request(Method.PUT, "/api/user/opds-credentials")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"opdsUsername":"myopdsuser","password":"secret123"}"""),
        )

        val resp = app(Request(Method.GET, "/opds-settings").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("myopdsuser"), "Should show configured username")
        assertTrue(html.contains("opds-delete-btn"), "Should have delete button when configured")
    }

    @Test
    fun `sidebar contains opds settings link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/opds-settings\""), "Sidebar should have /opds-settings link")
    }
}
