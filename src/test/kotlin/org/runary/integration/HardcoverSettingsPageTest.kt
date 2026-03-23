package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HardcoverSettingsPageTest : IntegrationTestBase() {
    @Test
    fun `hardcover settings page requires authentication`() {
        val resp = app(Request(Method.GET, "/hardcover-settings"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `hardcover settings page renders unconfigured state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/hardcover-settings").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("hc-api-key"), "Should have API key input")
        assertTrue(html.contains("hc-save-btn"), "Should have save button")
        assertTrue(html.contains("hc-test-btn"), "Should have test button")
        assertTrue(html.contains("hardcover-status"), "Should have status section")
    }

    @Test
    fun `sidebar contains hardcover settings link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/hardcover-settings\""), "Sidebar should have /hardcover-settings link")
    }
}
